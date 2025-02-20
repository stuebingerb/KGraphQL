package com.apurebase.kgraphql.stitched.schema.execution

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.GraphqlRequest
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.model.ast.OperationTypeNode
import com.apurebase.kgraphql.schema.model.ast.SelectionNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.structure.Field
import com.apurebase.kgraphql.schema.structure.Type
import com.apurebase.kgraphql.stitched.RemoteExecutionException
import com.apurebase.kgraphql.stitched.StitchedGraphqlRequest
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

abstract class AbstractRemoteRequestExecutor(private val objectMapper: ObjectMapper) : RemoteRequestExecutor {

    /**
     * Executes the actual [request] against the given [url] in the current [ctx]. This function is intended to
     * be implemented by consumers to execute the actual request.
     */
    abstract suspend fun executeRequest(url: String, request: StitchedGraphqlRequest, ctx: Context): String

    /**
     * Main entry point called from the local request executor for the given [node] and [ctx].
     */
    final override suspend fun execute(node: Execution.Remote, ctx: Context): JsonNode? {
        val remoteUrl = node.remoteUrl
        val request = toGraphQLRequest(node, ctx)
        val response = runCatching {
            executeRequest(remoteUrl, request, ctx)
        }.getOrElse {
            """
            { "errors": [ { "message": "${it.message}" } ] }
            """.trimIndent()
        }
        val responseJson = objectMapper.readTree(response)
        responseJson["errors"]?.let { errors ->
            // TODO: properly transfer errors from the remote execution
            val messages = (errors as? ArrayNode)?.map { (it as? ObjectNode)?.get("message")?.textValue() }
                ?: listOf("Error(s) during remote execution")
            throw RemoteExecutionException(message = messages.joinToString(", "), node = node)
        }
        return responseJson["data"]?.get(node.remoteOperation)
    }

    private fun SelectionNode.FieldNode.alias() = alias?.let {
        "${it.value}: "
    } ?: ""

    /**
     * Converts the given [node] to a [GraphqlRequest] in the given [ctx].
     */
    private fun toGraphQLRequest(node: Execution.Remote, ctx: Context): StitchedGraphqlRequest {
        val operation = when (node.operationType) {
            OperationTypeNode.QUERY -> "query"
            OperationTypeNode.MUTATION -> "mutation"
            OperationTypeNode.SUBSCRIPTION -> "subscription"
        }
        val variablesString = node.variables?.takeIf { it.isNotEmpty() }?.map {
            "${it.variable.valueNodeName}: ${it.type.typeReference()}"
        }?.joinToString(separator = ",", prefix = "(", postfix = ") ") { it } ?: ""
        val query = buildString {
            append("$operation $variablesString{")
            append(node.remoteOperation)
            val inputArgs = node.arguments?.entries?.takeIf { it.isNotEmpty() }?.let { entries ->
                entries.joinToString(prefix = "(", postfix = ")") { (key, value) ->
                    "$key: ${value.valueNodeName}"
                }
            } ?: ""
            append(inputArgs)
            addSelectionsForField(node)
            appendLine("}")
            node.namedFragments?.forEach { fragment ->
                addSelectionsForFragment(fragment)
            }
        }
        val variables = ctx.get<Variables>()?.getRaw()
        return StitchedGraphqlRequest(query = query, variables = variables)
    }

    private fun StringBuilder.addSelectionsForField(node: Execution.Remote) {
        val filteredSelections =
            (node.selectionNode as? SelectionNode.FieldNode)?.selectionSet?.selections?.filterForType(node.field.returnType)
                .orEmpty()
        if (filteredSelections.isNotEmpty()) {
            append("{")
            filteredSelections.forEach {
                addSelection(it, node.field.returnType)
            }
            append("}")
        }
    }

    private fun StringBuilder.addSelectionsForFragment(fragment: Execution.Fragment) {
        val fragmentName =
            (fragment.selectionNode as SelectionNode.FragmentNode.FragmentSpreadNode).name.value
        val filteredSelections = fragment.elements.map { it.selectionNode }.filterForType(fragment.condition.onType)
        if (filteredSelections.isNotEmpty()) {
            appendLine("fragment $fragmentName on ${fragment.condition.onType.name} {")
            filteredSelections.forEach {
                addSelection(it, fragment.condition.onType)
            }
            appendLine("}")
        }
    }

    /**
     * Filters [this] list of selection nodes for fields that belong to the given [type] itself, i.e. are not
     * stitched from a different schema.
     */
    private fun List<SelectionNode>.filterForType(type: Type): List<SelectionNode> {
        val availableFieldNames: Set<String> =
            type.unwrapped().fields?.filterNot { it is Field.RemoteOperation<*, *> }?.mapTo(mutableSetOf()) { it.name }
                .orEmpty() + "__typename"
        return filter { it !is SelectionNode.FieldNode || it.name.value in availableFieldNames }
    }

    private fun StringBuilder.addSelection(selection: SelectionNode, type: Type) {
        when (selection) {
            is SelectionNode.FieldNode -> {
                val selectionArgs = selection.arguments?.takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = ",", prefix = "(", postfix = ")") { argumentNode ->
                        val argumentValue = if (argumentNode.value is ValueNode.VariableNode) {
                            "$${argumentNode.value.valueNodeName}"
                        } else {
                            argumentNode.value.valueNodeName
                        }
                        "${argumentNode.name.value}: $argumentValue"
                    } ?: ""
                val nodeSelections = selection.selectionSet?.selections.orEmpty()
                val currentType =
                    type.unwrapped().fields?.firstOrNull { it.name == selection.name.value }?.returnType ?: type
                val filteredSelections = nodeSelections.filterForType(currentType)
                appendLine("${selection.alias()}${selection.name.value}$selectionArgs")
                if (filteredSelections.isNotEmpty()) {
                    appendLine("{")
                    filteredSelections.forEach { sub ->
                        addSelection(sub, currentType)
                    }
                    appendLine("}")
                }
            }

            is SelectionNode.FragmentNode.FragmentSpreadNode -> {
                appendLine("...${selection.name.value}")
            }

            is SelectionNode.FragmentNode.InlineFragmentNode -> {
                appendLine("...on ${selection.typeCondition!!.name.value} {")
                selection.selectionSet.selections.forEach { sub ->
                    addSelection(sub, type)
                }
                appendLine("}")
            }
        }
    }
}
