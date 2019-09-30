package com.apurebase.kgraphql.schema.jol.ast

data class NameNode(
    val value: String,
    override val loc: Location?
): ASTNode()
