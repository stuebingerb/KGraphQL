package com.apurebase.kgraphql.schema.execution

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.Deferred

internal suspend fun MutableMap<String, Deferred<JsonNode?>>.merge(
    key: String,
    node: Deferred<JsonNode?>
): MutableMap<String, Deferred<JsonNode?>> {
    merge(key, node, this::get, this::set)
    return this
}

internal fun ObjectNode.merge(other: ObjectNode) {
    other.properties().forEach {
        merge(it.key, it.value)
    }
}

internal fun ObjectNode.merge(key: String, node: JsonNode?) {
    merge(key, node, this::get, this::set)
}

internal suspend fun merge(
    key: String,
    node: Deferred<JsonNode?>,
    get: (String) -> Deferred<JsonNode?>?,
    set: (String, Deferred<JsonNode?>) -> Any?
) {
    val existingNode = get(key)?.await()
    if (existingNode != null) {
        val node = node.await()
        when {
            node == null -> error("trying to merge null with non-null for $key")
            node is ObjectNode -> {
                check(existingNode is ObjectNode) { "trying to merge object with simple node for $key" }
                existingNode.merge(node)
            }

            existingNode is ObjectNode -> error("trying to merge simple node with object node for $key")
            node != existingNode -> error("trying to merge different simple nodes for $key")
        }
    } else {
        set(key, node)
    }
}

internal fun merge(key: String, node: JsonNode?, get: (String) -> JsonNode?, set: (String, JsonNode?) -> Any?) {
    val existingNode = get(key)
    if (existingNode != null) {
        when {
            node == null -> error("trying to merge null with non-null for $key")
            node is ObjectNode -> {
                check(existingNode is ObjectNode) { "trying to merge object with simple node for $key" }
                existingNode.merge(node)
            }

            existingNode is ObjectNode -> error("trying to merge simple node with object node for $key")
            node != existingNode -> error("trying to merge different simple nodes for $key")
        }
    } else {
        set(key, node)
    }
}
