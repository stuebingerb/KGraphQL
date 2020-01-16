package com.apurebase.kgraphql.schema.execution

import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory


suspend fun deferredJsonBuilder(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    init: suspend DeferredJsonMap.() -> Unit
): JsonObject {
    val logger = LoggerFactory.getLogger("DeferredJsonBuilder")

    return try {
        val builder = DeferredJsonMap(dispatcher, logger)

        logger.debug("initializing builder")
        builder.init()


        logger.debug("awaiting builder")
        builder.awaitAll()

        logger.debug("completing builder")
        builder.build()
    } catch (e: CancellationException) {
        throw e.cause ?: e
    }
}

