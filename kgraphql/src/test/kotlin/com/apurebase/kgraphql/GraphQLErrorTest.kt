package com.apurebase.kgraphql

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

class GraphQLErrorTest {

    @Test
    fun `graphql error should default to INTERNAL_SERVER_ERROR type`() {
        val graphqlError = GraphQLError(
            message = "test"
        )

        val expectedJson = buildJsonObject {
            put("errors", buildJsonArray {
                addJsonObject {
                    put("message", "test")
                    put("locations", buildJsonArray {})
                    put("path", buildJsonArray {})
                    put("extensions", buildJsonObject {
                        put("type", "INTERNAL_SERVER_ERROR")
                    })
                }
            })
        }.toString()

        graphqlError.serialize() shouldBe expectedJson
    }

    @Test
    fun `test graphql error with custom extensions`() {
        val graphqlError = GraphQLError(
            message = "test",
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
                    put("locations", buildJsonArray {})
                    put("path", buildJsonArray {})
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
