package com.apurebase.kgraphql.schema.execution

class ExecutionPlan(
    val operations: List<Execution.Node>
) : List<Execution.Node> by operations {
    var isSubscription = false
}
