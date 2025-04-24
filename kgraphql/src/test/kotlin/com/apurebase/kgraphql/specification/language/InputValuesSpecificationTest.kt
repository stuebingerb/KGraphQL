package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.with
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@Specification("2.9 Input Values")
class InputValuesSpecificationTest {

    @Suppress("unused")
    enum class FakeEnum {
        ENUM1, ENUM2
    }

    data class FakeData(val number: Int = 0, val description: String = "", val list: List<String> = emptyList())

    val schema = defaultSchema {
        enum<FakeEnum>()
        inputType<FakeData>()

        query("Int") { resolver { value: Int -> value } }
        query("Float") { resolver { value: Float -> value } }
        query("Double") { resolver { value: Double -> value } }
        query("String") { resolver { value: String -> value } }
        query("Boolean") { resolver { value: Boolean -> value } }
        query("Null") { resolver { value: Int? -> value } }
        query("Enum") { resolver { value: FakeEnum -> value } }
        query("List") { resolver { value: List<Int> -> value } }
        query("Object") { resolver { value: FakeData -> value.number } }
        query("ObjectList") { resolver { value: FakeData -> value.list } }
    }

    @Test
    @Specification("2.9.1 Int Value")
    fun `Int input value`() {
        val input = 4356
        val response = deserialize(schema.executeBlocking("{ Int(value: $input) }"))
        assertThat(response.extract<Int>("data/Int"), equalTo(input))
    }

    @ParameterizedTest
    @ValueSource(strings = ["42.0", "\"foo\"", "bar"])
    @Specification("2.9.1 Int Value")
    fun `Invalid Int input value`(value: String) {
        invoking {
            deserialize(schema.executeBlocking("{ Int(value: $value) }"))
        } shouldThrow GraphQLError::class with {
            message shouldBeEqualTo "Cannot coerce $value to numeric constant"
            extensions shouldBeEqualTo mapOf(
                "type" to "BAD_USER_INPUT"
            )
        }
    }

    @Test
    @Specification("2.9.2 Float Value")
    fun `Float input value`() {
        val input = 4356.34
        val response = deserialize(schema.executeBlocking("{ Float(value: $input) }"))
        assertThat(response.extract<Double>("data/Float"), equalTo(input))
    }

    @Test
    @Specification("2.9.2 Float Value")
    fun `Double input value`() {
        // GraphQL Float is Kotlin Double
        val input = 4356.34
        val response = deserialize(schema.executeBlocking("{ Double(value: $input) }"))
        assertThat(response.extract<Double>("data/Double"), equalTo(input))
    }

