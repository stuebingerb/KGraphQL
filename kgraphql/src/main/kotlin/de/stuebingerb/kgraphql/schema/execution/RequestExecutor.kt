package de.stuebingerb.kgraphql.schema.execution

import de.stuebingerb.kgraphql.Context
import de.stuebingerb.kgraphql.request.VariablesJson

internal interface RequestExecutor {
    suspend fun suspendExecute(plan: ExecutionPlan, variables: VariablesJson, context: Context): String
}
