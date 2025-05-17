package com.apurebase.kgraphql.integration

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

open class SuspendCache<K : Any, V>(
    private val cache: AsyncCache<K, V>,
    private val onGet: suspend (K) -> V,
) : CoroutineScope, AsyncCache<K, V> by cache {
    override val coroutineContext = SupervisorJob()
    suspend fun get(key: K): V = supervisorScope {
        try {
            getAsync(key).await()
        } catch (e: CompletionException) {
            if (e.cause != null) {
                throw e.cause!!
            } else {
                throw e
            }
        }
    }

    fun put(key: K, value: V) {
        put(key, future { value })
    }


    private fun CoroutineScope.getAsync(key: K) = get(key) { k, executor ->
        future(executor.asCoroutineDispatcher()) {
            onGet(k)
        }
    }

    companion object {
        fun <K : Any, V> suspendCache(
            timeoutDuration: Duration = 2.minutes,
            block: suspend (K) -> V,
        ) = runBlocking { suspendCache(timeoutDuration, block) }

        @Suppress("UPPER_BOUND_VIOLATED_BASED_ON_JAVA_ANNOTATIONS")
        fun <K : Any, V> CoroutineScope.suspendCache(
            timeoutDuration: Duration = 2.minutes,
            block: suspend (K) -> V,
        ) = Caffeine.newBuilder()
            .expireAfterWrite(timeoutDuration.inWholeMilliseconds, TimeUnit.SECONDS)
            .buildAsync<K, V> { k, _ -> future { block(k) } }
            .let { SuspendCache(it, block) }
    }
}


