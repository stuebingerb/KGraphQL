package com.apurebase.kgraphql.schema.model.ast

data class VariableDefinitionNode(
    override val loc: Location?,
    val variable: ValueNode.VariableNode,
    val type: TypeNode,
    val defaultValue: ValueNode?,
    val directives: List<DirectiveNode>?
) : ASTNode()
