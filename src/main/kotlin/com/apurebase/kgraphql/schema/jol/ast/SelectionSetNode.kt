package com.apurebase.kgraphql.schema.jol.ast

data class SelectionSetNode(
    override val loc: Location?,
    val selections: List<SelectionNode>
): ASTNode()
