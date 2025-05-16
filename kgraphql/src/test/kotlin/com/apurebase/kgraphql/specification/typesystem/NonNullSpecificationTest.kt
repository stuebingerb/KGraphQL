package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.schema.execution.Executor
import com.apurebase.kgraphql.shouldBeInstanceOf
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@Specification("3.1.8 Non-null")
class NonNullSpecificationTest {

    @Test
    fun `if the result of non-null type is null, error should be raised`() {
        val schema = KGraphQL.schema {
            query("nonNull") {
                resolver { string: String? -> string!! }
            }
        }
        val exception = shouldThrowExactly<ExecutionException> {
            schema.executeBlocking("{nonNull}")
        }
        exception.originalError shouldBeInstanceOf java.lang.NullPointerException::class
    }

    @Test
    fun `nullable input types are always optional`() {
        val schema = KGraphQL.schema {
            query("nullable") {
                resolver { input: String? -> input }
            }
        }

        val responseOmittedInput = deserialize(schema.executeBlocking("{nullable}"))
        responseOmittedInput.extract<Any?>("data/nullable") shouldBe null

        val responseNullInput = deserialize(schema.executeBlocking("{nullable(input: null)}"))
        responseNullInput.extract<Any?>("data/nullable") shouldBe null
    }

    @Test
    fun `non-null types are always required`() {
        val schema = KGraphQL.schema {
            query("nonNull") {
                resolver { input: String -> input }
            }
        }
        expect<ValidationException>("Missing value for non-nullable argument input on the field 'nonNull'") {
            schema.executeBlocking("{nonNull}")
        }
    }

    @Test
    fun `variable of a nullable type cannot be provided to a non-null argument`() {
        val schema = KGraphQL.schema {
            query("nonNull") {
                resolver { input: String -> input }
            }
        }

        expect<InvalidInputValueException>("Invalid variable ${'$'}arg argument type String, expected String!\n") {
            schema.executeBlocking("query(\$arg: String){nonNull(input: \$arg)}", "{\"arg\":\"SAD\"}")
        }
    }

    data class Type1(val value: String)
    data class Type2(val items: List<Type1?>)

    @Test
    fun `null within arrays should work`() {
        val schema = KGraphQL.schema {
            configure {
                executor = Executor.DataLoaderPrepared
            }
            query("data") {
                resolver { ->
                    Type2(
                        items = listOf(
                            Type1("Stuff"),
                            null
                        )
                    )
                }
            }
        }

        schema.executeBlocking(
            """
            {
                data {
                    items {
                        value
                    }
                }
            }
            """.trimIndent()
        ).deserialize().run {
            extract<String>("data/data/items[0]/value") shouldBe "Stuff"
            extract<String?>("data/data/items[1]") shouldBe null
        }
    }

    data class MyInput(val value1: String, val value2: String?, val value3: Int)

    @Test
    fun `missing nullable values without Kotlin default values should execute successfully and use null`() {
        val schema = KGraphQL.schema {
            query("main") {
                resolver { input: MyInput -> "${input.value1} - ${input.value2 ?: "Nada"} - ${input.value3}" }
            }
        }

        schema.executeBlocking(
            """
            {
                main(input: { value1: "Hello", value3: 42 })
            }
            """.trimIndent()
        ).deserialize().run {
            extract<String>("data/main") shouldBe "Hello - Nada - 42"
        }
    }

    @Test
    fun `missing non-nullable values without Kotlin default values should raise an error`() {
        val schema = KGraphQL.schema {
            inputType<MyInput> {
                property(MyInput::value1) {
                    name = "valueOne"
                }
            }
            query("main") {
                resolver { input: MyInput -> "${input.value1} - ${input.value2 ?: "Nada"} - ${input.value3}" }
            }
        }

        expect<InvalidInputValueException>("Missing non-optional input fields: valueOne, value3") {
            schema.executeBlocking(
                """
                {
                    main(input: { value2: "World" })
                }
                """.trimIndent()
            )
        }
    }

    data class MyOptionalInput(val value1: String = "Hello", val value2: String? = "World")

    @Test
    fun `missing nullable values with Kotlin default values should execute successfully and use Kotlin defaults`() {
        val schema = KGraphQL.schema {
            query("main") {
                resolver { input: MyOptionalInput -> "${input.value1} - ${input.value2 ?: "Nada"}" }
            }
        }

        schema.executeBlocking(
            """
            {
                main(input: { value1: "Hello" })
            }
            """.trimIndent()
        ).deserialize().run {
            extract<String>("data/main") shouldBe "Hello - World"
        }
    }

    @Test
    fun `missing non-nullable values with Kotlin default values should execute successfully and use Kotlin defaults`() {
        val schema = KGraphQL.schema {
            query("main") {
                resolver { input: MyOptionalInput -> "${input.value1} - ${input.value2 ?: "Nada"}" }
            }
        }

        println(schema.printSchema())

        schema.executeBlocking(
            """
            {
                main(input: { value2: "World, again" })
            }
            """.trimIndent()
        ).deserialize().run {
            extract<String>("data/main") shouldBe "Hello - World, again"
        }
    }
}
