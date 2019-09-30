package com.apurebase.kgraphql.schema.jol.ast

import com.apurebase.kgraphql.schema.jol.ast.ValueNode.StringValueNode

data class InputValueDefinitionNode(
    override val loc: Location?,
    val name: NameNode,
    val description: StringValueNode?,
    val type: TypeNode,
    val defaultValue: ValueNode?,
    val directives: List<DirectiveNode>?
): ASTNode()
