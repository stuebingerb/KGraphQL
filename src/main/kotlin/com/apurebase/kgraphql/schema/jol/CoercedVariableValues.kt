package com.apurebase.kgraphql.schema.jol

import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.jol.ast.TypeNode
import com.apurebase.kgraphql.schema.jol.ast.TypeNode.NonNullTypeNode
import com.apurebase.kgraphql.schema.jol.ast.VariableDefinitionNode
import com.apurebase.kgraphql.schema.jol.error.GraphQLError

data class CoercedVariableValues(
    val errors: List<GraphQLError>? = null,
    val coerced: Map<String, Any>? = null
) {

    data class CoercedVariableValuesOptions(val maxErrors: Int? = null)

//    companion object {
//        fun getVariableValues(
//            schema: DataSchema,
//            varDefNodes: List<VariableDefinitionNode>,
//            options: CoercedVariableValuesOptions? = null
//        ): CoercedVariableValues {
//            val errors = mutableListOf<GraphQLError>()
//
//            try {
//                val  coerced = coerceVariableValues(schema, varDefNodes) {
//                    if (options?.maxErrors != null && errors.size >= options.maxErrors) {
//                        throw GraphQLError("Too many errors processing variables, error limit reached. Execution aborted.")
//                    }
//                    errors.add(it)
//                }
//                if (errors.isEmpty()) {
//                    return CoercedVariableValues(coerced = coerced)
//                }
//            } catch (e: GraphQLError) {
//                errors.add(e)
//            }
//
//            return CoercedVariableValues(errors = errors)
//        }
//
//        fun coerceVariableValues(
//            schema: DataSchema,
//            varDefNodes: List<VariableDefinitionNode>,
//            inputs: Map<String, Any>,
//            onError: (GraphQLError) -> Unit
//        ): Map<String, Any> {
//            val coercedValues = mutableMapOf<String, Any>()
//
//            for (varDefNode in varDefNodes) {
//                val varName = varDefNode.variable.name.value
//                var varType = typeFromAST(schema, varDefNode.type)
//
////                throw TODO("We shall validate that varType is not an inputType!")
//
//
//                if (inputs.containsKey(varName)) {
//                    if (varDefNode.defaultValue != null) {
//                        coercedValues[varName] = valueFromAST(varDefNode.defaultValue, varType)
//                    } else if (varType is NonNullTypeNode) {
//                        onError(GraphQLError(
//                            message = "Variable \"$varName\" of required type \"NAME_NOT_FOUND\" was not provided.",
//                            nodes = listOf(varDefNode)
//                        ))
//                    }
//                    continue
//                }
//
//                val value = inputs[varName]
//                if (value == null && varType is NonNullTypeNode) {
//                    onError(GraphQLError(
//                        message = "Variable \"$$varName\" of non-null type \"NAME_NOT_FOUND\" must not be null.",
//                        nodes = listOf(varDefNode)
//                    ))
//                    continue
//                }
//
//                coercedValues[varName] = coerceInputValue(
//
//                )
//            }
//
//            return coercedValues
//        }
//    }
}
