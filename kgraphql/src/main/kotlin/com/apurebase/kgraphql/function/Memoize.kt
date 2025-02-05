package com.apurebase.kgraphql.function

import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.asLoadingCache
import kotlinx.coroutines.CoroutineScope

internal inline fun <X, Y> memoize(scope: CoroutineScope, memorySize: Long, crossinline f: suspend (X) -> Y): suspend (X) -> Y {
    val cache = Caffeine
        .newBuilder()
        .maximumSize(memorySize)
        .asLoadingCache<X, Y>(scope) { f(it) }

    return { cache.get(it) }
}
