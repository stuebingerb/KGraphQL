package de.stuebingerb.kgraphql.schema.model.ast

import de.stuebingerb.kgraphql.schema.model.ast.ValueNode.StringValueNode

data class InputValueDefinitionNode(
    override val loc: Location?,
    val name: NameNode,
    val description: StringValueNode?,
    val arguments: List<InputValueDefinitionNode>?,
    val type: TypeNode,
    val defaultValue: ValueNode?,
    val directives: List<DirectiveNode>?
) : ASTNode()
