package com.apurebase.kgraphql.schema.jol

import com.apurebase.kgraphql.schema.jol.ast.TypeNode
import com.apurebase.kgraphql.schema.jol.ast.TypeNode.NonNullTypeNode
import com.apurebase.kgraphql.schema.jol.ast.TypeNode.ListTypeNode
import com.apurebase.kgraphql.schema.jol.ast.TypeNode.NamedTypeNode
//
//internal fun typeFromAST(schema: DataSchema, typeNode: TypeNode): TypeNode? = when (typeNode) {
//    is ListTypeNode -> typeFromAST(schema, typeNode.type)?.let {
//        ListTypeNode(null, it)
//    }
//    is NonNullTypeNode -> typeFromAST(schema, typeNode.type)?.let {
//        NonNullTypeNode(null, it)
//    }
//    is NamedTypeNode -> schema.findyByName(typeNode.name.value)
//}
