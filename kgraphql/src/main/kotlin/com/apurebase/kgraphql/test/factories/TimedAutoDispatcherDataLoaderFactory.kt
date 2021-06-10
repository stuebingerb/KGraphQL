package com.apurebase.kgraphql.test.factories

import com.apurebase.kgraphql.test.BatchLoader
import com.apurebase.kgraphql.test.DataLoaderOptions
import com.apurebase.kgraphql.test.ExecutionResult
import com.apurebase.kgraphql.test.TimedAutoDispatcherImpl
import com.apurebase.kgraphql.test.TimedAutoDispatcherDataLoaderOptions

class TimedAutoDispatcherDataLoaderFactory<K, R>(
    optionsFactory: () -> TimedAutoDispatcherDataLoaderOptions<K, R>,
    cachePrimes: Map<K, ExecutionResult<R>>,
    batchLoader: BatchLoader<K, R>
) : DataLoaderFactory<K, R>(optionsFactory, batchLoader, cachePrimes, { _: DataLoaderOptions<K, R>, bl: BatchLoader<K, R>, parent ->
    TimedAutoDispatcherImpl(optionsFactory(), bl, parent)
})
