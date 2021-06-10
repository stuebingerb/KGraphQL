package com.apurebase.kgraphql.test

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import com.apurebase.kgraphql.test.statistics.SimpleStatisticsCollector

class TimedAutoDispatcherImpl<K, R>(
    options: TimedAutoDispatcherDataLoaderOptions<K, R>,
    batchLoader: BatchLoader<K, R>,
    parent: Job? = null,
) : SimpleDataLoaderImpl<K, R>(options, SimpleStatisticsCollector(), batchLoader), CoroutineScope {

    private val autoChannel = Channel<Unit>()
    override val coroutineContext = Job(parent)

    init {
        launch {
            var job: Job? = null
            for (msg in autoChannel) {
                job?.cancel()
                job = launch {
                    delay(options.waitInterval)
                    if (isActive) {
                        dispatch()
                    }
                }
            }
        }
    }

    suspend fun cancel() {
        coroutineContext.cancel()
        autoChannel.close()
        dispatch()
    }

    override suspend fun loadAsync(key: K): Deferred<R> {
        return super.loadAsync(key).also { autoChannel.send(Unit) }
    }

    override suspend fun loadManyAsync(vararg keys: K): Deferred<List<R>> {
        return super.loadManyAsync(*keys).also { autoChannel.send(Unit) }
    }

    override suspend fun clear(key: K) {
        super.clear(key).also { autoChannel.send(Unit) }
    }

    override suspend fun clearAll() {
        super.clearAll().also { autoChannel.send(Unit) }
    }

    override suspend fun prime(key: K, value: R) {
        super.prime(key, value).also { autoChannel.send(Unit) }
    }

    override suspend fun prime(key: K, value: Throwable){
        super.prime(key, value).also { autoChannel.send(Unit) }
    }

}
