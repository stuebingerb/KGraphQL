package de.stuebingerb.kgraphql.schema.structure

import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.request.VariablesJson
import de.stuebingerb.kgraphql.schema.DefaultSchema.Companion.OPERATION_NAME_PARAM
import de.stuebingerb.kgraphql.schema.builtin.BuiltInScalars
import de.stuebingerb.kgraphql.schema.directive.Directive
import de.stuebingerb.kgraphql.schema.execution.Execution
import de.stuebingerb.kgraphql.schema.execution.ExecutionMode
import de.stuebingerb.kgraphql.schema.execution.ExecutionPlan
import de.stuebingerb.kgraphql.schema.execution.TypeCondition
import de.stuebingerb.kgraphql.schema.introspection.TypeKind
import de.stuebingerb.kgraphql.schema.model.ast.ASTNode
import de.stuebingerb.kgraphql.schema.model.ast.DefinitionNode.ExecutableDefinitionNode
import de.stuebingerb.kgraphql.schema.model.ast.DefinitionNode.ExecutableDefinitionNode.FragmentDefinitionNode
import de.stuebingerb.kgraphql.schema.model.ast.DefinitionNode.ExecutableDefinitionNode.OperationDefinitionNode
import de.stuebingerb.kgraphql.schema.model.ast.DirectiveNode
import de.stuebingerb.kgraphql.schema.model.ast.DocumentNode
import de.stuebingerb.kgraphql.schema.model.ast.NameNode
import de.stuebingerb.kgraphql.schema.model.ast.OperationTypeNode
import de.stuebingerb.kgraphql.schema.model.ast.SelectionNode
import de.stuebingerb.kgraphql.schema.model.ast.SelectionNode.FieldNode
import de.stuebingerb.kgraphql.schema.model.ast.SelectionNode.FragmentNode
import de.stuebingerb.kgraphql.schema.model.ast.SelectionNode.FragmentNode.FragmentSpreadNode
import de.stuebingerb.kgraphql.schema.model.ast.SelectionNode.FragmentNode.InlineFragmentNode
import de.stuebingerb.kgraphql.schema.model.ast.SelectionSetNode
import de.stuebingerb.kgraphql.schema.model.ast.ValueNode
import de.stuebingerb.kgraphql.schema.model.ast.VariableDefinitionNode
import de.stuebingerb.kgraphql.schema.model.ast.toArguments
import java.util.Stack

internal class RequestInterpreter(private val schemaModel: SchemaModel) {

    private val directivesByName = schemaModel.directives.associateBy { it.name }
    private val usedFragments: MutableSet<String> = mutableSetOf()

    private inner class InterpreterContext(
        // Fragments defined for the current operation
        val fragments: Map<String, Pair<Type, SelectionSetNode>>,
        // Variables declared for the current operation
        val declaredVariables: List<VariableDefinitionNode>?
    ) {
        // prevent stack overflow
        private val fragmentsStack = Stack<String>()

        fun get(node: FragmentSpreadNode): Execution.Fragment? {
            if (fragmentsStack.contains(node.name.value)) {
                throw ValidationException("Fragment spread circular references are not allowed", node)
            }

            val (conditionType, selectionSet) = fragments[node.name.value] ?: return null
            val condition = TypeCondition(conditionType)

            fragmentsStack.push(node.name.value)
            val elements = selectionSet.selections.map { conditionType.handleSelectionFieldOrFragment(it, this) }
            fragmentsStack.pop()

            return Execution.Fragment(node, condition, elements, node.directives?.lookup(), null)
                .also { usedFragments.add(node.name.value) }
        }
    }

    fun createExecutionPlan(
        document: DocumentNode,
        requestedOperationName: String?,
        variables: VariablesJson
    ): ExecutionPlan {
        val executables = document.definitions.filterIsInstance<ExecutableDefinitionNode>()

        val operation = document.getOperation(variables, requestedOperationName)

        val (root, executionMode) = when (operation.operation) {
            OperationTypeNode.QUERY -> schemaModel.queryType to ExecutionMode.Normal
            OperationTypeNode.MUTATION -> (schemaModel.mutationType
                ?: throw ValidationException("Mutations are not supported on this schema")) to ExecutionMode.Serial

            OperationTypeNode.SUBSCRIPTION -> (schemaModel.subscriptionType
                ?: throw ValidationException("Subscriptions are not supported on this schema")) to ExecutionMode.Normal
        }

        val fragmentDefinitionNodes = executables.filterIsInstance<FragmentDefinitionNode>()
        val fragmentDefinitions = fragmentDefinitionNodes.associate { fragmentDef ->
            val type = schemaModel.allTypesByName[fragmentDef.typeCondition.name.value].validateForFragment(fragmentDef)
            val name = fragmentDef.name.value

            if (fragmentDefinitionNodes.count { it.name.value == name } > 1) {
                throw ValidationException("There can be only one fragment named '$name'", fragmentDef)
            }

            name to (type to fragmentDef.selectionSet)
        }

        val ctx = InterpreterContext(fragmentDefinitions, operation.variableDefinitions)

        return ExecutionPlan(
            executionMode = executionMode,
            operations = operation.selectionSet.selections.map {
                root.handleSelectionFieldOrFragment(it, ctx)
            },
            root = root,
            declaredVariables = operation.variableDefinitions
        ).also {
            val unusedFragments = ctx.fragments.keys.filter { fragment -> fragment !in usedFragments }
            if (unusedFragments.isNotEmpty()) {
                throw ValidationException("Found unused fragments: $unusedFragments")
            }
        }
    }

