package com.apurebase.kgraphql.schema.execution

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class KJsonObjectBuilder {

    internal val content: MutableMap<String, JsonElement> = linkedMapOf()

    /**
     * Adds given [value] to the current [JsonObject] with [this] as a key.
     */
    public infix fun String.to(value: JsonElement) {
        require(content[this] == null) {"Key $this is already registered in builder"}
        content[this] = value
    }

    /**
     * Adds given [value] as [JsonPrimitive] to the current [JsonObject] with [this] as a key.
     */
    public infix fun String.to(value: Number?) {
        require(content[this] == null) {"Key $this is already registered in builder"}
        content[this] = JsonPrimitive(value)
    }

    /**
     * Adds given [value] as [JsonPrimitive] to the current [JsonObject] with [this] as a key.
     */
    public infix fun String.to(value: Boolean?) {
        require(content[this] == null) {"Key $this is already registered in builder"}
        content[this] = JsonPrimitive(value)
    }

    /**
     * Adds given [value] as [JsonPrimitive] to the current [JsonObject] with [this] as a key.
     */
    public infix fun String.to(value: String?) {
        require(content[this] == null) {"Key $this is already registered in builder"}
        content[this] = JsonPrimitive(value)
    }
}

suspend fun kson(init: suspend KJsonObjectBuilder.() -> Unit): JsonObject {
    val builder = KJsonObjectBuilder()
    builder.init()
    return JsonObject(builder.content)
}
