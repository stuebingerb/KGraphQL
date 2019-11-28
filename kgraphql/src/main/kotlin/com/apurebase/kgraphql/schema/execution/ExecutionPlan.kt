package com.apurebase.kgraphql.schema.execution

class ExecutionPlan (
    val operations: List<Execution.Node>,
    val dataLoaderRegistry: DataLoaderRegistry? = null
) : List<Execution.Node> by operations {
    var isSubscription = false
}
