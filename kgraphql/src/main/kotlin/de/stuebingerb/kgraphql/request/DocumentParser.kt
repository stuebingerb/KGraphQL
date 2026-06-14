package de.stuebingerb.kgraphql.request

import de.stuebingerb.kgraphql.schema.model.ast.DocumentNode

internal class DocumentParser : RequestParser {
    override fun parseDocument(input: String): DocumentNode {
        return Parser(input).parseDocument()
    }
}
