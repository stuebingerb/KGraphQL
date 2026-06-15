package de.stuebingerb.kgraphql.stitched.schema.execution

import com.fasterxml.jackson.databind.JsonNode
import de.stuebingerb.kgraphql.Context
import de.stuebingerb.kgraphql.ExperimentalAPI
import de.stuebingerb.kgraphql.schema.execution.Execution

/**
 * Interface for remote request execution, used during schema stitching (only)
 */
@ExperimentalAPI
interface RemoteRequestExecutor {
    // ParallelRequestExecutor expects a JsonNode as result of any execution
    suspend fun execute(node: Execution.Remote, ctx: Context): JsonNode?
}
