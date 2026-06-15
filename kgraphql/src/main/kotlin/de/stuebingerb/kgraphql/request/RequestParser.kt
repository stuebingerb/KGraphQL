package de.stuebingerb.kgraphql.request

import de.stuebingerb.kgraphql.schema.model.ast.DocumentNode

internal interface RequestParser {
    fun parseDocument(input: String): DocumentNode
}
