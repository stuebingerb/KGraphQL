package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.with
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class InputObjectsSpecificationTest {

    enum class MockEnum { M1, M2 }

    data class InputOne(val enum: MockEnum, val id: String)

    data class InputTwo(val one: InputOne, val quantity: Int, val tokens: List<String>)

    data class Circular(val ref: Circular? = null, val value: String? = null)

    private val objectMapper = jacksonObjectMapper()

    val schema = KGraphQL.schema {
        enum<MockEnum>()
        inputType<InputTwo>()
        query("test") { resolver { input: InputTwo -> "success: $input" } }
    }

    @Test
    fun `An Input Object defines a set of input fields - scalars, enums, or other input objects`() {
        val two = object {
            val two = InputTwo(InputOne(MockEnum.M1, "M1"), 3434, listOf("23", "34", "21", "434"))
        }
        val variables = objectMapper.writeValueAsString(two)
        val response = deserialize(schema.executeBlocking("query(\$two: InputTwo!){test(input: \$two)}", variables))
        assertThat(response.extract<String>("data/test"), startsWith("success"))
    }

    @Test
    fun `Input objects may contain nullable circular references`() {
        val schema = KGraphQL.schema {
            inputType<Circular>()
            query("circular") {
                resolver { cir: Circular -> cir.ref?.value }
            }
        }

        val variables = object {
            val cirNull = Circular(Circular(null))
            val cirSuccess = Circular(Circular(null, "SUCCESS"))
        }
        val response = deserialize(
            schema.executeBlocking(
                "query(\$cirNull: Circular!, \$cirSuccess: Circular!){" +
                    "null: circular(cir: \$cirNull)" +
                    "success: circular(cir: \$cirSuccess)}",
                objectMapper.writeValueAsString(variables)
            )
        )
        assertThat(response.extract("data/success"), equalTo("SUCCESS"))
        assertThat(response.extract("data/null"), nullValue())
    }

    // https://github.com/aPureBase/KGraphQL/issues/93
    @Test
    fun `incorrect input parameter should throw an appropriate exception`() {
        data class MyInput(val value1: String)

        val schema = KGraphQL.schema {
            query("main") {
                resolver { input: MyInput -> input.value1 }
            }
        }

        invoking {
            schema.executeBlocking(
                """
                {
                    main(input: { valu1: "Hello" })
                }
                """
            )
        } shouldThrow GraphQLError::class with {
            message shouldBeEqualTo "Property 'valu1' on 'MyInput' does not exist"
            extensions shouldBeEqualTo mapOf(
                "type" to "BAD_USER_INPUT"
            )
        }
    }

    // Non-data class with a constructor parameter that is not a property
    class NonDataClass(param1: String = "Hello", val param3: Boolean?) {
        var param2: Int = param1.length
    }

    @Test
    fun `input objects should take fields from primary constructor`() {
        val schema = KGraphQL.schema {
            inputType<NonDataClass> {
                name = "NonDataClassInput"
            }
            query("test") {
                resolver { input: NonDataClass -> input }
            }
        }

        val sdl = schema.printSchema()
        sdl shouldBeEqualTo """
            type NonDataClass {
              param2: Int!
              param3: Boolean
            }

            type Query {
              test(input: NonDataClassInput!): NonDataClass!
            }

            input NonDataClassInput {
              param1: String!
              param3: Boolean
            }

        """.trimIndent()

        val response1 = schema.executeBlocking(
            """
            query {
                test(input: {param1: "myParam1"}) { param2 param3 }
            }
            """.trimIndent()
        )
        response1 shouldBeEqualTo """
            {"data":{"test":{"param2":8,"param3":null}}}
        """.trimIndent()

        val response2 = schema.executeBlocking(
            """
            query {
                test(input: {param3: true}) { param2 param3 }
            }
            """.trimIndent()
        )
        response2 shouldBeEqualTo """
            {"data":{"test":{"param2":5,"param3":true}}}
        """.trimIndent()
    }
}
