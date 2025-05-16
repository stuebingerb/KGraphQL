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
    fun `test graphql error with custom error type`() {
        val graphqlError = GraphQLError(
            message = "test",
            extensionsErrorType = "AUTHORIZATION_ERROR"
        )

        val expectedJson = buildJsonObject {
            put("errors", buildJsonArray {
                addJsonObject {
                    put("message", "test")
                    put("locations", buildJsonArray {})
                    put("path", buildJsonArray {})
                    put("extensions", buildJsonObject {
                        put("type", "AUTHORIZATION_ERROR")
                    })
                }
            })
        }.toString()

        graphqlError.serialize() shouldBe expectedJson
    }

    @Test
    fun `test graphql error with custom error type and detail`() {
        val graphqlError = GraphQLError(
            message = "test",
            extensionsErrorType = "VALIDATION_ERROR",
            extensionsErrorDetail = mapOf<String, Any?>(
                "singleCheck" to mapOf("email" to "not an email", "age" to "Limited to 150"),
                "multiCheck" to "The 'from' number must not exceed the 'to' number"
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

    @Test
    fun `test graphql error with custom extensions, type and detail`() {
        val graphqlError = GraphQLError(
            message = "test",
            extensions = mapOf(
                "type" to "VALIDATION_ERROR",
                "listProperty" to listOf("value1", "value2", 3),
                "detail" to mapOf<String, Any?>(
                    "singleCheck" to mapOf("email" to "not an email", "age" to "Limited to 150"),
                    "multiCheck" to "The 'from' number must not exceed the 'to' number"
                )
            ),
            extensionsErrorType = "this is overwritten by extensions[type]",
            extensionsErrorDetail = mapOf<String, Any?>(
                "ignoredCheck" to "this is overwritten by extensions[detail]"
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
                        put("detail", buildJsonObject {
                            put("singleCheck", buildJsonObject {
                                put("email", "not an email")
                                put("age", "Limited to 150")
                            })
                            put("multiCheck", "The 'from' number must not exceed the 'to' number")
                        })
                        put("listProperty", buildJsonArray {
                            add("value1")
                            add("value2")
                            add(3)
                        })
                    })
                }
            })
        }.toString()

        graphqlError.serialize() shouldBe expectedJson
    }
}
