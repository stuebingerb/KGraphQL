package de.stuebingerb.kgraphql.specification.typesystem

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.stuebingerb.kgraphql.FilmType
import de.stuebingerb.kgraphql.KGraphQL
import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.deserialize
import de.stuebingerb.kgraphql.expect
import de.stuebingerb.kgraphql.expectRequestError
import de.stuebingerb.kgraphql.extract
import de.stuebingerb.kgraphql.schema.SchemaException
import de.stuebingerb.kgraphql.schema.SchemaPrinter
import de.stuebingerb.kgraphql.schema.SchemaPrinterConfig
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
    fun `enum classes are not valid input types`() {
        expect<SchemaException>("Unable to handle input type 'FilmType': Cannot handle enum class 'de.stuebingerb.kgraphql.FilmType' as input type") {
            KGraphQL.schema { inputType<FilmType>() }
        }
    }

    @Test
    fun `functions are not valid input types`() {
        expect<SchemaException>("Unable to handle input type 'Function': Cannot handle function class 'kotlin.Function' as input type") {
            KGraphQL.schema { inputType<Function<*>>() }
        }
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

        expectRequestError<ValidationException>("Property 'valu1' on 'MyInput' does not exist") {
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

    data class InputSealedInterfaceImpl1(override val world: String) : InputSealedInterface
    data class InputSealedInterfaceImpl2(override val world: String, val again: String) : InputSealedInterface

    sealed interface InputSealedInterfaceNoSubclass {
        val world: String
    }

    sealed class SealedClassNoSubclass(val world: String)

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
    }

    sealed class InputSealed(val foo: String)
    data class InputSealedSub1(val bar: String) : InputSealed("bar")
    data class InputSealedSub2(val foobar: Int) : InputSealed("foobar")
    sealed class InputSealedSub3(val barfoo: Int) : InputSealed("barfoo")
    data class InputSealedSub31(val subFoo: String) : InputSealedSub3(13)
    data class InputSealedSub32(val subBar: Int) : InputSealedSub3(42)

    @Test
    fun `sealed classes should result in oneOf input objects`() {
        var schema = KGraphQL.schema {
            query("test") {
                resolver { input: InputSealed? -> input?.foo }
            }
        }
        SchemaPrinter().print(schema) shouldBe """
            type Query {
              test(input: InputSealedInput): String
            }
            
            input InputSealedInput @oneOf {
              inputsealedsub1: InputSealedSub1Input
              inputsealedsub2: InputSealedSub2Input
              inputsealedsub3: InputSealedSub3Input
            }
            
            input InputSealedSub1Input {
              bar: String!
            }
            
            input InputSealedSub2Input {
              foobar: Int!
            }

            input InputSealedSub31Input {
              subFoo: String!
            }
            
            input InputSealedSub32Input {
              subBar: Int!
            }
            
            input InputSealedSub3Input @oneOf {
              inputsealedsub31: InputSealedSub31Input
              inputsealedsub32: InputSealedSub32Input
            }
            
        """.trimIndent()

        schema = KGraphQL.schema {
            query("test") {
                resolver { input: InputSealed? -> input?.foo }
            }
            unionType<InputSealed>()
        }
        SchemaPrinter().print(schema) shouldBe """
            type InputSealedSub1 {
              bar: String!
              foo: String!
            }
            
            type InputSealedSub2 {
              foo: String!
              foobar: Int!
            }
            
            type InputSealedSub31 implements InputSealedSub3 {
              barfoo: Int!
              foo: String!
              subFoo: String!
            }
            
            type InputSealedSub32 implements InputSealedSub3 {
              barfoo: Int!
              foo: String!
              subBar: Int!
            }
            
            type Query {
              test(input: InputSealedInput): String
            }
            
            interface InputSealedSub3 {
              barfoo: Int!
              foo: String!
            }
            
            union InputSealed = InputSealedSub1 | InputSealedSub2 | InputSealedSub31 | InputSealedSub32
            
            input InputSealedInput @oneOf {
              inputsealedsub1: InputSealedSub1Input
              inputsealedsub2: InputSealedSub2Input
              inputsealedsub3: InputSealedSub3Input
            }
            
            input InputSealedSub1Input {
              bar: String!
            }
            
            input InputSealedSub2Input {
              foobar: Int!
            }
            
            input InputSealedSub31Input {
              subFoo: String!
            }
            
            input InputSealedSub32Input {
              subBar: Int!
            }
            
            input InputSealedSub3Input @oneOf {
              inputsealedsub31: InputSealedSub31Input
              inputsealedsub32: InputSealedSub32Input
            }

        """.trimIndent()

        schema = KGraphQL.schema {
            query("test") {
                resolver { input: String -> input }
            }
            inputType<InputSealed>()
        }
        SchemaPrinter().print(schema) shouldBe """
            type Query {
              test(input: String!): String!
            }
            
            input InputSealed @oneOf {
              inputsealedsub1: InputSealedSub1Input
              inputsealedsub2: InputSealedSub2Input
              inputsealedsub3: InputSealedSub3Input
            }
            
            input InputSealedSub1Input {
              bar: String!
            }
            
            input InputSealedSub2Input {
              foobar: Int!
            }

            input InputSealedSub31Input {
              subFoo: String!
            }
            
            input InputSealedSub32Input {
              subBar: Int!
            }
            
            input InputSealedSub3Input @oneOf {
              inputsealedsub31: InputSealedSub31Input
              inputsealedsub32: InputSealedSub32Input
            }

        """.trimIndent()

        // It should also be possible to configure the oneOf input type with custom names, descriptions, and deprecations
        schema = KGraphQL.schema {
            query("test") {
                resolver { input: InputSealed? -> input }
            }
            inputType<InputSealed> {
                name = "InputSealedInput"
                InputSealedSub1::class.configure {
                    ignore = true
                }
                InputSealedSub2::class.configure {
                    name = "otherSub"
                    description = "Some description"
                    deprecate("Deprecated for testing purposes")
                }
            }
            inputType<InputSealedSub3> {
                name = "InputSealedSub3Input"
                InputSealedSub31::class.configure {
                    name = "renamedInputSealedSub31"
                }
            }
        }
        SchemaPrinter(SchemaPrinterConfig(includeDescriptions = true)).print(schema) shouldBe """
            type InputSealedSub1 {
              bar: String!
              foo: String!
            }

            type InputSealedSub2 {
              foo: String!
              foobar: Int!
            }
            
            type InputSealedSub31 {
              barfoo: Int!
              foo: String!
              subFoo: String!
            }
            
            type InputSealedSub32 {
              barfoo: Int!
              foo: String!
              subBar: Int!
            }

            "Query object"
            type Query {
              test(input: InputSealedInput): InputSealed
            }

            union InputSealed = InputSealedSub1 | InputSealedSub2 | InputSealedSub31 | InputSealedSub32

            input InputSealedInput @oneOf {
              inputsealedsub3: InputSealedSub3Input
              "Some description"
              otherSub: InputSealedSub2Input @deprecated(reason: "Deprecated for testing purposes")
            }

            input InputSealedSub2Input {
              foobar: Int!
            }

            input InputSealedSub31Input {
              subFoo: String!
            }
            
            input InputSealedSub32Input {
              subBar: Int!
            }
            
            input InputSealedSub3Input @oneOf {
              inputsealedsub32: InputSealedSub32Input
              renamedInputSealedSub31: InputSealedSub31Input
            }
            
        """.trimIndent()
    }

    sealed interface SealedInterface {
        data class BBB(val i: Int) : SealedInterface
        sealed interface CCC : SealedInterface
        data class DDD(val d: Double) : CCC
        sealed interface EEE : CCC
        data class FFF(val s: String) : EEE
    }

    @Test
    fun `configuration of oneOf input objects must be on direct sub classes only`() {
        expect<SchemaException>("'InputSealedSub31' is not a direct subclass of 'InputSealed'; configure it via `inputType<InputSealedSub3>()`") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputSealed? -> input }
                }
                inputType<InputSealed> {
                    // InputSealedSub31 is not a direct subclass of InputSealed but transitive via InputSealedSub3
                    InputSealedSub31::class.configure {
                        name = "renamedInputSealedSub31"
                    }
                }
            }
        }
        expect<SchemaException>("'DDD' is not a direct subclass of 'SealedInterface'; configure it via `inputType<CCC>()`") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputSealed? -> input }
                }
                inputType<SealedInterface> {
                    // DDD is not a direct subclass of SealedInterface but transitive via CCC
                    SealedInterface.DDD::class.configure {
                        name = "renamedDDD"
                    }
                }
            }
        }
        expect<SchemaException>("'EEE' is not a direct subclass of 'SealedInterface'; configure it via `inputType<CCC>()`") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputSealed? -> input }
                }
                inputType<SealedInterface> {
                    SealedInterface.EEE::class.configure {
                        name = "renamedEEE"
                    }
                }
            }
        }
        expect<SchemaException>("'FFF' is not a direct subclass of 'SealedInterface'; configure it via `inputType<EEE>()`") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputSealed? -> input }
                }
                inputType<SealedInterface> {
                    SealedInterface.FFF::class.configure {
                        name = "renamedFFF"
                    }
                }
            }
        }
    }

    @Test
    fun `sealed classes inside input types should result in oneOf input objects`() {
        data class InputType(val sealed: InputSealed?)

        var schema = KGraphQL.schema {
            query("test") {
                resolver { input: InputType? -> input?.sealed?.foo }
            }
        }
        SchemaPrinter().print(schema) shouldBe """
            type Query {
              test(input: InputTypeInput): String
            }
            
            input InputSealedInput @oneOf {
              inputsealedsub1: InputSealedSub1Input
              inputsealedsub2: InputSealedSub2Input
              inputsealedsub3: InputSealedSub3Input
            }
            
            input InputSealedSub1Input {
              bar: String!
            }
            
            input InputSealedSub2Input {
              foobar: Int!
            }
            
            input InputSealedSub31Input {
              subFoo: String!
            }
            
            input InputSealedSub32Input {
              subBar: Int!
            }
            
            input InputSealedSub3Input @oneOf {
              inputsealedsub31: InputSealedSub31Input
              inputsealedsub32: InputSealedSub32Input
            }
            
            input InputTypeInput {
              sealed: InputSealedInput
            }

        """.trimIndent()
        schema.executeBlocking(
            """
            query {
                test(input: {sealed: {inputsealedsub1: {bar: "test"}}})
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"test":"bar"}}
        """.trimIndent()
        schema.executeBlocking(
            """
            query {
                test(input: {sealed: {inputsealedsub2: {foobar: 42}}})
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"test":"foobar"}}
        """.trimIndent()

        schema = KGraphQL.schema {
            query("test") {
                resolver { input: String -> input }
            }
            inputType<InputType>()
        }
        SchemaPrinter().print(schema) shouldBe """
            type Query {
              test(input: String!): String!
            }
            
            input InputSealedInput @oneOf {
              inputsealedsub1: InputSealedSub1Input
              inputsealedsub2: InputSealedSub2Input
              inputsealedsub3: InputSealedSub3Input
            }
            
            input InputSealedSub1Input {
              bar: String!
            }
            
            input InputSealedSub2Input {
              foobar: Int!
            }
            
            input InputSealedSub31Input {
              subFoo: String!
            }
            
            input InputSealedSub32Input {
              subBar: Int!
            }
            
            input InputSealedSub3Input @oneOf {
              inputsealedsub31: InputSealedSub31Input
              inputsealedsub32: InputSealedSub32Input
            }
            
            input InputType {
              sealed: InputSealedInput
            }

        """.trimIndent()
    }

    @Test
    fun `sealed interfaces should result in oneOf input objects`() {
        val schema = KGraphQL.schema {
            query("test") {
                resolver { input: InputSealedInterface -> input.world }
            }
        }
        SchemaPrinter().print(schema) shouldBe """
            type Query {
              test(input: InputSealedInterfaceInput!): String!
            }
            
            input InputSealedInterfaceImpl1Input {
              world: String!
            }
            
            input InputSealedInterfaceImpl2Input {
              again: String!
              world: String!
            }
            
            input InputSealedInterfaceInput @oneOf {
              inputsealedinterfaceimpl1: InputSealedInterfaceImpl1Input
              inputsealedinterfaceimpl2: InputSealedInterfaceImpl2Input
            }

        """.trimIndent()
    }

    @Test
    fun `sealed interfaces and classes without subclass should result in proper error message`() {
        expect<SchemaException>("Unable to handle 'query(\"test\")': The sealed interface 'InputSealedInterfaceNoSubclass' does not have known subclasses") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputSealedInterfaceNoSubclass -> input.world }
                }
            }
        }

        expect<SchemaException>("Unable to handle 'query(\"test\")': The sealed class 'SealedClassNoSubclass' does not have known subclasses") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: SealedClassNoSubclass -> input.world }
                }
            }
        }

        data class MyInput(val foo: String, val sealed: InputSealedInterfaceNoSubclass)

        expect<SchemaException>("Unable to handle 'query(\"test\")': The sealed interface 'InputSealedInterfaceNoSubclass' does not have known subclasses") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: MyInput -> input.sealed.world }
                }
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

    @Test
    fun `oneOf input objects must only have nullable fields`() {
        class OneOfInputWithRequiredFields(val a: String, val b: Int?, val c: List<String>)

        expect<SchemaException>("Unable to handle input type 'OneOfInputWithRequiredFields': OneOf input types must only have nullable fields. Fields [a, c] are non-nullable on type 'OneOfInputWithRequiredFields'") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: OneOfInputWithRequiredFields -> input }
                }
                inputType<OneOfInputWithRequiredFields> {
                    isOneOf = true
                }
            }
        }
    }

    @Test
    fun `oneOf input objects out of sealed classes must have unique field names`() {
        expect<SchemaException>("Unable to handle input type 'InvalidInputSealed': Cannot add field with duplicated name 'inputSealedSub1'") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputSealed? -> input }
                }
                inputType<InputSealed> {
                    name = "InvalidInputSealed"
                    // Both subtypes have the same name, which results in duplicate field names
                    InputSealedSub1::class.configure {
                        name = "inputSealedSub1"
                    }
                    InputSealedSub2::class.configure {
                        name = "inputSealedSub1"
                    }
                }
            }
        }
    }

    @Test
    fun `oneOf input objects out of sealed classes must have valid field names`() {
        expect<SchemaException>("Unable to handle input type 'InvalidInputSealed': Illegal name '42'. Names must start with a letter or underscore, and may only contain [_a-zA-Z0-9]") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputSealed? -> input }
                }
                inputType<InputSealed> {
                    name = "InvalidInputSealed"
                    InputSealedSub1::class.configure {
                        name = "42"
                    }
                }
            }
        }
    }

    @Test
    fun `oneOf input objects out of sealed classes must result in valid input types`() {
        expect<SchemaException>("Unable to handle input type 'InvalidInputSealed': An input type must define one or more fields. Found none on type 'InvalidInputSealed'") {
            KGraphQL.schema {
                query("test") {
                    resolver { input: InputSealed? -> input }
                }
                inputType<InputSealed> {
                    name = "InvalidInputSealed"
                    // All subtypes are ignored, which results in an empty oneOf input type
                    InputSealedSub1::class.configure {
                        ignore = true
                    }
                    InputSealedSub2::class.configure {
                        ignore = true
                    }
                    InputSealedSub3::class.configure {
                        ignore = true
                    }
                }
            }
        }
    }
}
