package com.apurebase.kgraphql.schema.jol

import com.apurebase.kgraphql.schema.jol.error.GraphQLError

data class ExecutionResult(
    val data: Map<String, Any>? = null,
    val errors: List<GraphQLError>? = null
)
