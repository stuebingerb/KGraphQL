package com.apurebase.kgraphql.helpers

import com.apurebase.kgraphql.InvalidInputValueException
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
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

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
                .filterNot { it is Execution.Node && it.children.isNotEmpty() }
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
            is Map<*, *> -> list.add(value.toJsonElement())
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
            is Map<*, *> -> map[key] = value.toJsonElement()
            is Collection<*> -> map[key] = value.toJsonElement()
            is Array<*> -> map[key] = value.toList().toJsonElement()
            else -> map[key] = JsonPrimitive(value.toString())  // other type
        }
    }
    return JsonObject(map)
}

/**
 * Converts this [JsonNode] to a [ValueNode], applying special treatment for doubles and floats, as specified by
 * https://spec.graphql.org/October2021/#sec-Scalars.Input-Coercion:
 *
 * "GraphQL has different constant literals to represent integer and floating-point input values, and coercion rules
 * may apply differently depending on which type of input value is encountered. GraphQL may be parameterized by
 * variables, the values of which are often serialized when sent over a transport like HTTP. Since some common
 * serializations (ex. JSON) do not discriminate between integer and floating-point values, they are interpreted as an
 * integer input value if they have an empty fractional part (ex. 1.0) and otherwise as floating-point input value."
 */
fun JsonNode?.toValueNode(expectedType: __Type): ValueNode = when (this) {
    is BooleanNode -> ValueNode.BooleanValueNode(booleanValue(), null)
    is IntNode, is LongNode -> ValueNode.NumberValueNode(longValue(), null)
    is DoubleNode, is FloatNode -> if (doubleValue().isWholeNumber()) {
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
            val expectedPropType = inputFields.firstOrNull { it.name == prop.key }?.type
                ?: throw InvalidInputValueException(
                    "Property '${prop.key}' on '${expectedType.unwrapped().name}' does not exist",
                    null
                )
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

internal fun Double.isWholeNumber() = this % 1.0 == 0.0

/**
 * Returns the (single) constructor to use during input value generation. This is either the
 * [primaryConstructor] for Kotlin classes, or the single constructor for Java classes.
 *
 * Adapted from Jackson Kotlin's `primarilyConstructor()`
 */
internal fun KClass<*>.singleConstructor() = primaryConstructor ?: constructors.singleOrNull()
