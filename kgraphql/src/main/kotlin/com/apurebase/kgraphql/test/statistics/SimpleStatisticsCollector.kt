package com.apurebase.kgraphql.test.statistics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

class SimpleStatisticsCollector : StatisticsCollector {

    private val internalStatistics = Statistics()

    private val statisticsScope = CoroutineScope(Dispatchers.Default)
    private var statisticsJobs = mutableListOf<Deferred<*>>()

    override suspend fun incLoadAsyncMethodCalledAsync() =
        executeInStatisticsScopeAsync { internalStatistics.loadAsyncMethodCalled.incrementAndGet() }

    override suspend fun incLoadManyAsyncMethodCalledAsync() =
        executeInStatisticsScopeAsync { internalStatistics.loadAsyncManyMethodCalled.incrementAndGet() }

    override suspend fun incDispatchMethodCalledAsync() =
        executeInStatisticsScopeAsync { internalStatistics.dispatchMethodCalled.incrementAndGet() }

    override suspend fun incClearMethodCalledAsync() =
        executeInStatisticsScopeAsync { internalStatistics.clearMethodCalled.incrementAndGet() }

    override suspend fun incClearAllMethodCalledAsync() =
        executeInStatisticsScopeAsync { internalStatistics.clearAllMethodCalled.incrementAndGet() }

    override suspend fun incPrimeMethodCalledAsync() =
        executeInStatisticsScopeAsync { internalStatistics.primeMethodCalled.incrementAndGet() }

    override suspend fun incObjectsRequestedAsync(objects: Long) =
        executeInStatisticsScopeAsync { internalStatistics.objectsRequested.addAndGet(objects) }

    override suspend fun incBatchCallsExecutedAsync() =
        executeInStatisticsScopeAsync { internalStatistics.batchCallsExecuted.incrementAndGet() }

    override suspend fun incCacheHitCountAsync() =
        executeInStatisticsScopeAsync { internalStatistics.cacheHitCount.incrementAndGet() }

    private suspend fun <T> executeInStatisticsScopeAsync(block: suspend () -> T): Deferred<T> {
        val deferred = statisticsScope.async { block() }
        statisticsJobs.add(deferred)
        return deferred
    }

    override suspend fun createStatisticsSnapshot(): DataLoaderStatistics {
        val currentJobs = statisticsJobs
        statisticsJobs = mutableListOf()
        currentJobs.forEach { it.await() }
        return internalStatistics.snapshot()
    }


    internal data class Statistics(
        var loadAsyncMethodCalled: AtomicLong = AtomicLong(0),
        val loadAsyncManyMethodCalled: AtomicLong = AtomicLong(0),
        val dispatchMethodCalled: AtomicLong = AtomicLong(0),
        val clearMethodCalled: AtomicLong = AtomicLong(0),
        val clearAllMethodCalled: AtomicLong = AtomicLong(0),
        val primeMethodCalled: AtomicLong = AtomicLong(0),
        val objectsRequested: AtomicLong = AtomicLong(0),
        val batchCallsExecuted: AtomicLong = AtomicLong(0),
        val cacheHitCount: AtomicLong = AtomicLong(0)
    ) {
        fun snapshot() = DataLoaderStatistics(
            loadAsyncMethodCalled = this@Statistics.loadAsyncMethodCalled.get(),
            loadManyAsyncMethodCalled = this@Statistics.loadAsyncManyMethodCalled.get(),
            dispatchMethodCalled = this@Statistics.dispatchMethodCalled.get(),
            clearMethodCalled = this@Statistics.clearMethodCalled.get(),
            clearAllMethodCalled = this@Statistics.clearAllMethodCalled.get(),
            primeMethodCalled = this@Statistics.primeMethodCalled.get(),
            objectsRequested = this@Statistics.objectsRequested.get(),
            batchCallsExecuted = this@Statistics.batchCallsExecuted.get(),
            cacheHitCount = this@Statistics.cacheHitCount.get()
        )
    }
}
