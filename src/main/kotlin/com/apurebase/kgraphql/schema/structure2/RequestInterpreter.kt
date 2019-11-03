package com.apurebase.kgraphql.schema.structure2

import com.apurebase.kgraphql.RequestException
import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.request.graph.DirectiveInvocation
import com.apurebase.kgraphql.request.graph.Fragment
import com.apurebase.kgraphql.schema.DefaultSchema.Companion.OPERATION_NAME_PARAM
import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.execution.ExecutionPlan
import com.apurebase.kgraphql.schema.execution.TypeCondition
import com.apurebase.kgraphql.schema.jol.ast.*
import com.apurebase.kgraphql.schema.jol.ast.DefinitionNode.*
import com.apurebase.kgraphql.schema.jol.ast.DefinitionNode.ExecutableDefinitionNode.FragmentDefinitionNode
import com.apurebase.kgraphql.schema.jol.ast.DefinitionNode.ExecutableDefinitionNode.OperationDefinitionNode
import com.apurebase.kgraphql.schema.jol.ast.SelectionNode.FieldNode
import com.apurebase.kgraphql.schema.jol.ast.SelectionNode.FragmentNode
import com.apurebase.kgraphql.schema.jol.ast.SelectionNode.FragmentNode.FragmentSpreadNode
import com.apurebase.kgraphql.schema.jol.ast.SelectionNode.FragmentNode.InlineFragmentNode
import java.util.*
import kotlin.reflect.full.starProjectedType


class RequestInterpreter(val schemaModel: SchemaModel) {

    private val directivesByName = schemaModel.directives.associateBy { it.name }

    // TODO: Be able to do this without this intermediate context
    inner class InterpreterContext(
        val fragments: Map<String, Pair<Type, SelectionSetNode>>
    ) {
        // prevent stack overflow
        private val fragmentsStack = Stack<String>()
        fun get(node: FragmentSpreadNode): Execution.Fragment? {
            if(fragmentsStack.contains(node.name.value)) throw RequestException("Fragment spread circular references are not allowed")

            val (conditionType, selectionSet) = fragments[node.name.value] ?: return null
            val condition = TypeCondition(conditionType)

            fragmentsStack.push(node.name.value)
            val elements = selectionSet.selections.map { conditionType.handleSelectionFieldOrFragment(it, this) }
            fragmentsStack.pop()

            return Execution.Fragment(condition, elements, node.directives?.lookup())
        }
    }

    fun createExecutionPlan(document: DocumentNode, variables: VariablesJson): ExecutionPlan {
        val test = document.definitions.filterIsInstance<ExecutableDefinitionNode>()

        val operation = test.filterIsInstance<OperationDefinitionNode>().let { operations ->
            when (operations.size) {
                0 -> throw RequestException("Must provide any operation")
                1 -> operations.first()
                else -> {
                    val operationNamesFound = operations.mapNotNull { it.name?.value }.also {
                        if (it.size != operations.size) throw RequestException("anonymous operation must be the only defined operation")
                    }.joinToString(prefix = "[", postfix = "]")

                    val operationName = variables.get(String::class, String::class.starProjectedType, OPERATION_NAME_PARAM)
                        ?: throw RequestException("Must provide an operation name from: $operationNamesFound")

                    operations.firstOrNull { it.name?.value == operationName }
                        ?: throw RequestException("Must provide an operation name from: $operationNamesFound, found $operationName")
                }
            }
        }

        val root = when (operation.operation) {
            OperationTypeNode.QUERY -> schemaModel.query
            OperationTypeNode.MUTATION -> schemaModel.mutation
            OperationTypeNode.SUBSCRIPTION -> TODO("Not supported")
        }
        val fragmentDefinitions = test.filterIsInstance<FragmentDefinitionNode>().map { fragmentDef ->
            val type = schemaModel.allTypesByName[fragmentDef.typeCondition.name.value] ?: throw TODO("Handle")

            fragmentDef.name!!.value to (type to fragmentDef.selectionSet)
        }.toMap()

        val ctx = InterpreterContext(fragmentDefinitions)

        return ExecutionPlan(
            operation.selectionSet.selections.map {
                root.handleSelection(it as FieldNode, ctx, operation.variableDefinitions)
            }
        )
    }

    private fun handleReturnType(ctx: InterpreterContext, type: Type, requestNode: FieldNode) =
        handleReturnType(ctx, type, requestNode.selectionSet, requestNode.name)

    private fun handleReturnType(ctx: InterpreterContext, type: Type, selectionSet: SelectionSetNode?, propertyName: NameNode? = null): List<Execution> {
        val children = mutableListOf<Execution>()

        if (!selectionSet?.selections.isNullOrEmpty()) {
            selectionSet!!.selections.mapTo(children) {
                handleReturnTypeChildOrFragment(it, type, ctx)
            }
        } else if (type.unwrapped().fields?.isNotEmpty() == true) {
            throw RequestException("Missing selection set on property ${propertyName?.value} of type ${type.unwrapped().name}")
        }

        return children
    }

