package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.schema.jol.DataLoader
import com.apurebase.kgraphql.schema.structure2.Field

class ExecutionPlan(
        val operations: List<Execution.Node>,
        val dataLoaders: MutableMap<Field.DataLoader<*, *, *>, DataLoader<Any, *>>
) : List<Execution.Node> by operations
