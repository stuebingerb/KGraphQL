package de.stuebingerb.kgraphql.specification.typesystem

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.stuebingerb.kgraphql.InvalidInputValueException
import de.stuebingerb.kgraphql.KGraphQL
import de.stuebingerb.kgraphql.deserialize
import de.stuebingerb.kgraphql.expect
import de.stuebingerb.kgraphql.expectExecutionError
import de.stuebingerb.kgraphql.extract
import de.stuebingerb.kgraphql.schema.SchemaException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@Suppress("unused")
class InputObjectsSpecificationTest {

    enum class MockEnum { M1, M2 }

    data class InputOne(val enum: MockEnum, val id: String)

    data class InputTwo(val one: InputOne, val quantity: Int, val tokens: List<String>)

    data class Circular(val ref: Circular? = null, val value: String? = null)

    private val objectMapper = jacksonObjectMapper()

    val schema = KGraphQL.schema {
        inputType<InputTwo>()
        query("test") { resolver { input: InputTwo -> "success: $input" } }
    }

    @Test
    fun `an input object defines a set of input fields - scalars, enums, or other input objects`() {
        val two = object {
            val two = InputTwo(InputOne(MockEnum.M1, "M1"), 3434, listOf("23", "34", "21", "434"))
        }
        val variables = objectMapper.writeValueAsString(two)
        val response = deserialize(schema.executeBlocking("query(\$two: InputTwo!){test(input: \$two)}", variables))
        response.extract<String>("data/test") shouldBe "success: InputTwo(one=InputOne(enum=M1, id=M1), quantity=3434, tokens=[23, 34, 21, 434])"
    }

