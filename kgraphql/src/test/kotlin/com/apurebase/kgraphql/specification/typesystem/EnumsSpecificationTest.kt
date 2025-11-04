package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.schema.SchemaException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@Specification("3.1.5 Enums")
class EnumsSpecificationTest {

    enum class Coolness {
        NOT_COOL, COOL, TOTALLY_COOL
    }

    val schema = KGraphQL.schema {
        enum<Coolness> {
            description = "State of coolness"
            value(Coolness.COOL) {
                description = "really cool"
            }
        }

        query("cool") {
            resolver { cool: Coolness -> cool.toString() }
        }
    }

    @Test
    fun `string literals must not be accepted as an enum input`() {
        expect<InvalidInputValueException>("String literal '\"COOL\"' is invalid value for enum type Coolness") {
            schema.executeBlocking("{cool(cool : \"COOL\")}")
        }
    }

    @Test
    fun `string constants are accepted as an enum input`() {
        val response = deserialize(schema.executeBlocking("{cool(cool : COOL)}"))
        response.extract<String>("data/cool") shouldBe "COOL"
    }

    enum class Empty

    @Test
    fun `enums must have at least one value`() {
        expect<SchemaException>("Enum 'Empty' must have at least one value") {
            KGraphQL.schema {
                enum<Empty>()
                query("test") {
                    resolver { -> "test" }
                }
            }
        }

        expect<SchemaException>("Enum 'EmptyCoolness' must have at least one value") {
            KGraphQL.schema {
                enum(Coolness::class, emptyArray()) {
                    name = "EmptyCoolness"
                }
                query("test") {
                    resolver { -> "test" }
                }
            }
        }
    }

    @Test
    fun `enums should allow to limit their values`() {
        val schema = KGraphQL.schema {
            enum(Coolness::class, arrayOf(Coolness.TOTALLY_COOL))
            query("test") {
                resolver { -> "test" }
            }
        }

        schema.printSchema() shouldBe """
            type Query {
              test: String!
            }
            
            enum Coolness {
              TOTALLY_COOL
            }
            
        """.trimIndent()
    }
}
