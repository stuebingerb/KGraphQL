package com.apurebase.kgraphql.configuration

import com.apurebase.kgraphql.KGraphQL.Companion.schema
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.expect
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SchemaConfigurationTest {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    suspend fun `execution result should be the same with and without caching`(withCaching: Boolean) {
        val schema = schema {
            configure {
                useCachingDocumentParser = withCaching
            }
            query("hello") {
                resolver { -> "world" }
            }
        }

        schema.execute("{ hello }") shouldBe """
            {"data":{"hello":"world"}}
        """.trimIndent()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    suspend fun `execution result should use pretty printing if configured`(withPrettyPrinter: Boolean) {
        val schema = schema {
            configure {
                useDefaultPrettyPrinter = withPrettyPrinter
            }
            query("hello") {
                resolver { -> "world" }
            }
        }

        val expected = if (withPrettyPrinter) {
            """
            {
              "data" : {
                "hello" : "world"
              }
            }
            """.trimIndent()
        } else {
            """
            {"data":{"hello":"world"}}
            """.trimIndent()
        }

        schema.execute("{ hello }") shouldBe expected
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    suspend fun `introspections should be allowed depending on configuration`(introspectionAllowed: Boolean) {
        val schema = defaultSchema {
            configure {
                introspection = introspectionAllowed
            }
            query("hello") {
                resolver { -> "world" }
            }
        }

        if (introspectionAllowed) {
            schema.execute("{ __schema { queryType { name } } }") shouldBe """
                {"data":{"__schema":{"queryType":{"name":"Query"}}}}
            """.trimIndent()
        } else {
            expect<ValidationException>("GraphQL introspection is not allowed") {
                schema.execute("{ __schema { queryType { name } } }")
            }
        }
    }
}