    @Test
    fun `input objects may contain nullable circular references`() {
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
        response.extract<String>("data/success") shouldBe "SUCCESS"
        response.extract<Any?>("data/null") shouldBe null
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

        expectExecutionError<InvalidInputValueException>("Property 'valu1' on 'MyInput' does not exist") {
            schema.executeBlocking(
                """
                {
                    main(input: { valu1: "Hello" })
                }
                """
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
            query("test") {
                resolver { input: NonDataClass -> input }
            }
        }

        val sdl = schema.printSchema()
        sdl shouldBe """
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
        response1 shouldBe """
            {"data":{"test":{"param2":8,"param3":null}}}
        """.trimIndent()

        val response2 = schema.executeBlocking(
            """
            query {
                test(input: {param3: true}) { param2 param3 }
            }
            """.trimIndent()
        )
        response2 shouldBe """
            {"data":{"test":{"param2":5,"param3":true}}}
        """.trimIndent()
    }

    @Test
    fun `input objects must have at least one field`() {
        // Non-data class with a constructor parameter that is not a property
        class ClassWithEmptyConstructor {
            val hello: String = "world"
        }

        expect<SchemaException>("Unable to handle 'query(\"test\")': An input type must define one or more fields. Found none on type 'ClassWithEmptyConstructorInput'") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: ClassWithEmptyConstructor -> input }
                }
            }
        }
        expect<SchemaException>("Unable to handle input type 'ClassWithEmptyConstructor': An input type must define one or more fields. Found none on type 'ClassWithEmptyConstructor'") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: String -> input }
                }
                inputType<ClassWithEmptyConstructor>()
            }
        }
    }

    interface InputInterface {
        val hello: String
    }

    sealed interface InputSealedInterface {
        val world: String
    }

    @Test
    fun `input objects must not be interfaces`() {
        expect<SchemaException>("Unable to handle 'query(\"test\")': Interface 'InputInterface' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputInterface -> input.hello }
                }
            }
        }

        expect<SchemaException>("Unable to handle 'query(\"test\")': Interface 'InputInterface' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputInterface -> input.hello }
                }
                type<InputInterface>()
            }
        }

        expect<SchemaException>("Unable to handle input type 'InputInterface': Interface 'InputInterface' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: String -> input }
                }
                inputType<InputInterface>()
            }
        }

        // Sealed interfaces should be reported as interfaces, not as sealed classes
        expect<SchemaException>("Unable to handle 'query(\"test\")': Interface 'InputSealedInterface' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputSealedInterface -> input.world }
                }
            }
        }

        expect<SchemaException>("Unable to handle 'query(\"test\")': Interface 'InputSealedInterface' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputSealedInterface -> input.world }
                }
                type<InputSealedInterface>()
            }
        }

        expect<SchemaException>("Unable to handle input type 'InputSealedInterface': Interface 'InputSealedInterface' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: String -> input }
                }
                inputType<InputSealedInterface>()
            }
        }
    }

    @Test
    fun `input objects must not contain interfaces`() {
        data class InputType(val hello: InputInterface)
        data class InputTypeSealed(val world: List<InputSealedInterface>)

        expect<SchemaException>("Unable to handle 'query(\"test\")': Interface 'InputInterface' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputType -> input.hello }
                }
            }
        }

        expect<SchemaException>("Unable to handle 'query(\"test\")': Interface 'InputInterface' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputType -> input.hello }
                }
                type<InputType>()
            }
        }

        expect<SchemaException>("Unable to handle input type 'InputType': Interface 'InputInterface' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: String -> input }
                }
                inputType<InputType>()
            }
        }

        // Sealed interfaces should be reported as interfaces, not as sealed classes
        expect<SchemaException>("Unable to handle 'query(\"test\")': Interface 'InputSealedInterface' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputTypeSealed -> input.world }
                }
            }
        }

        expect<SchemaException>("Unable to handle 'query(\"test\")': Interface 'InputSealedInterface' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputTypeSealed -> input.world }
                }
                type<InputTypeSealed>()
            }
        }

        expect<SchemaException>("Unable to handle input type 'InputTypeSealed': Interface 'InputSealedInterface' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: String -> input }
                }
                inputType<InputTypeSealed>()
            }
        }
    }

    sealed class InputSealed(val foo: String)
    data class InputSealedSub(val bar: String) : InputSealed("foo")

    @Test
    fun `input objects must not be sealed classes`() {
        expect<SchemaException>("Unable to handle 'query(\"test\")': Sealed class 'InputSealed' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputSealed? -> input?.foo }
                }
            }
        }

        expect<SchemaException>("Unable to handle 'query(\"test\")': Sealed class 'InputSealed' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputSealed? -> input?.foo }
                }
                unionType<InputSealed>()
            }
        }

        expect<SchemaException>("Unable to handle input type 'InputSealed': Sealed class 'InputSealed' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: String -> input }
                }
                inputType<InputSealed>()
            }
        }
    }

    @Test
    fun `input objects must not contain sealed classes`() {
        data class InputType(val sealed: InputSealed?)

        expect<SchemaException>("Unable to handle 'query(\"test\")': Sealed class 'InputSealed' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputType? -> input?.sealed?.foo }
                }
            }
        }

        expect<SchemaException>("Unable to handle input type 'InputType': Sealed class 'InputSealed' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: String -> input }
                }
                inputType<InputType>()
            }
        }
    }

    abstract class InputAbstract(val foo: String)

    @Test
    fun `input objects must not be abstract classes`() {
        expect<SchemaException>("Unable to handle 'query(\"test\")': Abstract class 'InputAbstract' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { inputs: List<InputAbstract> -> inputs.firstOrNull()?.foo }
                }
            }
        }

        expect<SchemaException>("Unable to handle 'query(\"test\")': Abstract class 'InputAbstract' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { inputs: List<InputAbstract> -> inputs.firstOrNull()?.foo }
                }
                type<InputAbstract>()
            }
        }

        expect<SchemaException>("Unable to handle input type 'InputAbstract': Abstract class 'InputAbstract' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: String -> input }
                }
                inputType<InputAbstract>()
            }
        }
    }

    @Test
    fun `input objects must not contain abstract classes`() {
        data class InputType(val abstracts: List<InputAbstract>)
        expect<SchemaException>("Unable to handle 'query(\"test\")': Abstract class 'InputAbstract' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { inputs: List<InputType> -> inputs.firstOrNull()?.abstracts }
                }
            }
        }

        expect<SchemaException>("Unable to handle 'query(\"test\")': Abstract class 'InputAbstract' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { inputs: List<InputType> -> inputs.firstOrNull()?.abstracts }
                }
                type<InputType>()
            }
        }

        expect<SchemaException>("Unable to handle input type 'InputType': Abstract class 'InputAbstract' is not allowed as input type") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: String -> input }
                }
                inputType<InputType>()
            }
        }
    }
}
