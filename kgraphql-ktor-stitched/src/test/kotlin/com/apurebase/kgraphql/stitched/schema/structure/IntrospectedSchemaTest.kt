package com.apurebase.kgraphql.stitched.schema.structure

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.request.Introspection
import com.apurebase.kgraphql.schema.SchemaPrinter
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class IntrospectedSchemaTest {
    data class TestObject(val name: String)

    @Suppress("unused")
    enum class TestEnum {
        TYPE1, TYPE2
    }

    @Test
    fun `introspected schema should result in the same SDL as the schema itself`() {
        val schema = KGraphQL.schema {
            extendedScalars()

            query("getObject") {
                resolver { -> TestObject("dummy") }
            }
            query("getEnum") {
                resolver { -> TestEnum.TYPE1 }
            }
            mutation("add") {
                resolver { input: TestObject -> input }
            }
            enum<TestEnum>()
            stringScalar<UUID> {
                deserialize = UUID::fromString
                serialize = UUID::toString
                specifiedByURL = "https://tools.ietf.org/html/rfc4122"
            }
        }

        val schemaFromIntrospection = IntrospectedSchema.fromIntrospectionResponse(
            schema.executeBlocking(Introspection.query(Introspection.SpecLevel.WorkingDraft))
        )

        SchemaPrinter().print(schemaFromIntrospection) shouldBe SchemaPrinter().print(schema)
    }

    @Test
    fun `introspection of remote schema should not fail when there are additional entries`() {
        // A minimal introspection response with an extensions node (supported by the spec), and another (unsupported
        // by the spec) one. Both must not make schema introspection fail.
        val introspectionResponse = """
            {
                "data": {
                    "__schema": {
                        "queryType": {
                            "name": "Query"
                        },
                        "mutationType": null,
                        "subscriptionType": null,
                        "types": [],
                        "directives": []
                    }
                },
                "extensions": {
                    "cost": {
                        "requestedQueryCost": 1,
                        "actualQueryCost": 1
                    }
                },
                "unsupported_extensions": {
                    "version": "1.0.0"
                }
            }
        """.trimIndent()

        // The introspected schema won't be very useful but at least it must not throw any exception
        shouldNotThrowAny {
            IntrospectedSchema.fromIntrospectionResponse(response = introspectionResponse)
        }
    }
}
