package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.Context
import com.fasterxml.jackson.databind.JsonNode

/**
 * Interface for remote request execution, used during schema stitching (only)
 */
interface RemoteRequestExecutor {
    // ParallelRequestExecutor expects a JsonNode as result of any execution
    suspend fun execute(node: Execution.Remote, ctx: Context): JsonNode?
}