    private fun handleReturnType(ctx: InterpreterContext, type: Type, requestNode: FieldNode) =
        handleReturnType(ctx, type, requestNode.selectionSet, requestNode.name)

    private fun handleReturnType(
        ctx: InterpreterContext,
        type: Type,
        selectionSet: SelectionSetNode?,
        propertyName: NameNode? = null
    ): List<Execution> = if (!selectionSet?.selections.isNullOrEmpty()) {
        selectionSet.selections.map {
            handleReturnTypeChildOrFragment(it, type, ctx)
        }
    } else if (type.unwrapped().fields?.isNotEmpty() == true) {
        throw ValidationException(
            "Missing selection set on property '${propertyName?.value}' of type '${type.unwrapped().name}'",
            selectionSet
        )
    } else {
        emptyList()
    }

    private fun handleReturnTypeChildOrFragment(node: SelectionNode, returnType: Type, ctx: InterpreterContext) =
        returnType.unwrapped().handleSelectionFieldOrFragment(node, ctx)

    private fun findFragmentType(
        fragment: FragmentNode,
        ctx: InterpreterContext,
        enclosingType: Type?
    ): Execution.Fragment = when (fragment) {
        is FragmentSpreadNode -> {
            ctx.get(fragment)?.also { it.condition.onType.validateForFragment(it.selectionNode, enclosingType) }
                ?: throw ValidationException(
                    message = "Fragment '${fragment.name.value}' not found",
                    node = fragment
                )
        }

        is InlineFragmentNode -> {
            val type = if (fragment.typeCondition != null) {
                schemaModel.allTypesByName[fragment.typeCondition.name.value]
            } else {
                enclosingType
            }.validateForFragment(fragment, enclosingType)
            Execution.Fragment(
                selectionNode = fragment,
                condition = TypeCondition(type),
                directives = fragment.directives?.lookup(),
                elements = fragment.selectionSet.selections.map { type.handleSelectionFieldOrFragment(it, ctx) },
                parent = null
            )
        }
    }

    private fun Type.handleSelectionFieldOrFragment(node: SelectionNode, ctx: InterpreterContext): Execution =
        when (node) {
            is FragmentNode -> findFragmentType(node, ctx, this)
            is FieldNode -> handleSelection(node, ctx)
        }

    private fun Type.handleSelection(node: FieldNode, ctx: InterpreterContext): Execution.Node {
        return when (val field = this[node.name.value]) {
            null -> throw ValidationException(
                "Property '${node.name.value}' on '$name' does not exist",
                node
            )

            is Field.RemoteOperation<*, *> -> handleRemoteOperation(field, node, ctx)

            else -> {
                validatePropertyArguments(this, field, node)

                Execution.Node(
                    selectionNode = node,
                    field = field,
                    children = handleReturnType(ctx, field.returnType, node),
                    arguments = node.arguments?.toArguments(),
                    directives = node.directives?.lookup(),
                    // TODO: can we remove variables from Execution.Node? variables are only used with Execution.Remote
                    variables = null,
                    arrayIndex = null,
                    parent = null
                )
            }
        }
    }

