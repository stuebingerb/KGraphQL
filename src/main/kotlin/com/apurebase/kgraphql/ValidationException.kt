package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.jol.ast.ASTNode
import com.apurebase.kgraphql.schema.jol.error.GraphQLError

class ValidationException(message: String, nodes: List<ASTNode>? = null): GraphQLError(message, nodes = nodes)
