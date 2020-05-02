package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.configuration.SchemaConfiguration

data class ExecutionOptions(
    /**
     * If null it'll fallback to the default from [SchemaConfiguration].
     */
    val executor: Executor? = null
)
