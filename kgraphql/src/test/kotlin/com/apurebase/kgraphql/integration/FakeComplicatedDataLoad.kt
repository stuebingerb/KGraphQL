package com.apurebase.kgraphql.integration

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.random.nextLong

class FakeComplicatedDataLoad : CoroutineScope {
    override val coroutineContext = SupervisorJob()

    private val cache1 = SuspendCache.suspendCache<Pair<Long, String>, String> { (wait, key) ->
        delay(wait)
        "$wait-$key"
    }
    private val cache2 = SuspendCache.suspendCache<Pair<Long, String>, String> { (wait, key) ->
        delay(wait + Random.nextLong(1..3L))
        "$key-$wait"
    }

    suspend fun loadValue(returnValue: String, delay: Long = 50) = coroutineScope {
        async(CoroutineName("FakeComplicatedDataLoad:loadValue:$returnValue:$delay")) {
            "${cache1.get(delay to returnValue)}:${cache2.get(delay to returnValue)}"
        }.await()
    }

}
