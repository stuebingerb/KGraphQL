package com.apurebase.kgraphql.helpers

import com.apurebase.kgraphql.schema.builtin.BuiltInScalars
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.introspection.__Type
import com.apurebase.kgraphql.schema.model.ast.NameNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * This returns a list of all scalar fields requested on this type.
 */
fun Execution.getFields(): List<String> = when (this) {
    is Execution.Fragment -> elements.flatMap(Execution::getFields)
    is Execution.Node -> {
        if (children.isEmpty()) {
            listOf(key)
        } else {
            children
                .filterNot { (it is Execution.Node && it.children.isNotEmpty()) }
                .flatMap(Execution::getFields)
        }
    }
}.distinct()

/**
 * Collection : Convert to JsonElement
 */
fun Collection<*>.toJsonElement(): JsonElement {
    val list: MutableList<JsonElement> = mutableListOf()
    forEach {
        val value = it ?: return@forEach
        when (value) {
            is Number -> list.add(JsonPrimitive(value))
            is Boolean -> list.add(JsonPrimitive(value))
            is String -> list.add(JsonPrimitive(value))
            is Map<*, *> -> list.add((value).toJsonElement())
            is Collection<*> -> list.add(value.toJsonElement())
            is Array<*> -> list.add(value.toList().toJsonElement())
            else -> list.add(JsonPrimitive(value.toString())) // other type
        }
    }
    return JsonArray(list)
}

/**
 * Map : Convert to JsonElement
 */
fun Map<*, *>.toJsonElement(): JsonElement {
    val map: MutableMap<String, JsonElement> = mutableMapOf()
    forEach {
        val key = it.key as? String ?: return@forEach
        val value = it.value ?: return@forEach
        when (value) {
            is Number -> map[key] = JsonPrimitive(value)
            is Boolean -> map[key] = JsonPrimitive(value)
            is String -> map[key] = JsonPrimitive(value)
            is Map<*, *> -> map[key] = (value).toJsonElement()
            is Collection<*> -> map[key] = value.toJsonElement()
            is Array<*> -> map[key] = value.toList().toJsonElement()
            else -> map[key] = JsonPrimitive(value.toString())  // other type
        }
    }
    return JsonObject(map)
}

/**
 * Converts this [JsonNode] to a [ValueNode], applying special treatment for doubles and floats:
 *
 * Json does not differentiate between "1" and "1.0" (cf. https://json-schema.org/understanding-json-schema/reference/numeric),
 * and while it is allowed to pass "1" for a Double, it is not allowed to pass "1.0" for an Int.
 * So when the [expectedType] is INT *and* the value is a whole number, we convert a Json "1.0"
 * to [ValueNode.NumberValueNode] instead of [ValueNode.DoubleValueNode].
 */
fun JsonNode?.toValueNode(expectedType: __Type): ValueNode = when (this) {
    is BooleanNode -> ValueNode.BooleanValueNode(booleanValue(), null)
    is IntNode -> ValueNode.NumberValueNode(longValue(), null)
    is LongNode -> ValueNode.NumberValueNode(longValue(), null)

    is DoubleNode -> if (expectedType.isInt() && doubleValue() % 1.0 == 0.0) {
        ValueNode.NumberValueNode(longValue(), null)
    } else {
        ValueNode.DoubleValueNode(doubleValue(), null)
    }

    is FloatNode -> if (expectedType.isInt() && doubleValue() % 1.0 == 0.0) {
        ValueNode.NumberValueNode(longValue(), null)
    } else {
        ValueNode.DoubleValueNode(doubleValue(), null)
    }

    is TextNode -> if (expectedType.unwrapped().kind == TypeKind.ENUM) {
        ValueNode.EnumValueNode(textValue(), null)
    } else {
        // TODO: what about multiline strings?
        ValueNode.StringValueNode(textValue(), false, null)
    }

    is ArrayNode -> ValueNode.ListValueNode(map { it.toValueNode(expectedType) }, null)
    is ObjectNode -> ValueNode.ObjectValueNode(
        properties().filterNot { it.key.startsWith("__") }.map { prop ->
            val inputFields = checkNotNull(expectedType.unwrapped().inputFields) {
                "Expected INPUT_OBJECT for ${expectedType.unwrapped().name} but got ${expectedType.kind}"
            }
            val expectedPropType = inputFields.first { it.name == prop.key }.type
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

private fun __Type.isInt() = unwrapped().name == BuiltInScalars.INT.typeDef.name
