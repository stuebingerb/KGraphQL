package nidomiro.kdataloader.factories

import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import nidomiro.kdataloader.BatchLoader
import nidomiro.kdataloader.DataLoaderOptions
import nidomiro.kdataloader.ExecutionResult
import nidomiro.kdataloader.TimedAutoDispatcherImpl
import nidomiro.kdataloader.TimedAutoDispatcherDataLoaderOptions
import kotlin.coroutines.CoroutineContext

class TimedAutoDispatcherDataLoaderFactory<K, R>(
    optionsFactory: () -> TimedAutoDispatcherDataLoaderOptions<K, R>,
    cachePrimes: Map<K, ExecutionResult<R>>,
    batchLoader: BatchLoader<K, R>,
) : DataLoaderFactory<K, R>(optionsFactory, batchLoader, cachePrimes, { _: DataLoaderOptions<K, R>, bl: BatchLoader<K, R>, parent ->
    TimedAutoDispatcherImpl(optionsFactory(), bl, null)
})
