package com.apurebase.kgraphql.schema.jol

import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.jol.ast.DefinitionNode
import com.apurebase.kgraphql.schema.jol.ast.DefinitionNode.*
import com.apurebase.kgraphql.schema.jol.ast.DefinitionNode.ExecutableDefinitionNode.*
import com.apurebase.kgraphql.schema.jol.ast.DocumentNode
import com.apurebase.kgraphql.schema.jol.error.GraphQLError

/**
 * Data that must be available at all points during query execution.
 *
 * Namely, schema of the type system that is currently executing,
 * and the fragments defined in the query document
 */
//data class ExecutionContext(
////    schema: GraphQLSchema,
//    val fragments: Map<String, FragmentDefinitionNode>,
////    rootValue: mixed,
////    contextValue: mixed,
//    val operation: OperationDefinitionNode,
////    variableValues: { [variable: string]: mixed, ... },
////fieldResolver: GraphQLFieldResolver<any, any>,
////typeResolver: GraphQLTypeResolver<any, any>,
//    val errors: List<GraphQLError>
//) {
//    companion object {
//        fun buildExecutionContext(
//            schema: DataSchema,
//            document: DocumentNode,
//            operationName: String? = null
//        ): ExecutionContext {
//            val fragments = mutableMapOf<String, FragmentDefinitionNode>()
//            var operation: OperationDefinitionNode? = null
//
//            loop@ for (definition in document.definitions) {
//                when (definition) {
//                    is OperationDefinitionNode -> {
//                        if (operationName == null) {
//                            if (operation != null) {
//                                throw GraphQLError("Must provide operation name if query contains multiple operations.")
//                            }
//                            operation = definition
//                        } else if (definition.name?.value == operationName) {
//                            operation = definition
//                        }
//                        break@loop
//                    }
//                    is FragmentDefinitionNode -> {
//                        fragments[definition.name!!.value] = definition
//                        break@loop
//                    }
//                }
//            }
//
//            if (operation == null) {
//                val errMsg = if (operationName == null) "Must provide an operation." else "Unknown operation named \"$operationName\"."
//                throw GraphQLError(errMsg)
//            }
//
//            val variables = CoercedVariableValues()
//
//            if (variables.errors != null) {
//                throw TODO("Not supported")
//            }
//
//            return ExecutionContext(
//                fragments = fragments,
//                operation = operation,
//                errors = listOf()
//            )
//        }
//    }
//}
