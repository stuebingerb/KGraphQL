package com.apurebase.kgraphql.schema.structure

import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.schema.DefaultSchema.Companion.OPERATION_NAME_PARAM
import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.execution.ExecutionOptions
import com.apurebase.kgraphql.schema.execution.ExecutionPlan
import com.apurebase.kgraphql.schema.execution.TypeCondition
import com.apurebase.kgraphql.schema.introspection.__Type
import com.apurebase.kgraphql.schema.model.ast.DefinitionNode.ExecutableDefinitionNode
import com.apurebase.kgraphql.schema.model.ast.DefinitionNode.ExecutableDefinitionNode.FragmentDefinitionNode
import com.apurebase.kgraphql.schema.model.ast.DefinitionNode.ExecutableDefinitionNode.OperationDefinitionNode
import com.apurebase.kgraphql.schema.model.ast.DirectiveNode
import com.apurebase.kgraphql.schema.model.ast.DocumentNode
import com.apurebase.kgraphql.schema.model.ast.NameNode
import com.apurebase.kgraphql.schema.model.ast.OperationTypeNode
import com.apurebase.kgraphql.schema.model.ast.SelectionNode
import com.apurebase.kgraphql.schema.model.ast.SelectionNode.FieldNode
import com.apurebase.kgraphql.schema.model.ast.SelectionNode.FragmentNode
import com.apurebase.kgraphql.schema.model.ast.SelectionNode.FragmentNode.FragmentSpreadNode
import com.apurebase.kgraphql.schema.model.ast.SelectionNode.FragmentNode.InlineFragmentNode
import com.apurebase.kgraphql.schema.model.ast.SelectionSetNode
import com.apurebase.kgraphql.schema.model.ast.VariableDefinitionNode
import com.apurebase.kgraphql.schema.model.ast.toArguments
import java.util.Stack
import kotlin.reflect.full.starProjectedType

class RequestInterpreter(private val schemaModel: SchemaModel) {

    private val directivesByName = schemaModel.directives.associateBy { it.name }

    inner class InterpreterContext(
        val fragments: Map<String, Pair<__Type, SelectionSetNode>>
    ) {
        // prevent stack overflow
        private val fragmentsStack = Stack<String>()
        fun get(node: FragmentSpreadNode): Execution.Fragment? {
            if (fragmentsStack.contains(node.name.value)) {
                throw ValidationException("Fragment spread circular references are not allowed", node)
            }

            val (conditionType, selectionSet) = fragments[node.name.value] ?: return null
            val condition = TypeCondition(conditionType.name!!)

            fragmentsStack.push(node.name.value)
            val elements = selectionSet.selections.map { conditionType.handleSelectionFieldOrFragment(it, this) }
            fragmentsStack.pop()

            return Execution.Fragment(node, condition, elements, node.directives?.lookup())
        }
    }

    fun createExecutionPlan(
        document: DocumentNode,
        requestedOperationName: String?,
        variables: VariablesJson,
        options: ExecutionOptions
    ): ExecutionPlan {
        val executables = document.definitions.filterIsInstance<ExecutableDefinitionNode>()

        val operation = document.getOperation(variables, requestedOperationName)

        val root = when (operation.operation) {
            OperationTypeNode.QUERY -> schemaModel.queryType
            OperationTypeNode.MUTATION -> schemaModel.mutationType
                ?: throw ValidationException("Mutations are not supported on this schema")

            OperationTypeNode.SUBSCRIPTION -> schemaModel.subscriptionType
                ?: throw ValidationException("Subscriptions are not supported on this schema")
        }

        val fragmentDefinitionNode = executables.filterIsInstance<FragmentDefinitionNode>()
        val fragmentDefinitions = fragmentDefinitionNode.associate { fragmentDef ->
            val type = schemaModel.allTypesByName.getValue(fragmentDef.typeCondition.name.value)
            val name = fragmentDef.name!!.value

            if (fragmentDefinitionNode.count { it.name!!.value == name } > 1) {
                throw ValidationException("There can be only one fragment named $name.", fragmentDef)
            }

            name to (type to fragmentDef.selectionSet)
        }

        val ctx = InterpreterContext(fragmentDefinitions)

        return ExecutionPlan(
            options,
            operation.selectionSet.selections.map {
                root.handleSelection(it as FieldNode, ctx, operation.variableDefinitions)
            }
        ).also {
            it.isSubscription = operation.operation == OperationTypeNode.SUBSCRIPTION
        }
    }

    private fun handleReturnType(ctx: InterpreterContext, type: __Type, requestNode: FieldNode) =
        handleReturnType(ctx, type, requestNode.selectionSet, requestNode.name)

    private fun handleReturnType(
        ctx: InterpreterContext,
        type: __Type,
        selectionSet: SelectionSetNode?,
        propertyName: NameNode? = null
    ): List<Execution> = if (!selectionSet?.selections.isNullOrEmpty()) {
        selectionSet!!.selections.map {
            handleReturnTypeChildOrFragment(it, type, ctx)
        }
    } else if (type.unwrapped().fields?.isNotEmpty() == true) {
        throw ValidationException(
            "Missing selection set on property ${propertyName?.value} of type ${type.unwrapped().name}",
            selectionSet
        )
    } else {
        emptyList()
    }

