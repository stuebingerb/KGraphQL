package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
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
    suspend fun `Int input value`() {
        val input = 4356
        val response = deserialize(schema.execute("{ Int(value: $input) }"))
        response.extract<Int>("data/Int") shouldBe input
    }

    @ParameterizedTest
    @ValueSource(strings = ["42.0", "\"foo\"", "bar"])
    @Specification("2.9.1 Int Value")
    suspend fun `Invalid Int input value`(value: String) {
        val exception = shouldThrowExactly<InvalidInputValueException> {
            deserialize(schema.execute("{ Int(value: $value) }"))
        }
        exception shouldHaveMessage "Cannot coerce $value to numeric constant"
        exception.extensions shouldBe mapOf(
            "type" to "BAD_USER_INPUT"
        )
    }

    @Test
    @Specification("2.9.2 Float Value")
    suspend fun `Float input value`() {
        val input = 4356.34
        val response = deserialize(schema.execute("{ Float(value: $input) }"))
        response.extract<Double>("data/Float") shouldBe input
    }

    @Test
    @Specification("2.9.2 Float Value")
    suspend fun `Double input value`() {
        // GraphQL Float is Kotlin Double
        val input = 4356.34
        val response = deserialize(schema.execute("{ Double(value: $input) }"))
        response.extract<Double>("data/Double") shouldBe input
    }

    @Test
    @Specification("2.9.2 Float Value")
    suspend fun `Double with exponential input value`() {
        val input = 4356.34e2
        val response = deserialize(schema.execute("{ Double(value: $input) }"))
        response.extract<Double>("data/Double") shouldBe input
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
    suspend fun `Boolean input value`(input: String, expected: Boolean) {
        val response = deserialize(schema.execute("{ Boolean(value: $input) }"))
        response.extract<Boolean>("data/Boolean") shouldBe expected
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "42", "\"foo\"", "[\"foo\", \"bar\"]"])
    @Specification("2.9.3 Boolean Value")
    suspend fun `Invalid Boolean input value`(value: String) {
        val exception = shouldThrowExactly<InvalidInputValueException> {
            deserialize(schema.execute("{ Boolean(value: $value) }"))
        }
        exception shouldHaveMessage "argument '$value' is not valid value of type Boolean"
        exception.extensions shouldBe mapOf(
            "type" to "BAD_USER_INPUT"
        )
    }

    @Test
    @Specification("2.9.4 String Value")
    suspend fun `String input value`() {
        val input = "\\\\Ala ma kota \\n\\\\kot ma Alę"
        val expected = "\\Ala ma kota \n\\kot ma Alę"
        val response = deserialize(schema.execute("{ String(value: \"$input\") }"))
        response.extract<String>("data/String") shouldBe expected
    }

    @Test
    @Specification("2.9.4 String Value")
    suspend fun `String block input value`() {
        val input = "\\Ala ma kota \n\\kot ma Alę"
        val expected = "\\Ala ma kota \n\\kot ma Alę"
        val response = deserialize(schema.execute("{ String(value: \"\"\"$input\"\"\") }"))
        response.extract<String>("data/String") shouldBe expected
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "true", "42", "[\"foo\", \"bar\"]"])
    @Specification("2.9.4 String Value")
    suspend fun `Invalid String input value`(value: String) {
        val exception = shouldThrowExactly<InvalidInputValueException> {
            deserialize(schema.execute("{ String(value: $value) }"))
        }
        exception shouldHaveMessage "argument '$value' is not valid value of type String"
        exception.extensions shouldBe mapOf(
            "type" to "BAD_USER_INPUT"
        )
    }

    @Test
    @Specification("2.9.5 Null Value")
    suspend fun `Null input value`() {
        val response = deserialize(schema.execute("{ Null(value: null) }"))
        response.extract<Nothing?>("data/Null") shouldBe null
    }

    @Test
    @Specification("2.9.6 Enum Value")
    suspend fun `Enum input value`() {
        val response = deserialize(schema.execute("{ Enum(value: ENUM1) }"))
        response.extract<String>("data/Enum") shouldBe FakeEnum.ENUM1.toString()
    }

    @ParameterizedTest
    @ValueSource(strings = ["ENUM3"])
    @Specification("2.9.6 Enum Value")
    suspend fun `Invalid Enum input value`(value: String) {
        val exception = shouldThrowExactly<InvalidInputValueException> {
            deserialize(schema.execute("{ Enum(value: $value) }"))
        }
        exception shouldHaveMessage "Invalid enum ${FakeEnum::class.simpleName} value. Expected one of [ENUM1, ENUM2]"
        exception.extensions shouldBe mapOf(
            "type" to "BAD_USER_INPUT"
        )
    }

    @Test
    @Specification("2.9.7 List Value")
    suspend fun `List input value`() {
        val response = deserialize(schema.execute("{ List(value: [23, 3, 23]) }"))
        response.extract<List<Int>>("data/List") shouldBe listOf(23, 3, 23)
    }

    @ParameterizedTest
    @ValueSource(strings = ["true", "\"foo\""])
    @Specification("2.9.7 List Value")
    suspend fun `Invalid List input value`(value: String) {
        val exception = shouldThrowExactly<InvalidInputValueException> {
            deserialize(schema.execute("{ List(value: $value) }"))
        }
        exception shouldHaveMessage "Cannot coerce $value to numeric constant"
        exception.extensions shouldBe mapOf(
            "type" to "BAD_USER_INPUT"
        )
    }

    @Test
    @Specification("2.9.8 Object Value")
    suspend fun `Literal object input value`() {
        val response = deserialize(
            schema.execute("{ Object(value: { number: 232, description: \"little number\" }) }")
        )
        response.extract<Int>("data/Object") shouldBe 232
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "true", "42"])
    @Specification("2.9.8 Object Value")
    suspend fun `Invalid Literal object input value`(value: String) {
        val exception = shouldThrowExactly<InvalidInputValueException> {
            schema.execute("{ Object(value: { number: 232, description: \"little number\", list: $value }) }")
        }
        exception shouldHaveMessage "argument '$value' is not valid value of type String"
        exception.extensions shouldBe mapOf(
            "type" to "BAD_USER_INPUT"
        )
    }

    @Test
    @Specification("2.9.8 Object Value")
    suspend fun `Invalid Literal object input value - null`() {
        val exception = shouldThrowExactly<InvalidInputValueException> {
            schema.execute("{ Object(value: null) }")
        }
        exception shouldHaveMessage "argument 'null' is not valid value of type FakeData"
        exception.extensions shouldBe mapOf(
            "type" to "BAD_USER_INPUT"
        )
    }

    @Test
    @Specification("2.9.8 Object Value")
    suspend fun `Literal object input value with list field`() {
        val response = deserialize(
            schema.execute(
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
        response.extract<List<String>>("data/ObjectList") shouldBe listOf("number", "description", "little number")
    }

    @Test
    @Specification("2.9.8 Object Value")
    suspend fun `Object input value`() {
        val response = deserialize(
            schema.execute(
                request = "query(\$object: FakeData!) { Object(value: \$object) }",
                variables = "{ \"object\": { \"number\": 232, \"description\": \"little number\" } }"
            )
        )
        response.extract<Int>("data/Object") shouldBe 232
    }

    @Test
    @Specification("2.9.8 Object Value")
    suspend fun `Object input value with list field`() {
        val response = deserialize(
            schema.execute(
                request = "query(\$object: FakeData!){ ObjectList(value: \$object) }",
                variables = "{ \"object\": { \"number\": 232, \"description\": \"little number\", \"list\": [\"number\", \"description\", \"little number\"] } }"
            )
        )
        response.extract<List<String>>("data/ObjectList") shouldBe listOf("number", "description", "little number")
    }

    @Test
    @Specification("2.9.8 Object Value")
    suspend fun `Input object value mixed with variables`() {
        val response = schema.execute(
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

        response.extract<List<String>>("data/ObjectList") shouldBe listOf(
            "number",
            "Custom description",
            "little number"
        )
    }

    @Test
    @Specification("2.9.8 Object Value")
    suspend fun `Unknown object input value type`() {
        val exception = shouldThrowExactly<InvalidInputValueException> {
            schema.execute("query(\$object: FakeDate) { Object(value: \$object) }")
        }
        exception shouldHaveMessage "Invalid variable \$object argument type FakeDate, expected FakeData!"
        exception.extensions shouldBe mapOf(
            "type" to "BAD_USER_INPUT"
        )
    }

    data class Dessert(
        override val id: String,
        var name: String
    ) : Model

    interface Model {
        val id: String
    }

    // https://github.com/aPureBase/KGraphQL/issues/199
    @Test
    suspend fun `input object value with interface`() {
        val schema = KGraphQL.schema {
            inputType<Dessert> {
                name = "DessertInput"
                description = "Dessert object to be updated"
            }
            query("dessert") {
                resolver { -> Dessert("id-1", "name-1") }
            }
            mutation("updateDessert") {
                resolver { dessert: Dessert -> dessert }
            }
        }

        schema.execute("""
            query {
                dessert { id name }
            }
        """.trimIndent()) shouldBe """
            {"data":{"dessert":{"id":"id-1","name":"name-1"}}}
        """.trimIndent()

        schema.execute("""
            mutation {
                updateDessert(dessert: {id: "id-2", name: "name-2"}) { id name }
            }
        """.trimIndent()) shouldBe """
            {"data":{"updateDessert":{"id":"id-2","name":"name-2"}}}
        """.trimIndent()
    }
}
