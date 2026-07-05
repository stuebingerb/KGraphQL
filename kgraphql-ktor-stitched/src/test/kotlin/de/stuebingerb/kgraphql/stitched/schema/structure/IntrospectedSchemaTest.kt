package de.stuebingerb.kgraphql.stitched.schema.structure

import de.stuebingerb.kgraphql.KGraphQL
import de.stuebingerb.kgraphql.request.Introspection
import de.stuebingerb.kgraphql.schema.SchemaPrinter
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.util.UUID

class IntrospectedSchemaTest {
    sealed class Union
    data class SubType1(val name: String) : Union()
    data class SubType2(val id: Int) : Union()

    interface Interface {
        val id: Int
    }

    interface AnotherInterface : Interface {
        val id2: Int
    }

    data class Implementation1(override val id: Int, val name: String) : Interface
    data class Implementation2(override val id: Int, override val id2: Int) : AnotherInterface

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
            unionType<Union>()
            type<Interface>()
            type<Implementation1>()
            type<Implementation2>()
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

    @Test
    fun `introspected schema should have proper possibleTypes`() {
        val schema = KGraphQL.schema {
            query("getUnion") {
                resolver { type: Int ->
                    if (type == 1) {
                        SubType1("st1")
                    } else {
                        SubType2(2)
                    }
                }.returns<Union>()
            }
            query("getInterface") {
                resolver { -> Implementation1(1, "impl1") }.returns<Interface>()
            }
            type<Implementation1>()
            type<Implementation2>()
            type<AnotherInterface>()
        }

        val schemaFromIntrospection = IntrospectedSchema.fromIntrospectionResponse(
            schema.executeBlocking(Introspection.query(Introspection.SpecLevel.WorkingDraft))
        )

        val unionType = schemaFromIntrospection.types.find { it.name == "Union" }
        unionType shouldNotBe null
        unionType?.possibleTypes?.map { it.name } shouldContainExactlyInAnyOrder listOf("SubType1", "SubType2")

        val interfaceType = schemaFromIntrospection.types.find { it.name == "Interface" }
        interfaceType shouldNotBe null
        interfaceType?.possibleTypes?.map { it.name } shouldContainExactlyInAnyOrder listOf(
            "Implementation1",
            "Implementation2"
        )

        val anotherInterfaceType = schemaFromIntrospection.types.find { it.name == "AnotherInterface" }
        anotherInterfaceType shouldNotBe null
        anotherInterfaceType?.possibleTypes?.map { it.name } shouldContainExactlyInAnyOrder listOf("Implementation2")
    }
}