    private fun handleReturnTypeChildOrFragment(node: SelectionNode, returnType: __Type, ctx: InterpreterContext) =
        returnType.unwrapped().handleSelectionFieldOrFragment(node, ctx)

    private fun findFragmentType(
        fragment: FragmentNode,
        ctx: InterpreterContext,
        enclosingType: __Type
    ): Execution.Fragment = when (fragment) {
        is FragmentSpreadNode -> {
            ctx.get(fragment) ?: throw unknownFragmentTypeException(fragment)
        }

        is InlineFragmentNode -> {
            val type = if (fragment.directives?.isNotEmpty() == true) {
                enclosingType
            } else {
                schemaModel.queryTypesByName[fragment.typeCondition?.name?.value] ?: throw unknownFragmentTypeException(
                    fragment
                )
            }
            Execution.Fragment(
                selectionNode = fragment,
                condition = TypeCondition(type.name!!),
                directives = fragment.directives?.lookup(),
                elements = fragment.selectionSet.selections.map { type.handleSelectionFieldOrFragment(it, ctx) }
            )
        }
    }

    private fun __Type.handleSelectionFieldOrFragment(node: SelectionNode, ctx: InterpreterContext): Execution =
        when (node) {
            is FragmentNode -> findFragmentType(node, ctx, this)
            is FieldNode -> handleSelection(node, ctx)
        }

    private fun __Type.handleSelection(
        node: FieldNode,
        ctx: InterpreterContext,
        variables: List<VariableDefinitionNode>? = null
    ): Execution.Node {
        return when (val field = this[node.name.value]) {
            null -> throw ValidationException(
                "Property ${node.name.value} on $name does not exist",
                node
            )

            is Field.Union<*> -> handleUnion(field, node, ctx)

            is Field -> {
                validatePropertyArguments(this, field, node)

                return Execution.Node(
                    selectionNode = node,
                    field = field,
                    children = handleReturnType(ctx, field.returnType, node),
                    key = node.name.value,
                    alias = node.alias?.value,
                    arguments = node.arguments?.toArguments(),
                    directives = node.directives?.lookup(),
                    variables = variables
                )
            }

            else -> {
                error("unhandled field $field")
            }
        }
    }

    private fun <T> handleUnion(
        field: Field.Union<T>,
        selectionNode: FieldNode,
        ctx: InterpreterContext
    ): Execution.Union {
        validateUnionRequest(field, selectionNode)

        // https://spec.graphql.org/October2021/#sec-Unions
        //  "With interfaces and objects, only those fields defined on the type can be queried directly; to query
        //  other fields on an interface, typed fragments must be used. This is the same as for unions, but unions
        //  do not define any fields, so *no* fields may be queried on this type without the use of type refining
        //  fragments or inline fragments (with the exception of the meta-field `__typename`)."
        val unionMembersChildren: Map<__Type, List<Execution>> =
            (field.returnType.unwrapped() as Type.Union).possibleTypes.associateWith { possibleType ->
                val mergedSelectionsForType = selectionNode.selectionSet?.selections?.flatMap {
                    when {
                        // Only __typename is allowed as field selection
                        it is FieldNode && it.name.value == "__typename"
                            -> listOf(it)

                        it is FragmentSpreadNode && ctx.fragments[it.name.value]?.first?.name == possibleType.name
                            -> ctx.fragments.getValue(it.name.value).second.selections

                        it is InlineFragmentNode && possibleType.name == it.typeCondition?.name?.value
                            -> it.selectionSet.selections

                        else -> emptyList()
                    }
                }

                if (!mergedSelectionsForType.isNullOrEmpty()) {
                    handleReturnType(ctx, possibleType, SelectionSetNode(null, mergedSelectionsForType))
                } else {
                    throw ValidationException("Missing selection set for type ${possibleType.name}", selectionNode)
                }
            }

        return Execution.Union(
            node = selectionNode,
            unionField = field,
            memberChildren = unionMembersChildren,
            key = selectionNode.name.value,
            alias = selectionNode.alias?.value,
            condition = null,
            directives = selectionNode.directives?.lookup()
        )
    }

    private fun unknownFragmentTypeException(fragment: FragmentNode) = when (fragment) {
        is FragmentSpreadNode -> ValidationException(
            message = "Fragment ${fragment.name.value} not found",
            node = fragment
        )

        is InlineFragmentNode -> ValidationException(
            message = "Unknown type ${fragment.typeCondition?.name?.value} in type condition on fragment",
            node = fragment
        )
    }

    private fun List<DirectiveNode>.lookup() = associate { findDirective(it) to it.arguments?.toArguments() }

    private fun findDirective(invocation: DirectiveNode): Directive {
        return directivesByName[invocation.name.value.removePrefix("@")]
            ?: throw ValidationException("Directive ${invocation.name.value} does not exist", invocation)
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
                        throw ValidationException("anonymous operation must be the only defined operation")
                    }
                }.joinToString(prefix = "[", postfix = "]")

                val operationName = requestedOperationName ?: (
                    variables.get(String::class, String::class.starProjectedType, OPERATION_NAME_PARAM)
                        ?: throw ValidationException("Must provide an operation name from: $operationNamesFound")
                    )

                operations.firstOrNull { it.name?.value == operationName }
                    ?: throw ValidationException("Must provide an operation name from: $operationNamesFound, found $operationName")
            }
        }
    }
}