    private fun handleReturnTypeChildOrFragment(node: SelectionNode, returnType: Type, ctx: InterpreterContext) =
        returnType.unwrapped().handleSelectionFieldOrFragment(node, ctx)

    private fun findFragmentType(fragment: FragmentNode, ctx: InterpreterContext, enclosingType: Type): Execution.Fragment = when(fragment) {
        is FragmentSpreadNode -> {
            ctx.get(fragment) ?: throw throwUnknownFragmentTypeEx(fragment)
        }
        is InlineFragmentNode -> {
            val type =if (fragment.directives?.isNotEmpty() == true) {
                enclosingType
            } else {
                schemaModel.queryTypesByName[fragment.typeCondition?.name?.value] ?: throw throwUnknownFragmentTypeEx(fragment)
            }
            Execution.Fragment(
                condition = TypeCondition(type),
                directives = fragment.directives?.lookup(),
                elements = fragment.selectionSet.selections.map { type.handleSelectionFieldOrFragment(it, ctx) }
            )
        }
    }

    private fun Type.handleSelectionFieldOrFragment(node: SelectionNode, ctx: InterpreterContext): Execution = when (node) {
        is FragmentNode -> findFragmentType(node, ctx, this)
        is FieldNode -> handleSelection(node, ctx)
    }

    private fun Type.handleSelection(node: FieldNode, ctx: InterpreterContext, variables: List<VariableDefinitionNode>? = null): Execution.Node {
        return when (val field = this[node.name.value]) {
            null -> throw RequestException("Property ${node.name.value} on $name does not exist")
            is Field.Union<*> -> handleUnion(field, node, ctx)
            else -> {
                validatePropertyArguments(this, field, node)

                return Execution.Node(
                    field = field,
                    children = handleReturnType(ctx, field.returnType, node),
                    key = node.name.value,
                    alias = node.alias?.value,
                    arguments = node.arguments?.toArguments(),
                    directives = node.directives?.lookup(),
                    variables = variables
                )
            }
        }
    }

    private fun <T> handleUnion(field: Field.Union<T>, selectionNode: FieldNode, ctx: InterpreterContext): Execution.Union {
        validateUnionRequest(field, selectionNode)

        val unionMembersChildren: Map<Type, List<Execution>> = field.returnType.possibleTypes.associateWith { possibleType ->
            val selections = selectionNode.selectionSet?.selections

            val a = selections?.filterIsInstance<FragmentSpreadNode>()?.firstOrNull {
                ctx.fragments[it.name.value]?.first?.name == possibleType.name
            }

            if (a != null) return@associateWith handleReturnType(ctx, possibleType, ctx.fragments.getValue(a.name.value).second)

            val b = selections?.filterIsInstance<InlineFragmentNode>()?.find {
                possibleType.name == it.typeCondition?.name?.value
            }

            if (b != null) return@associateWith handleReturnType(ctx, possibleType, b.selectionSet)

            throw RequestException("Missing type argument for type ${possibleType.name}")
        }

        return Execution.Union (
            unionField = field,
            memberChildren = unionMembersChildren,
            key = selectionNode.name.value,
            alias = selectionNode.alias?.value,
            condition = null,
            directives = selectionNode.directives?.lookup()
        )

    }

    private fun throwUnknownFragmentTypeEx(fragment: Fragment) : RequestException {
        return RequestException("Unknown type ${fragment.typeCondition} in type condition on fragment ${fragment.fragmentKey}")
    }
    private fun throwUnknownFragmentTypeEx(fragment: FragmentNode) = when (fragment) {
        is FragmentSpreadNode -> TODO("No Clue")
        is InlineFragmentNode -> RequestException("Unknown type ${fragment.typeCondition?.name?.value} in type condition on fragment ${fragment.typeCondition?.name?.value}")
    }


    private fun handleUnsupportedOperations(keys: List<String>) {
        keys.forEach { key ->
            if (!schemaModel.query.hasField(key) && !schemaModel.mutation.hasField(key)) {
                throw RequestException("$key is not supported by this schema")
            }
        }
    }

//    fun List<DirectiveInvocation>.lookup() = associate { findDirective(it) to it.arguments }
    fun List<DirectiveNode>.lookup() = associate { findDirective(it) to it.arguments?.toArguments() }

    fun findDirective(invocation: DirectiveNode): Directive {
        return directivesByName[invocation.name.value.removePrefix("@")]
            ?: throw RequestException("Directive ${invocation.name.value} does not exist")
    }

    fun findDirective(invocation : DirectiveInvocation) : Directive {
        return directivesByName[invocation.key.removePrefix("@")]
                ?: throw RequestException("Directive ${invocation.key} does not exist")
    }
}
