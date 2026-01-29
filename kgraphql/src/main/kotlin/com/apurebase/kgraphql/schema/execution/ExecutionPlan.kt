package com.apurebase.kgraphql.schema.execution

/**
 * Execution mode to use when executing the plan. "Normal" mode is in fact parallel mode but we
 * stick to the name used in the spec.
 *
 * cf. https://spec.graphql.org/September2025/#sec-Normal-and-Serial-Execution
 */
enum class ExecutionMode {
    Normal, Serial
}

class ExecutionPlan(
    val isSubscription: Boolean,
    val executionMode: ExecutionMode,
    val operations: List<Execution.Node>
) : List<Execution.Node> by operations
