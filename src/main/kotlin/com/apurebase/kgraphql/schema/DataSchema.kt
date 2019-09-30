package com.apurebase.kgraphql.schema

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.configuration.SchemaConfiguration
import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.introspection.__Type
import com.apurebase.kgraphql.schema.jol.Parser
import com.apurebase.kgraphql.schema.jol.ast.*
import com.apurebase.kgraphql.schema.jol.ast.DefinitionNode.*
import com.apurebase.kgraphql.schema.jol.ast.DefinitionNode.ExecutableDefinitionNode.FragmentDefinitionNode
import com.apurebase.kgraphql.schema.jol.ast.DefinitionNode.ExecutableDefinitionNode.OperationDefinitionNode
import com.apurebase.kgraphql.schema.jol.ast.OperationTypeNode.*
import com.apurebase.kgraphql.schema.structure2.Field
import com.apurebase.kgraphql.schema.structure2.RequestInterpreter
import com.apurebase.kgraphql.schema.structure2.SchemaModel
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlin.coroutines.CoroutineContext

//class DataSchema (
//    val model: SchemaModel,
//): Schema, __Schema by model, DefaultSchema {
//
//    override val coroutineContext: CoroutineContext = Job()
//
//    private val requestInterpreter : RequestInterpreter = RequestInterpreter(model)
//
//
//    override suspend fun execute(request: String, variables: String?, context: Context): String {
//        // 1. parse request
//        val document = Parser(request).parseDocument()
//
//        val parsedVariables = variables
//            ?.let { VariablesJson.Defined(configuration.objectMapper, variables) }
//            ?: VariablesJson.Empty()
//
//
//        // 2. TODO: Assert valid arguments provided
//
//        // 3. Build ExecutionContext
//        // Field
//        val exContext = ExecutionContext.buildExecutionContext(this, document)
//
//        // 4. Execute operations
//
//        val root = JsonNodeFactory.instance.objectNode()
//        val data = root.putObject("data")
//
//        document
//            .definitions
//            .asFlow()
//            .map { defNode(it) }
//            .collect { (key: String, value: JsonNode) ->
//                data.set(key, value)
//            }
//
//        return jacksonObjectMapper()
//            .writer()
//            .withDefaultPrettyPrinter()
//            .writeValueAsString(root)
//    }
//}

data class Tester(val id: Int, val value: String)
data class TestType(val id: Int, val name: String)

fun main() = runBlocking {

    val schema = KGraphQL.schema {

        configure {
            useDefaultPrettyPrinter = true
        }

        query("test") {
            resolver { -> Tester(1, "Hello World") }
        }

        type<Tester> {
            property<TestType>("typeOld") {
                resolver {
                    TestType(1, "Hello World")
                }
            }

            dataProperty<Int, TestType>("type") {
                setReturnType { TestType(1, "") }
                loader { keys ->
                    keys.map { it to TestType(1, "Hello World") }.toMap()
                }
                prepare { it.id }
            }
        }
    } as DefaultSchema

    schema.execute("""
        {
            test {
                value
                typeOld {
                    id
                    name
                }
            }
        }
    """.trimIndent()).let(::println)

}
