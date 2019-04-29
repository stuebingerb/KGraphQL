package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.request.VariablesJson


interface RequestExecutor {
    fun execute(plan : ExecutionPlan, variables: VariablesJson, context: Context) : String
    suspend fun suspendExecute(plan : ExecutionPlan, variables: VariablesJson, context: Context) : String
}