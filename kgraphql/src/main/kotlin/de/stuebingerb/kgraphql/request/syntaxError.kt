package de.stuebingerb.kgraphql.request

import de.stuebingerb.kgraphql.InvalidSyntaxException
import de.stuebingerb.kgraphql.schema.model.ast.Source

internal fun syntaxError(
    source: Source,
    position: Int,
    description: String
) = InvalidSyntaxException(
    message = "Syntax Error: $description",
    source = source,
    positions = listOf(position)
)
