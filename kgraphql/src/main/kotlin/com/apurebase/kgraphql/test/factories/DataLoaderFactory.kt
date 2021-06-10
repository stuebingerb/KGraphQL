package com.apurebase.kgraphql.test.factories

import com.apurebase.kgraphql.test.*
import com.apurebase.kgraphql.test.prime
import kotlinx.coroutines.Job

typealias DataLoaderFactoryMethod<K, R> = (options: DataLoaderOptions<K, R>, batchLoader: BatchLoader<K, R>, parent: Job?) -> DataLoader<K, R>

open class DataLoaderFactory<K, R>(
    @Suppress("MemberVisibilityCanBePrivate")
    protected val optionsFactory: () -> DataLoaderOptions<K, R>,
    @Suppress("MemberVisibilityCanBePrivate")
    protected val batchLoader: BatchLoader<K, R>,
    @Suppress("MemberVisibilityCanBePrivate")
    protected val cachePrimes: Map<K, ExecutionResult<R>>,
    protected val factoryMethod: DataLoaderFactoryMethod<K, R>
) {

    suspend fun constructNew(parent: Job? = null): DataLoader<K, R> {
        val dataLoader = factoryMethod(optionsFactory(), batchLoader, parent)
        cachePrimes.forEach { (key, value) -> dataLoader.prime(key, value) }
        return dataLoader
    }
}