    private fun Type.handleRemoteOperation(
        field: Field.RemoteOperation<*, *>,
        node: FieldNode,
        ctx: InterpreterContext
    ): Execution.Remote {
        fun List<SelectionNode>.namedFragments(): List<Execution.Fragment> = flatMap { selectionNode ->
            when (selectionNode) {
                is FragmentSpreadNode -> {
                    val fragment = findFragmentType(selectionNode, ctx, null)
                    fragment.elements.map { it.selectionNode }.namedFragments() + fragment
                }

                is InlineFragmentNode -> {
                    selectionNode.selectionSet.selections.namedFragments()
                }

                is FieldNode -> {
                    selectionNode.selectionSet?.selections.orEmpty().namedFragments()
                }
            }
        }

        // If we don't have an entry in remoteTypesBySchema we are querying a local type (can happen with stitched fields)
        // TODO: should be more obvious and maybe we need a "typesBySchema", not only for remotes
        val typesFromSameSchema =
            (schemaModel.remoteTypesBySchema[field.remoteUrl]
                ?: schemaModel.allTypes).mapNotNullTo(HashSet()) { it.name }

        val nodeArgs = node.arguments?.toArguments()
        val usedVariableNames = nodeArgs.orEmpty().values
            .filterIsInstance<ValueNode.VariableNode>()
            .mapTo(HashSet()) { it.name.value }
        return Execution.Remote(
            selectionNode = node,
            field = field,
            children = handleReturnType(ctx, field.returnType, node),
            arguments = nodeArgs,
            directives = node.directives?.lookup(),
            variables = ctx.declaredVariables?.filter { it.variable.name.value in usedVariableNames },
            namedFragments = node.selectionSet?.selections?.namedFragments()
                ?.filter { it.condition.onType.name in typesFromSameSchema }
                ?.distinctBy { (it.selectionNode as FragmentSpreadNode).name.value },
            remoteUrl = field.remoteUrl,
            remoteOperation = field.remoteQuery,
            operationType = when {
                this == schemaModel.mutationType -> OperationTypeNode.MUTATION
                this == schemaModel.subscriptionType -> OperationTypeNode.SUBSCRIPTION
                else -> OperationTypeNode.QUERY
            },
            parent = null
        )
    }

    private fun Type?.validateForFragment(fragment: ASTNode, enclosingType: Type? = null): Type {
        val (typeName, fragmentName) = when (fragment) {
            is InlineFragmentNode -> fragment.typeCondition?.name?.value to "inline fragment"
            is FragmentSpreadNode -> this?.name to "fragment '${fragment.name.value}'"
            is FragmentDefinitionNode -> fragment.typeCondition.name.value to "fragment '${fragment.name.value}'"
            else -> error("Unsupported fragment type: $fragment")
        }
        if (this == null) {
            throw ValidationException(
                message = "Unknown type '$typeName' in type condition on $fragmentName",
                node = fragment
            )
        } else if (isInputType()) {
            throw ValidationException(
                message = "Fragments can only be specified on object types, interfaces, and unions but '$name' is $kind on $fragmentName",
                node = fragment
            )
        } else if (enclosingType != null) {
            // https://spec.graphql.org/September2025/#GetPossibleTypes()
            fun getPossibleTypes(type: Type): List<Type> {
                return when (type.kind) {
                    TypeKind.OBJECT -> listOf(type)
                    TypeKind.INTERFACE -> checkNotNull(type.possibleTypes)
                    TypeKind.UNION -> checkNotNull(type.possibleTypes)
                    else -> emptyList()
                }
            }

            // https://spec.graphql.org/September2025/#sec-Fragment-Spread-Is-Possible
            val possibleTypeNamesFromEnclosingType = getPossibleTypes(enclosingType).map { it.name }
            val possibleTypeNamesFromThisType = getPossibleTypes(this).map { it.name }
            val applicableTypeNames = possibleTypeNamesFromEnclosingType.intersect(possibleTypeNamesFromThisType.toSet())
            if (applicableTypeNames.isEmpty()) {
                throw ValidationException(
                    message = "Invalid type '$typeName' in type condition on $fragmentName of type '${enclosingType.name}'; must be one of '$possibleTypeNamesFromEnclosingType'",
                    node = fragment
                )
            }
        }
        return this
    }

    private fun List<DirectiveNode>.lookup() = associate { findDirective(it) to it.arguments?.toArguments() }

    private fun findDirective(invocation: DirectiveNode): Directive {
        return directivesByName[invocation.name.value.removePrefix("@")]
            ?: throw ValidationException("Directive '${invocation.name.value}' does not exist", invocation)
    }

    private fun DocumentNode.getOperation(
        variables: VariablesJson,
        requestedOperationName: String?
    ): OperationDefinitionNode = definitions.filterIsInstance<OperationDefinitionNode>().let { operations ->
        when (operations.size) {
            0 -> throw ValidationException("Must provide any operation")
            1 -> operations.first()
            else -> {
                val operationNamesFound = operations.mapNotNull { it.name?.value }.also {
                    if (it.size != operations.size) {
                        throw ValidationException("Anonymous operation must be the only defined operation")
                    }
                }.joinToString(prefix = "[", postfix = "]")

                val operationName = requestedOperationName ?: variables.get(
                    BuiltInScalars.STRING.typeDef.toScalarType(),
                    OPERATION_NAME_PARAM
                )?.let { it as? ValueNode.StringValueNode }?.value

                operations.firstOrNull { it.name?.value == operationName }
                    ?: throw ValidationException("Must provide an operation name from: $operationNamesFound, found: $operationName")
            }
        }
    }
}
