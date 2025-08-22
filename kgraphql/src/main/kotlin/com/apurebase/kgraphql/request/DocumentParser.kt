package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.schema.model.ast.DocumentNode

internal class DocumentParser : RequestParser {
    override fun parseDocument(input: String): DocumentNode {
        return Parser(input).parseDocument()
    }
}
