@file:Suppress("MemberVisibilityCanBePrivate")

package com.apurebase.kgraphql.test.dsl

import com.apurebase.kgraphql.test.BatchMode
import com.apurebase.kgraphql.test.DataLoaderOptions

class DataLoaderOptionsDSL<K, R> {

    private var cacheDefinitionDSL = CacheDefinitionDSL<K, R>()

    /**
     * The mode of batching
     */
    var batchMode: BatchMode = BatchMode.LoadInBatch()

    /**
     * The cache implementation definition
     */
    fun cache(block: CacheDefinitionDSL<K, R>.() -> Unit) {
        cacheDefinitionDSL.block()
    }


    internal fun toDataLoaderOptions() = DataLoaderOptions(
        cache = cacheDefinitionDSL.getConfiguredInstance(),
        cacheExceptions = cacheDefinitionDSL.cacheExceptions,
        batchMode = batchMode
    )

}
