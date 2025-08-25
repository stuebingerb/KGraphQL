package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.schema.model.ast.DocumentNode

internal interface RequestParser {
    fun parseDocument(input: String): DocumentNode
}
