package com.apurebase.kgraphql.test.factories

import com.apurebase.kgraphql.test.BatchLoader
import com.apurebase.kgraphql.test.DataLoaderOptions
import com.apurebase.kgraphql.test.ExecutionResult
import com.apurebase.kgraphql.test.SimpleDataLoaderImpl

class SimpleDataLoaderFactory<K, R>(
    optionsFactory: () -> DataLoaderOptions<K, R>,
    cachePrimes: Map<K, ExecutionResult<R>>,
    batchLoader: BatchLoader<K, R>
) : DataLoaderFactory<K, R>(optionsFactory, batchLoader, cachePrimes, { o: DataLoaderOptions<K, R>, bl: BatchLoader<K, R>, _ ->
    SimpleDataLoaderImpl(o, bl)
})
