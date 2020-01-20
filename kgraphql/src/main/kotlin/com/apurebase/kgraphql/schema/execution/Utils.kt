package com.apurebase.kgraphql.schema.execution

import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS


suspend fun deferredJsonBuilder(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    logger: Logger? = null,
    timeout: Long? = null,
    init: suspend DeferredJsonMap.() -> Unit
): JsonObject {
    val block: suspend () -> JsonObject = {
        try {
            val builder = DeferredJsonMap(dispatcher, logger)

            logger?.debug("initializing object builder")
            builder.init()


            logger?.debug("awaiting object builder")
            builder.awaitAll()

            logger?.debug("completing object builder")
            builder.build()
        } catch (e: CancellationException) {
            throw e.cause ?: e
        }
    }
    return timeout?.let { withTimeout(it) { block() } } ?: block()
}

