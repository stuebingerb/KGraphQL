package com.apurebase.kgraphql.schema.execution

import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject

suspend fun deferredJsonBuilder(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    timeout: Long? = null,
    init: suspend DeferredJsonMap.() -> Unit
): JsonObject {
    val block: suspend () -> JsonObject = {
        try {
            val builder = DeferredJsonMap(dispatcher)
            builder.init()
            builder.awaitAll()
            builder.build()
        } catch (e: CancellationException) {
            throw e.cause ?: e
        }
    }
    return timeout?.let { withTimeout(it) { block() } } ?: block()
}

