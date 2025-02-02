package com.apurebase.kgraphql.function

import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.asLoadingCache
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext

fun <X : Any, Y : Any> (suspend (X) -> Y).memoized(scope: CoroutineScope = CoroutineScope(EmptyCoroutineContext), memorySize: Long): suspend (X) -> Y {
    val cache = Caffeine
        .newBuilder()
        .maximumSize(memorySize)
        .asLoadingCache<X, Y>(scope) { this(it) }

    return { cache.get(it) }
}
