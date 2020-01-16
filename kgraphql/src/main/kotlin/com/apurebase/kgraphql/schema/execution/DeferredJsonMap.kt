package com.apurebase.kgraphql.schema.execution

import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.slf4j.Logger

class DeferredJsonMap internal constructor(
    private val dispatcher: CoroutineDispatcher,
    private val logger: Logger? = null
): CoroutineScope {

    internal val job = Job()
    override val coroutineContext = (dispatcher + job)

    private val deferredMap = mutableMapOf<String, Deferred<JsonElement>>()
    private val unDefinedDeferredMap = mutableMapOf<String, DeferredJsonMap>()
    private val moreJobs = mutableListOf<Deferred<Unit>>()
    private var completedMap: Map<String, JsonElement>? = null

    infix fun String.toValue(element: JsonElement) {
        this toDeferredValue CompletableDeferred(element)
    }

    infix fun String.toDeferredValue(element: Deferred<JsonElement>) {
//        require(deferredMap[this] == null) { "Key '$this' is already registered in builder" }
        deferredMap[this] = element
    }

    suspend infix fun String.toDeferredObj(block: suspend DeferredJsonMap.() -> Unit) {
        if (unDefinedDeferredMap[this] != null) {
            val prevMap = unDefinedDeferredMap.getValue(this)
            block(prevMap)
        } else {
            val map = DeferredJsonMap(dispatcher, logger)
            block(map)
            unDefinedDeferredMap[this] = map
        }
    }

    suspend infix fun String.toDeferredArray(block: suspend DeferredJsonArray.() -> Unit) {
        val array = DeferredJsonArray(dispatcher, logger)
        block(array)
        this@toDeferredArray toDeferredValue array.asDeferred()
    }

    fun <T> String.ebc(value: Deferred<T?>, block: suspend DeferredJsonMap.(T?) -> JsonElement) {
        val deferred = CompletableDeferred<JsonElement>()
        this@ebc toDeferredValue deferred
        deferredLaunch {
            val l = value.await()
            val ll = block(l)
            deferred.complete(ll)
        }
    }

    fun asDeferred() : Deferred<JsonElement> {
        return async(coroutineContext, start = CoroutineStart.LAZY) {
            awaitAll()
            build()
        }
    }

    suspend fun awaitAll() {
        logger?.trace("awaiting all in map")
        check(completedMap == null) { "The deferred tree has already been awaited!" }

        (moreJobs + deferredMap.values).awaitAll()
        unDefinedDeferredMap.map { (key, map) ->
            key toDeferredValue map.asDeferred()
        }
        (moreJobs + deferredMap.values).awaitAll()
        completedMap = deferredMap.mapValues { it.value.await() }

        job.complete()
    }

    fun deferredLaunch(block: suspend DeferredJsonMap.() -> Unit) {
        moreJobs.add(async(job, start = CoroutineStart.LAZY) {
            block(this@DeferredJsonMap)
        })
    }

    fun build(): JsonObject {
        logger?.trace("completing map")
        checkNotNull(completedMap) { "The deferred tree has not been awaited!" }
        return JsonObject(completedMap!!)
    }
}
