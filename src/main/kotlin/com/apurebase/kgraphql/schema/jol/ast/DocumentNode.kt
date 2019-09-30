package com.apurebase.kgraphql.schema.jol.ast

data class DocumentNode(
    val loc: Location?,
    val definitions: List<DefinitionNode>

)
