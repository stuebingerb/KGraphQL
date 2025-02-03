package com.apurebase.kgraphql.schema.execution.stitched

import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.execution.ParallelRequestExecutor.ExecutionContext
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.introspection.__InputValue
import com.apurebase.kgraphql.schema.introspection.__Type
import com.apurebase.kgraphql.schema.model.ast.ArgumentNode
import com.apurebase.kgraphql.schema.model.ast.ArgumentNodes
import com.apurebase.kgraphql.schema.model.ast.NameNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.stitched.LinkArgument
import com.apurebase.kgraphql.schema.structure.InputValue
import com.apurebase.kgraphql.schema.structure.SchemaModel
import com.apurebase.kgraphql.schema.structure.Type
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.FloatNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode

class RemoteArgumentTransformer(val schema: SchemaModel) {
    fun transformArguments(
        receiver: Any?,
        inputValues: List<InputValue<*>>,
        executionNode: Execution.Remote,
        ctx: ExecutionContext,
        argsFromParent: Map<__InputValue, String>
    ): List<Any>? {
        // Resolve input arguments before calling remote schemas
        val nodeArgs = executionNode.arguments.orEmpty()
        val usedVariableNames =
            nodeArgs.values.filterIsInstance<ValueNode.VariableNode>().mapTo(HashSet()) { it.name.value }
        val mappedNodeArgs = nodeArgs.map {
            it.key to it.value
        } + argsFromParent.map {
            // TODO: we should be able to use the type as-is (but in case of INPUT_OBJECT it is missing inputFields)
            val expectedType = findType(it.key.type.unwrapped().name)
            val parentValue = (receiver as? ObjectNode)?.get(it.value).toValueNode(expectedType)
            if (parentValue is ValueNode.NullValueNode && it.key.type.kind == TypeKind.NON_NULL) {
                // parentValue is null but required for the remote operation; return null to skip call
                // TODO: improve architecture
                return null
            }
            it.key.name to parentValue
        }
        val mappedArgumentNodes = ArgumentNodes(mappedNodeArgs.map {
            ArgumentNode(null, NameNode(it.first, null), it.second)
        })
        return inputValues.mapNotNull { parameter ->
            when {
                parameter.type.isInstance(ctx.requestContext) -> ctx.requestContext + ctx.variables
                parameter.type.isInstance(executionNode) -> Execution.Remote(
                    selectionNode = executionNode.selectionNode,
                    field = executionNode.field,
                    children = executionNode.children,
                    key = executionNode.key,
                    alias = executionNode.alias,
                    arguments = mappedArgumentNodes,
                    directives = executionNode.directives,
                    variables = executionNode.variables?.filter { it.variable.name.value in usedVariableNames },
                    namedFragments = executionNode.namedFragments,
                    remoteUrl = executionNode.remoteUrl,
                    remoteOperation = executionNode.remoteOperation,
                    operationType = executionNode.operationType
                )

                else -> null
            }
        }
    }

    private fun __Type.unwrapped(): __Type = when (kind) {
        TypeKind.NON_NULL, TypeKind.LIST -> (ofType as __Type).unwrapped()
        else -> this
    }

    // TODO: make use of JsonNodeType instead?
    private fun JsonNode?.toValueNode(expectedType: __Type): ValueNode = when (this) {
        is BooleanNode -> ValueNode.BooleanValueNode(booleanValue(), null)
        is IntNode -> ValueNode.NumberValueNode(longValue(), null)
        is LongNode -> ValueNode.NumberValueNode(longValue(), null)
        is DoubleNode -> ValueNode.DoubleValueNode(doubleValue(), null)
        is FloatNode -> ValueNode.DoubleValueNode(doubleValue(), null)
        is TextNode -> if (expectedType.unwrapped().kind == TypeKind.ENUM) {
            ValueNode.EnumValueNode(textValue(), null)
        } else {
            // TODO: what about multiline strings?
            ValueNode.StringValueNode(textValue(), false, null)
        }

        is ArrayNode -> ValueNode.ListValueNode(map { it.toValueNode(expectedType) }, null)
        is ObjectNode -> ValueNode.ObjectValueNode(
            properties().filterNot { it.key.startsWith("__") }.map { prop ->
                val inputFields = checkNotNull(expectedType.inputFields) {
                    "Expected INPUT_OBJECT for ${expectedType.name} but got ${expectedType.kind}"
                }
                val expectedPropType =
                    findType(inputFields.firstOrNull { it.name == prop.key }?.type?.unwrapped()?.name)
                ValueNode.ObjectValueNode.ObjectFieldNode(
                    null,
                    NameNode(prop.key, null),
                    prop.value.toValueNode(expectedPropType)
                )
            },
            null
        )

        is NullNode, null -> ValueNode.NullValueNode(null)
        else -> error("Unexpected value: $this")
    }

    private fun findType(typeName: String?): Type = typeName?.let { schema.allTypesByName[it] }
        ?: throw IllegalArgumentException("$typeName has not been registered in this schema")
}
