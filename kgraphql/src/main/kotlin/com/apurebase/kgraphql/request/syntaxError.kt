package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.InvalidSyntaxException
import com.apurebase.kgraphql.schema.model.ast.Source

internal fun syntaxError(
    source: Source,
    position: Int,
    description: String
) = InvalidSyntaxException(
    message = "Syntax Error: $description",
    source = source,
    positions = listOf(position)
)
