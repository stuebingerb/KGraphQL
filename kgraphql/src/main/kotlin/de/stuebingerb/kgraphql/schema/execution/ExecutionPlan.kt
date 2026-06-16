package de.stuebingerb.kgraphql.schema.execution

import de.stuebingerb.kgraphql.schema.model.ast.VariableDefinitionNode
import de.stuebingerb.kgraphql.schema.structure.Type

/**
 * Execution mode to use when executing the plan. "Normal" mode is in fact parallel mode but we
 * stick to the name used in the spec.
 *
 * cf. https://spec.graphql.org/September2025/#sec-Normal-and-Serial-Execution
 */
enum class ExecutionMode {
    Normal, Serial
}

internal class ExecutionPlan(
    val executionMode: ExecutionMode,
    val operations: List<Execution>,
    val root: Type,
    val declaredVariables: List<VariableDefinitionNode>?
) : List<Execution> by operations
