package com.apurebase.kgraphql.schema.execution

import com.fasterxml.jackson.databind.node.NullNode
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

fun main() = runBlocking {
    kson2(Dispatchers.Default) {

        "Hello" to JsonPrimitive("World")

        "nest" toObj {
            "abc" to JsonPrimitive("123")
            delay(100)

            "nothing" to JsonNull

            (1..3).map {
                launch {
                    delay(50)
                    "q$it" to JsonPrimitive(it)

                    "list".toArr(listOf(1,2,3)) {
                        obj {
                            "id" to JsonPrimitive(it)
                            "more" arr listOf(JsonPrimitive(1), JsonPrimitive(2), JsonPrimitive(3))
                        }
                    }
                }
            }
            "nothing2" to JsonNull

        }

    }.toString().let(::println)
}

suspend fun kson2(dispatcher: CoroutineDispatcher, init: suspend DeferredJsonMap.() -> Unit): JsonObject {
    val builder = DeferredJsonMap(dispatcher)
    builder.init()
    val value =  builder.build()
    println("Build finished")
    return value
}

open class DeferredJsonMap(private val dispatcher: CoroutineDispatcher): CoroutineScope, MutableMap<String, JsonElement> by linkedMapOf() {

    internal val job = SupervisorJob()
    override val coroutineContext = (dispatcher + job)

    infix fun String.to(element: JsonElement) {
        require(get(this) == null) {"Key '$this' is already registered in builder"}
        put(this, element)
    }

    infix fun String.arr(values: List<JsonElement>) {
        require(get(this) == null) {"Key '$this' is already registered in builder"}
        put(this, JsonArray(values))
    }

    suspend fun <T> arr(values: Collection<T>, block: suspend (T) -> JsonElement): JsonArray = coroutineScope {
        values.map { v ->
            async(job) { block(v) }
        }.awaitAll().let(::JsonArray)

    }

    suspend fun <T> String.toArr(values: Collection<T>, block: suspend (T) -> JsonElement) = coroutineScope {
        require(get(this@toArr) == null) {"Key '$this' is already registered in builder"}
        put(this@toArr, arr(values, block))
    }

    suspend fun obj(block: suspend DeferredJsonMap.() -> Unit): JsonObject = coroutineScope {
        DeferredJsonMap(dispatcher).also {
            block(it)
            it.job.complete()
        }.build()
    }

    suspend infix fun String.toObj(block: suspend DeferredJsonMap.() -> Unit): Unit = coroutineScope {
        require(get(this@toObj) == null) {"Key '$this' is already registered in builder"}
        put(this@toObj, obj(block))
        Unit
    }


    suspend fun build(): JsonObject {
        return JsonObject(this)
    }

}

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
