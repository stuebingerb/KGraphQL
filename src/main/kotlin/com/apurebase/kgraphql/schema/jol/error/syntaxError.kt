package com.apurebase.kgraphql.schema.jol.error

import com.apurebase.kgraphql.schema.jol.ast.Source

internal fun syntaxError(
    source: Source,
    position: Int,
    description: String
) = GraphQLError(
    message = "Syntax Error: $description",
    nodes = null,
    source = source,
    positions = listOf(position)
)
