package com.apurebase.kgraphql.schema.jol.utils

import com.apurebase.kgraphql.schema.jol.ast.TypeNode
import com.apurebase.kgraphql.schema.jol.ast.TypeNode.NonNullTypeNode
import com.apurebase.kgraphql.schema.jol.ast.ValueNode
import com.apurebase.kgraphql.schema.jol.ast.ValueNode.NullValueNode
import com.apurebase.kgraphql.schema.jol.ast.ValueNode.VariableNode

//internal fun valueFromAST(
//    valueNode: ValueNode?,
//    type: TypeNode,
//    variables: Map<String, Any>? = mapOf()
//): Any? {
//    // When there is no node, then there is also no value.
//    // Importantly, this is different from returning the value null
//    if (valueNode == null) return Nothing
//
//    if (type is NonNullTypeNode) {
//        if (valueNode is NullValueNode) {
//            return Nothing // Invalid: intentionally return no value.
//        }
//        return valueFromAST(valueNode, type.type, variables)
//    }
//
//    if (valueNode is NullValueNode) {
//        // This is explicitly returning the value null.
//        return null
//    }
//
//    if (valueNode is VariableNode) {
//        val variableName = valueNode.name.value
//        if (variables == null) {
//            // No valid return value.
//            return Nothing
//        }
//        val variableValue = variables[variableName]
//        if (variableValue == null && type is NonNullTypeNode) {
//            return Nothing // Invalid: intentionally return no value.
//        }
//        // Note: This does no further checking that his variable is correct.
//        // This assumes that this query has been validated and the variable
//        // usage here is of the correct type.
//        return variableValue
//    }
//}