    @Test
    @Specification("2.9.2 Float Value")
    fun `Double with exponential input value`() {
        val input = 4356.34e2
        val response = deserialize(schema.executeBlocking("{ Double(value: $input) }"))
        assertThat(response.extract<Double>("data/Double"), equalTo(input))
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "true, true",
            "false, false",
            "\"true\", true",
            "\"false\", false",
            "\"TRUE\", true",
            "\"FALSE\", false",
            "\"tRuE\", true",
            "\"faLSe\", false",
            "1, true",
            "0, false",
            "-1, false"
        ]
    )
    @Specification("2.9.3 Boolean Value")
    fun `Boolean input value`(input: String, expected: Boolean) {
        val response = deserialize(schema.executeBlocking("{ Boolean(value: $input) }"))
        assertThat(response.extract<Boolean>("data/Boolean"), equalTo(expected))
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "42", "\"foo\"", "[\"foo\", \"bar\"]"])
    @Specification("2.9.3 Boolean Value")
    fun `Invalid Boolean input value`(value: String) {
        invoking {
            deserialize(schema.executeBlocking("{ Boolean(value: $value) }"))
        } shouldThrow GraphQLError::class with {
            message shouldBeEqualTo "argument '$value' is not valid value of type Boolean"
            extensions shouldBeEqualTo mapOf(
                "type" to "BAD_USER_INPUT"
            )
        }
    }

    @Test
    @Specification("2.9.4 String Value")
    fun `String input value`() {
        val input = "\\\\Ala ma kota \\n\\\\kot ma Alę"
        val expected = "\\Ala ma kota \n\\kot ma Alę"
        val response = deserialize(schema.executeBlocking("{ String(value: \"$input\") }"))
        assertThat(response.extract<String>("data/String"), equalTo(expected))
    }

    @Test
    @Specification("2.9.4 String Value")
    fun `String block input value`() {
        val input = "\\Ala ma kota \n\\kot ma Alę"
        val expected = "\\Ala ma kota \n\\kot ma Alę"
        val response = deserialize(schema.executeBlocking("{ String(value: \"\"\"$input\"\"\") }"))
        assertThat(response.extract<String>("data/String"), equalTo(expected))
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "true", "42", "[\"foo\", \"bar\"]"])
    @Specification("2.9.4 String Value")
    fun `Invalid String input value`(value: String) {
        invoking {
            deserialize(schema.executeBlocking("{ String(value: $value) }"))
        } shouldThrow GraphQLError::class with {
            message shouldBeEqualTo "argument '$value' is not valid value of type String"
            extensions shouldBeEqualTo mapOf(
                "type" to "BAD_USER_INPUT"
            )
        }
    }

    @Test
    @Specification("2.9.5 Null Value")
    fun `Null input value`() {
        val response = deserialize(schema.executeBlocking("{ Null(value: null) }"))
        assertThat(response.extract<Nothing?>("data/Null"), equalTo(null))
    }

    @Test
    @Specification("2.9.6 Enum Value")
    fun `Enum input value`() {
        val response = deserialize(schema.executeBlocking("{ Enum(value: ENUM1) }"))
        assertThat(response.extract<String>("data/Enum"), equalTo(FakeEnum.ENUM1.toString()))
    }

    @ParameterizedTest
    @ValueSource(strings = ["ENUM3"])
    @Specification("2.9.6 Enum Value")
    fun `Invalid Enum input value`(value: String) {
        invoking {
            deserialize(schema.executeBlocking("{ Enum(value: $value) }"))
        } shouldThrow GraphQLError::class with {
            message shouldBeEqualTo "Invalid enum ${FakeEnum::class.simpleName} value. Expected one of [ENUM1, ENUM2]"
            extensions shouldBeEqualTo mapOf(
                "type" to "BAD_USER_INPUT"
            )
        }
    }

    @Test
    @Specification("2.9.7 List Value")
    fun `List input value`() {
        val response = deserialize(schema.executeBlocking("{ List(value: [23, 3, 23]) }"))
        assertThat(response.extract<List<Int>>("data/List"), equalTo(listOf(23, 3, 23)))
    }

    @ParameterizedTest
    @ValueSource(strings = ["true", "\"foo\""])
    @Specification("2.9.7 List Value")
    fun `Invalid List input value`(value: String) {
        invoking {
            deserialize(schema.executeBlocking("{ List(value: $value) }"))
        } shouldThrow GraphQLError::class with {
            message shouldBeEqualTo "Cannot coerce $value to numeric constant"
            extensions shouldBeEqualTo mapOf(
                "type" to "BAD_USER_INPUT"
            )
        }
    }

    @Test
    @Specification("2.9.8 Object Value")
    fun `Literal object input value`() {
        val response = deserialize(
            schema.executeBlocking("{ Object(value: { number: 232, description: \"little number\" }) }")
        )
        assertThat(response.extract<Int>("data/Object"), equalTo(232))
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "true", "42"])
    @Specification("2.9.8 Object Value")
    fun `Invalid Literal object input value`(value: String) {
        invoking {
            schema.executeBlocking("{ Object(value: { number: 232, description: \"little number\", list: $value }) }")
        } shouldThrow GraphQLError::class with {
            message shouldBeEqualTo "argument '$value' is not valid value of type String"
            extensions shouldBeEqualTo mapOf(
                "type" to "BAD_USER_INPUT"
            )
        }
    }

    @Test
    @Specification("2.9.8 Object Value")
    fun `Invalid Literal object input value - null`() {
        invoking {
            schema.executeBlocking("{ Object(value: null) }")
        } shouldThrow GraphQLError::class with {
            message shouldBeEqualTo "argument 'null' is not valid value of type FakeData"
            extensions shouldBeEqualTo mapOf(
                "type" to "BAD_USER_INPUT"
            )
        }
    }

    @Test
    @Specification("2.9.8 Object Value")
    fun `Literal object input value with list field`() {
        val response = deserialize(
            schema.executeBlocking(
                """
                {
                    ObjectList(
                        value: {
                            number: 232,
                            description: "little number",
                            list: ["number", "description", "little number"]
                        }
                    )
                }
                """.trimIndent()
            )
        )
        assertThat(
            response.extract<List<String>>("data/ObjectList"),
            equalTo(listOf("number", "description", "little number"))
        )
    }

    @Test
    @Specification("2.9.8 Object Value")
    fun `Object input value`() {
        val response = deserialize(
            schema.executeBlocking(
                request = "query(\$object: FakeData!) { Object(value: \$object) }",
                variables = "{ \"object\": { \"number\": 232, \"description\": \"little number\" } }"
            )
        )
        assertThat(response.extract<Int>("data/Object"), equalTo(232))
    }

    @Test
    @Specification("2.9.8 Object Value")
    fun `Object input value with list field`() {
        val response = deserialize(
            schema.executeBlocking(
                request = "query(\$object: FakeData!){ ObjectList(value: \$object) }",
                variables = "{ \"object\": { \"number\": 232, \"description\": \"little number\", \"list\": [\"number\", \"description\", \"little number\"] } }"
            )
        )
        assertThat(
            response.extract<List<String>>("data/ObjectList"),
            equalTo(listOf("number", "description", "little number"))
        )
    }

    @Test
    @Specification("2.9.8 Object Value")
    fun `Input object value mixed with variables`() {
        val response = schema.executeBlocking(
            """
            query ObjectVariablesMixed(${'$'}description: String!, ${'$'}number: Int! = 25) {
                ObjectList(value: {
                    number: ${'$'}number,
                    description: ${'$'}description,
                    list: ["number", ${'$'}description, "little number"]
                })
            }
        """.trimIndent(), """{ "description": "Custom description" }"""
        ).deserialize()

        assertThat(
            response.extract<List<String>>("data/ObjectList"),
            equalTo(listOf("number", "Custom description", "little number"))
        )
    }

    @Test
    @Specification("2.9.8 Object Value")
    fun `Unknown object input value type`() {
        invoking {
            schema.executeBlocking("query(\$object: FakeDate) { Object(value: \$object) }")
        } shouldThrow GraphQLError::class with {
            message shouldBeEqualTo "Invalid variable \$object argument type FakeDate, expected FakeData!"
            extensions shouldBeEqualTo mapOf(
                "type" to "BAD_USER_INPUT"
            )
        }
    }
}
