package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.builtin.BuiltInScalars
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.model.ast.NameNode
import com.apurebase.kgraphql.schema.model.ast.SelectionNode
import com.apurebase.kgraphql.schema.structure.Field
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

class GraphQLErrorTest {

    private val dummyNode = Execution.Node(
        selectionNode = SelectionNode.FieldNode(
            parent = null,
            alias = null,
            name = NameNode(value = "dummyNode", loc = null),
            arguments = null,
            directives = null
        ),
        field = Field.Delegated(
            name = "field",
            description = null,
            isDeprecated = false,
            deprecationReason = null,
            args = emptyList(),
            returnType = BuiltInScalars.STRING.typeDef.toScalarType(),
            argsFromParent = emptyMap()
        ),
        children = emptyList(),
        arguments = null,
        directives = null,
        variables = null,
        arrayIndex = null,
        parent = null
    )

    @Test
    fun `execution error should default to INTERNAL_SERVER_ERROR type`() {
        val graphqlError = ExecutionError(
            message = "test",
            node = dummyNode
        )

        val expectedJson = buildJsonObject {
            put("errors", buildJsonArray {
                addJsonObject {
                    put("message", "test")
                    put("path", buildJsonArray {
                        add("dummyNode")
                    })
                    put("extensions", buildJsonObject {
                        put("type", "INTERNAL_SERVER_ERROR")
                    })
                }
            })
        }.toString()

        graphqlError.serialize() shouldBe expectedJson
    }

    @Test
    fun `request error should default to BAD_USER_INPUT type and not have a path key`() {
        val graphqlError = RequestError(
            message = "test"
        )

        val expectedJson = buildJsonObject {
            put("errors", buildJsonArray {
                addJsonObject {
                    put("message", "test")
                    put("extensions", buildJsonObject {
                        put("type", "BAD_USER_INPUT")
                    })
                }
            })
        }.toString()

        graphqlError.serialize() shouldBe expectedJson
    }

    @Test
    fun `test graphql error with custom extensions`() {
        val graphqlError = ExecutionError(
            message = "test",
            node = dummyNode,
            extensions = mapOf(
                "type" to "VALIDATION_ERROR",
                "listProperty" to listOf("value1", "value2", 3),
                "detail" to mapOf<String, Any?>(
                    "singleCheck" to mapOf("email" to "not an email", "age" to "Limited to 150"),
                    "multiCheck" to "The 'from' number must not exceed the 'to' number"
                )
            )
        )

        val expectedJson = buildJsonObject {
            put("errors", buildJsonArray {
                addJsonObject {
                    put("message", "test")
                    put("path", buildJsonArray {
                        add("dummyNode")
                    })
                    put("extensions", buildJsonObject {
                        put("type", "VALIDATION_ERROR")
                        put("listProperty", buildJsonArray {
                            add("value1")
                            add("value2")
                            add(3)
                        })
                        put("detail", buildJsonObject {
                            put("singleCheck", buildJsonObject {
                                put("email", "not an email")
                                put("age", "Limited to 150")
                            })
                            put("multiCheck", "The 'from' number must not exceed the 'to' number")
                        })
                    })
                }
            })
        }.toString()

        graphqlError.serialize() shouldBe expectedJson
    }
}
