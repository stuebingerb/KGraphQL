package nidomiro.kdataloader.factories

import nidomiro.kdataloader.BatchLoader
import nidomiro.kdataloader.DataLoaderOptions
import nidomiro.kdataloader.ExecutionResult
import nidomiro.kdataloader.TimedAutoDispatcherDataLoaderOptions
import nidomiro.kdataloader.TimedAutoDispatcherImpl

class TimedAutoDispatcherDataLoaderFactory<K, R>(
    optionsFactory: () -> TimedAutoDispatcherDataLoaderOptions<K, R>,
    cachePrimes: Map<K, ExecutionResult<R>>,
    batchLoader: BatchLoader<K, R>,
) : DataLoaderFactory<K, R>(
    optionsFactory,
    batchLoader,
    cachePrimes,
    { _: DataLoaderOptions<K, R>, bl: BatchLoader<K, R>, parent ->
        TimedAutoDispatcherImpl(optionsFactory(), bl, null)
    }
)
