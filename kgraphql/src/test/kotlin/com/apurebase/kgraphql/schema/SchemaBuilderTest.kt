package com.apurebase.kgraphql.schema

import com.apurebase.kgraphql.Account
import com.apurebase.kgraphql.Actor
import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.FilmType
import com.apurebase.kgraphql.Id
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Scenario
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.context
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.apurebase.kgraphql.schema.dsl.types.TypeDSL
import com.apurebase.kgraphql.schema.execution.DefaultGenericTypeResolver
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.structure.Field
import com.apurebase.kgraphql.shouldBeInstanceOf
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import kotlin.reflect.KType
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.typeOf

/**
 * Tests for SchemaBuilder behaviour, not request execution
 */
class SchemaBuilderTest {

    @Test
    fun `ignored property DSL`() {
        val testedSchema = defaultSchema {
            query("scenario") {
                resolver { -> Scenario(Id("GKalus", 234234), "Gamil Kalus", "TOO LONG") }
            }
            type<Scenario> {
                Scenario::author.ignore()
                Scenario::content.configure {
                    description = "Content is Content"
                    isDeprecated = false
                }
            }
        }

        val scenarioType = testedSchema.model.queryTypes[Scenario::class]
            ?: throw Exception("Scenario type should be present in schema")
        scenarioType["author"] shouldBe null
        scenarioType["content"] shouldNotBe null
    }

    @Test
    fun `ignored invisible properties`() {
        val testedSchema = defaultSchema {
            query("account") {
                resolver { -> Account(42, "AzureDiamond", "hunter2") }
            }
        }

        val scenarioType = testedSchema.model.queryTypes[Account::class]
            ?: throw Exception("Account type should be present in schema")

        // id should exist because it is public
        scenarioType["id"] shouldNotBe null

        // username should exist because it is public
        scenarioType["username"] shouldNotBe null

        // password shouldn't exist because it's private
        scenarioType["password"] shouldBe null
    }

    @Test
    fun `transformation DSL`() {
        val testedSchema = defaultSchema {
            query("scenario") {
                resolver { -> Scenario(Id("GKalus", 234234), "Gamil Kalus", "TOO LONG") }
            }
            type<Scenario> {
                transformation(Scenario::content) { content: String, capitalized: Boolean? ->
                    if (capitalized == true) content.replaceFirstChar { it.uppercase() } else content
                }
            }
        }
        val scenarioType = testedSchema.model.queryTypes[Scenario::class]
            ?: throw Exception("Scenario type should be present in schema")
        scenarioType.kind shouldBe TypeKind.OBJECT
        scenarioType["content"] shouldNotBe null
    }

    @Test
    fun `extension property DSL`() {
        val testedSchema = defaultSchema {

            query("scenario") {
                resolver { -> Scenario(Id("GKalus", 234234), "Gamil Kalus", "TOO LONG") }
            }

            type<Scenario> {
                property("pdf") {
                    description = "link to pdf representation of scenario"
                    resolver { scenario: Scenario -> "http://scenarios/${scenario.id}" }
                }
            }
        }

        val scenarioType = testedSchema.model.queryTypes[Scenario::class]
            ?: throw Exception("Scenario type should be present in schema")

        scenarioType.kind shouldBe TypeKind.OBJECT
        scenarioType["pdf"] shouldNotBe null
    }

    @Test
    fun `union type DSL`() {
        val tested = defaultSchema {

            query("scenario") {
                resolver { -> Scenario(Id("GKalus", 234234), "Gamil Kalus", "TOO LONG") }
            }

            val linked = unionType("Linked") {
                type<Actor>()
                type<Scenario>()
            }

            type<Scenario> {
                unionProperty("pdf") {
                    returnType = linked
                    description = "link to pdf representation of scenario"
                    resolver { scenario: Scenario ->
                        if (scenario.author.startsWith("Gamil")) {
                            Scenario(Id("ADD", 22), "gambino", "nope")
                        } else {
                            Actor("Chance", 333)
                        }
                    }
                }
            }
        }

        val scenarioType = tested.model.queryTypes[Scenario::class]
            ?: throw Exception("Scenario type should be present in schema")

        val unionField = scenarioType["pdf"]
        unionField shouldNotBe null
        unionField shouldBeInstanceOf Field.Union::class
    }

    @Test
    fun `circular dependency extension property`() {
        val tested = defaultSchema {
            query("actor") {
                resolver { -> Actor("Little John", 44) }
            }

            type<Actor> {
                property("linked") {
                    resolver { _ -> Actor("BIG John", 3234) }
                }
            }
        }

        val actorType = tested.model.queryTypes[Actor::class]
            ?: throw Exception("Actor type should be present in schema")
        actorType.kind shouldBe TypeKind.OBJECT
        val property = actorType["linked"] ?: throw Exception("Actor should have ext property 'linked'")
        property shouldNotBe null
        property.returnType.unwrapped().name shouldBe "Actor"
    }

    @Test
    fun `KFunction resolver`() {
        val actorService = object {
            fun getMainActor() = Actor("Little John", 44)
            fun getActor(id: Int) = when (id) {
                1 -> Actor("Joey", 4)
                else -> Actor("Bobby", 5)
            }
        }

        val tested = defaultSchema {
            query("mainActor") {
                actorService::getMainActor.toResolver()
            }

            query("actorById") {
                actorService::getActor.toResolver()
            }

            type<Actor> {
                property("linked") {
                    resolver { _ -> Actor("BIG John", 3234) }
                }
            }
        }

        val actorType = tested.model.queryTypes[Actor::class]
            ?: throw Exception("Actor type should be present in schema")
        actorType.kind shouldBe TypeKind.OBJECT
        val property = actorType["linked"] ?: throw Exception("Actor should have ext property 'linked'")
        property shouldNotBe null
        property.returnType.unwrapped().name shouldBe "Actor"

        deserialize(tested.executeBlocking("{mainActor{name}}"))
        deserialize(tested.executeBlocking("{actorById(id: 1){name}}"))
    }

    @Test
    fun ` _ should be allowed as receiver argument name`() {
        val schema = defaultSchema {
            query("actor") {
                resolver { -> Actor("Boguś Linda", 4343) }
            }

            type<Actor> {
                property("favDishes") {
                    resolver { _: Actor, size: Int ->
                        listOf("steak", "burger", "soup", "salad", "bread", "bird").take(size)
                    }
                }
            }
        }

        schema.executeBlocking("{actor{favDishes(size: 2)}}").also(::println).deserialize()
    }

    @Test
    fun `Custom type name`() {
        val schema = defaultSchema {
            query("actor") {
                resolver { type: FilmType -> Actor("Boguś Linda $type", 4343) }
            }

            enum<FilmType> {
                name = "TYPE"
            }
        }

        val result =
            deserialize(schema.executeBlocking("query(\$type : TYPE = FULL_LENGTH){actor(type: \$type){name}}"))
        result.extract<String>("data/actor/name") shouldBe "Boguś Linda FULL_LENGTH"
    }

    @Test
    fun `java arrays should be supported`() {
        KGraphQL.schema {
            query("actors") {
                resolver { ->
                    arrayOf(
                        Actor("Actor1", 1),
                        Actor("Actor2", 2)
                    )
                }
            }
        }.executeBlocking("{actors { name } }").let(::println)
    }

    class InputOne(val string: String)

    class InputTwo(val one: InputOne)

    @Test
    fun `Schema should map input types`() {
        val schema = defaultSchema {
            query("createInput") {
                resolver { input: InputTwo -> input.one }
            }
            inputType<InputTwo>()
        }

        schema.inputTypeByKClass(InputOne::class) shouldNotBe null
        schema.inputTypeByKClass(InputTwo::class) shouldNotBe null
    }

    @Test
    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    fun `Schema should infer input types from resolver functions`() {
        val schema = defaultSchema {
            query("sample") {
                resolver { i: InputTwo -> "SUCCESS" }
            }
        }

        schema.inputTypeByKClass(InputOne::class) shouldNotBe null
        schema.inputTypeByKClass(InputTwo::class) shouldNotBe null
    }

    private sealed class Maybe<out T> {
        abstract fun get(): T

        data object Undefined : Maybe<Nothing>() {
            override fun get() = throw IllegalArgumentException("Requested value is not defined!")
        }

        class Defined<U>(val value: U) : Maybe<U>() {
            override fun get() = value
        }
    }

    @Test
    fun `client code can declare custom generic type resolver`() {
        val typeResolver = object : DefaultGenericTypeResolver() {
            override fun unbox(obj: Any) = if (obj is Maybe<*>) obj.get() else super.unbox(obj)
            override fun resolveMonad(type: KType): KType {
                if (typeOf<Maybe<*>>().isSupertypeOf(type)) {
                    return type.arguments.first().type
                        ?: throw SchemaException("Could not get the type of the first argument for the type $type")
                }
                return super.resolveMonad(type)
            }
        }

        data class SomeWithGenericType(val value: Maybe<Int>, val anotherValue: String = "foo")

        val schema = defaultSchema {
            configure { genericTypeResolver = typeResolver }

            type<SomeWithGenericType>()
            query("definedValueProp") { resolver<SomeWithGenericType> { SomeWithGenericType(Maybe.Defined(33)) } }
            query("undefinedValueProp") { resolver<SomeWithGenericType> { SomeWithGenericType(Maybe.Undefined) } }

            query("definedValue") { resolver<Maybe<String>> { Maybe.Defined("good!") } }
            query("undefinedValue") { resolver<Maybe<String>> { Maybe.Undefined } }
        }

        deserialize(schema.executeBlocking("{__schema{queryType{fields{ type { ofType { kind name fields { type {ofType {kind name}}}}}}}}}")).let {
            it.extract<String>("data/__schema/queryType/fields[0]/type/ofType/kind") shouldBe "OBJECT"
            it.extract<String>("data/__schema/queryType/fields[0]/type/ofType/name") shouldBe "SomeWithGenericType"
            it.extract<String>("data/__schema/queryType/fields[0]/type/ofType/fields[0]/type/ofType/kind") shouldBe "SCALAR"
            it.extract<String>("data/__schema/queryType/fields[0]/type/ofType/fields[0]/type/ofType/name") shouldBe "String"

            it.extract<String>("data/__schema/queryType/fields[1]/type/ofType/kind") shouldBe "OBJECT"
            it.extract<String>("data/__schema/queryType/fields[1]/type/ofType/name") shouldBe "SomeWithGenericType"
            it.extract<String>("data/__schema/queryType/fields[1]/type/ofType/fields[0]/type/ofType/kind") shouldBe "SCALAR"
            it.extract<String>("data/__schema/queryType/fields[1]/type/ofType/fields[0]/type/ofType/name") shouldBe "String"

            it.extract<String>("data/__schema/queryType/fields[2]/type/ofType/kind") shouldBe "SCALAR"
            it.extract<String>("data/__schema/queryType/fields[2]/type/ofType/name") shouldBe "String"

            it.extract<String>("data/__schema/queryType/fields[3]/type/ofType/kind") shouldBe "SCALAR"
            it.extract<String>("data/__schema/queryType/fields[3]/type/ofType/name") shouldBe "String"
        }

        deserialize(schema.executeBlocking("{definedValueProp {value}}")).extract<Int>("data/definedValueProp/value") shouldBe 33
        deserialize(schema.executeBlocking("{undefinedValueProp {anotherValue}}")).let {
            it.extract<String>("data/undefinedValueProp/anotherValue") shouldBe "foo"
        }
        deserialize(schema.executeBlocking("{definedValue}")).let {
            it.extract<String>("data/definedValue") shouldBe "good!"
        }
        expect<IllegalArgumentException>("Requested value is not defined!") {
            deserialize(schema.executeBlocking("{undefinedValue}"))
        }
        expect<IllegalArgumentException>("Requested value is not defined!") {
            deserialize(schema.executeBlocking("{undefinedValueProp {value}}"))
        }
    }

    data class LambdaWrapper(val lambda: () -> Int)

    @Test
    fun `function properties can be handled by providing generic type resolver`() {
        val typeResolver = object : DefaultGenericTypeResolver() {
            override fun unbox(obj: Any) = if (obj is Function0<*>) obj() else super.unbox(obj)
            override fun resolveMonad(type: KType): KType {
                if (typeOf<Function0<*>>().isSupertypeOf(type)) {
                    return type.arguments.first().type
                        ?: throw SchemaException("Could not get the type of the first argument for the type $type")
                }
                return super.resolveMonad(type)
            }
        }

        val schema = defaultSchema {
            configure { genericTypeResolver = typeResolver }

            type<LambdaWrapper>()

            query("lambda") {
                resolver { -> LambdaWrapper { 1 } }
            }
        }

        deserialize(schema.executeBlocking("{__schema{queryType{fields{ type { ofType { kind name fields { type {ofType {kind name}}}}}}}}}")).let {
            it.extract<String>("data/__schema/queryType/fields[0]/type/ofType/kind") shouldBe "OBJECT"
            it.extract<String>("data/__schema/queryType/fields[0]/type/ofType/name") shouldBe "LambdaWrapper"
            it.extract<String>("data/__schema/queryType/fields[0]/type/ofType/fields[0]/type/ofType/kind") shouldBe "SCALAR"
            it.extract<String>("data/__schema/queryType/fields[0]/type/ofType/fields[0]/type/ofType/name") shouldBe "Int"
        }

        deserialize(schema.executeBlocking("{lambda {lambda}}")).extract<Int>("data/lambda/lambda") shouldBe 1
    }

    @Test
    fun `input value default value and description can be specified`() {
        val expectedDescription = "Int Argument"
        val expectedDefaultValue = 33
        val schema = defaultSchema {
            query("data") {
                resolver { int: Int -> int }.withArgs {
                    arg<Int> { name = "int"; defaultValue = expectedDefaultValue; description = expectedDescription }
                }
            }
        }

        val intArg = schema.queryType.fields?.find { it.name == "data" }?.args?.find { it.name == "int" }
        intArg?.defaultValue shouldBe expectedDefaultValue.toString()
        intArg?.description shouldBe expectedDescription

        val response = deserialize(schema.executeBlocking("{data}"))
        response.extract<Int>("data/data") shouldBe 33

        val introspection =
            deserialize(schema.executeBlocking("{__schema{queryType{fields{name, args{name, description, defaultValue}}}}}"))
        introspection.extract<String>("data/__schema/queryType/fields[0]/args[0]/description") shouldBe expectedDescription
    }

    @Test
    fun `introspections query should be disabled`() {
        val expectedDescription = "Int Argument"
        val expectedDefaultValue = 33
        val schema = defaultSchema {
            configure {
                introspection = false
            }

            query("data") {
                resolver { int: Int -> int }.withArgs {
                    arg<Int> { name = "int"; defaultValue = expectedDefaultValue; description = expectedDescription }
                }
            }
        }

        expect<ValidationException>("GraphQL introspection is not allowed") {
            schema.executeBlocking("{__schema{queryType{fields{name, args{name, description, defaultValue}}}}}")
        }
    }

    @Test
    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    fun `arg name must match exactly one of type property`() {
        expect<SchemaException>("Invalid input values on data: [intss]") {
            defaultSchema {
                query("data") {
                    resolver { int: Int, string: String? -> int }.withArgs {
                        arg<Int> { name = "intss"; defaultValue = 33 }
                    }
                }
            }
        }
    }

    @Test
    fun `arg name must be defined`() {
        expect<UninitializedPropertyAccessException>("lateinit property name has not been initialized") {
            defaultSchema {
                query("data") {
                    resolver { int: Int, _: String? -> int }.withArgs {
                        arg<Int> { defaultValue = 33 }
                    }
                }
            }
        }
    }

    data class UserData(val username: String, val stuff: String)

    @Test
    fun `client code can declare custom context class and use it in query resolver`() {
        val schema = KGraphQL.schema {
            query("name") {
                resolver { ctx: Context -> ctx.get<UserData>()?.username }
            }
        }

        val georgeName = "George"
        val response =
            deserialize(schema.executeBlocking("{name}", context = context { +UserData(georgeName, "STUFF") }))
        response.extract<String>("data/name") shouldBe georgeName
    }

    @Test
    fun `client code can use context class in property resolver`() {
        val georgeName = "George"
        val schema = KGraphQL.schema {
            query("actor") {
                resolver { -> Actor("George", 23) }
            }

            type<Actor> {
                property("nickname") {
                    resolver { _: Actor, ctx: Context -> "Hodor and ${ctx.get<UserData>()?.username}" }
                }

                transformation(Actor::name) { name: String, addStuff: Boolean?, ctx: Context ->
                    if (addStuff == true) name + ctx[UserData::class]?.stuff else name
                }
            }
        }

        val context = context {
            +UserData(georgeName, "STUFF")
            inject("ADA")
        }
        val response =
            deserialize(schema.executeBlocking("{actor{ nickname, name(addStuff: true) }}", context = context))
        response.extract<String>("data/actor/name") shouldBe "${georgeName}STUFF"
        response.extract<String>("data/actor/nickname") shouldBe "Hodor and $georgeName"
    }

    @Test
    fun `context type cannot be part of schema`() {
        expect<SchemaException>("Context type cannot be part of schema") {
            KGraphQL.schema {
                query("name") {
                    resolver { ctx: Context -> ctx }
                }
            }
        }
    }

    @Test
    fun `there should be a clear message when query resolver is not present`() {
        expect<IllegalArgumentException>("resolver has to be specified for query [name]") {
            KGraphQL.schema {
                query("name") { }
            }
        }
    }

    @Suppress("unused")
    class SixValues(
        val val1: Int = 1,
        val val2: String = "2",
        val val3: Int = 3,
        val val4: String = "4",
        val val5: Int = 5,
        val val6: String = "6"
    )

    private fun checkSixValuesSchema(schema: Schema) {
        val response = deserialize(
            schema.executeBlocking(
                "{" +
                    "queryWith1Param(val1: 2) { val1 }" +
                    "queryWith2Params(val1: 2, val2: \"3\") { val1, val2 }" +
                    "queryWith3Params(val1: 2, val2: \"3\", val3: 4) { val1, val2, val3 }" +
                    "queryWith4Params(val1: 2, val2: \"3\", val3: 4, val4: \"5\") { val1, val2, val3, val4 }" +
                    "queryWith5Params(val1: 2, val2: \"3\", val3: 4, val4: \"5\", val5: 6) { val1, val2, val3, val4, val5 }" +
                    "queryWith6Params(val1: 2, val2: \"3\", val3: 4, val4: \"5\", val5: 6, val6: \"7\") { val1, val2, val3, val4, val5, val6 }" +
                    "}"
            )
        )
        response.extract<Int>("data/queryWith1Param/val1") shouldBe 2

        response.extract<Int>("data/queryWith2Params/val1") shouldBe 2
        response.extract<String>("data/queryWith2Params/val2") shouldBe "3"

        response.extract<Int>("data/queryWith3Params/val1") shouldBe 2
        response.extract<String>("data/queryWith3Params/val2") shouldBe "3"
        response.extract<Int>("data/queryWith3Params/val3") shouldBe 4

        response.extract<Int>("data/queryWith4Params/val1") shouldBe 2
        response.extract<String>("data/queryWith4Params/val2") shouldBe "3"
        response.extract<Int>("data/queryWith4Params/val3") shouldBe 4
        response.extract<String>("data/queryWith4Params/val4") shouldBe "5"

        response.extract<Int>("data/queryWith5Params/val1") shouldBe 2
        response.extract<String>("data/queryWith5Params/val2") shouldBe "3"
        response.extract<Int>("data/queryWith5Params/val3") shouldBe 4
        response.extract<String>("data/queryWith5Params/val4") shouldBe "5"
        response.extract<Int>("data/queryWith5Params/val5") shouldBe 6

        response.extract<Int>("data/queryWith6Params/val1") shouldBe 2
        response.extract<String>("data/queryWith6Params/val2") shouldBe "3"
        response.extract<Int>("data/queryWith6Params/val3") shouldBe 4
        response.extract<String>("data/queryWith6Params/val4") shouldBe "5"
        response.extract<Int>("data/queryWith6Params/val5") shouldBe 6
        response.extract<String>("data/queryWith6Params/val6") shouldBe "7"
    }

    @Test
    fun `Schema can contain resolvers with up to 6 parameters`() {
        val schema = KGraphQL.schema {
            query("queryWith1Param") {
                resolver { val1: Int ->
                    SixValues(val1)
                }
            }

            query("queryWith2Params") {
                resolver { val1: Int, val2: String ->
                    SixValues(val1, val2)
                }
            }

            query("queryWith3Params") {
                resolver { val1: Int, val2: String, val3: Int ->
                    SixValues(val1, val2, val3)
                }
            }

            query("queryWith4Params") {
                resolver { val1: Int, val2: String, val3: Int, val4: String ->
                    SixValues(val1, val2, val3, val4)
                }
            }

            query("queryWith5Params") {
                resolver { val1: Int, val2: String, val3: Int, val4: String, val5: Int ->
                    SixValues(val1, val2, val3, val4, val5)
                }
            }

            query("queryWith6Params") {
                resolver { val1: Int, val2: String, val3: Int, val4: String, val5: Int, val6: String ->
                    SixValues(val1, val2, val3, val4, val5, val6)
                }
            }
        }

        checkSixValuesSchema(schema)
    }

    @Test
    fun `Schema can contain suspend resolvers`() {
        val schema = KGraphQL.schema {
            query("queryWith1Param") {
                resolver { val1: Int ->
                    delay(1)
                    SixValues(val1)
                }
            }

            query("queryWith2Params") {
                resolver { val1: Int, val2: String ->
                    delay(1)
                    SixValues(val1, val2)
                }
            }

            query("queryWith3Params") {
                resolver { val1: Int, val2: String, val3: Int ->
                    delay(1)
                    SixValues(val1, val2, val3)
                }
            }

            query("queryWith4Params") {
                resolver { val1: Int, val2: String, val3: Int, val4: String ->
                    delay(1)
                    SixValues(val1, val2, val3, val4)
                }
            }

            query("queryWith5Params") {
                resolver { val1: Int, val2: String, val3: Int, val4: String, val5: Int ->
                    delay(1)
                    SixValues(val1, val2, val3, val4, val5)
                }
            }

            query("queryWith6Params") {
                resolver { val1: Int, val2: String, val3: Int, val4: String, val5: Int, val6: String ->
                    delay(1)
                    SixValues(val1, val2, val3, val4, val5, val6)
                }
            }
        }

        checkSixValuesSchema(schema)
    }

    @Test
    fun `client code can specify couroutine dispatcher for execution engine`() {
        defaultSchema {
            configure {
                coroutineDispatcher = Dispatchers.Main
            }

            query("test") {
                resolver { -> "test" }
            }
        }
    }

    @Test
    fun `Schema can have same type and input type with different names`() {
        val schema = defaultSchema {
            query("createType") {
                resolver { input: InputOne -> input }
            }
            inputType<InputOne> {
                name = "TypeAsInput"
            }
            type<InputOne> {
                name = "TypeAsObject"
            }
        }

        schema.typeByKClass(InputOne::class) shouldNotBe null
        schema.inputTypeByKClass(InputOne::class) shouldNotBe null

        val introspection = deserialize(schema.executeBlocking("{__schema{types{name}}}"))
        val types = introspection.extract<List<Map<String, String>>>("data/__schema/types")
        val names = types.map { it["name"] }
        names shouldContain "TypeAsInput"
        names shouldContain "TypeAsObject"
    }

    @Test
    fun `Short int types are mapped to Short Scalar`() {
        val schema = defaultSchema {
            extendedScalars()
            query("shortQuery") {
                resolver { -> 1.toShort() }
            }
        }

        val typesIntrospection = deserialize(schema.executeBlocking("{__schema{types{name}}}"))
        val types = typesIntrospection.extract<List<Map<String, String>>>("data/__schema/types")
        val names = types.map { it["name"] }
        names shouldContain "Short"

        val response =
            deserialize(schema.executeBlocking("{__schema{queryType{fields{ type { ofType { kind name }}}}}}"))
        response.extract<String>("data/__schema/queryType/fields[0]/type/ofType/kind") shouldBe "SCALAR"
        response.extract<String>("data/__schema/queryType/fields[0]/type/ofType/name") shouldBe "Short"
    }

    @Test
    fun `Resolver cannot return an Unit value`() {
        expect<SchemaException>("Resolver for 'main' has no return value") {
            KGraphQL.schema {
                query("main") {
                    resolver { -> }
                }
            }
        }
    }

    private inline fun <reified T : Any> SchemaBuilder.createGenericQuery(x: T) {
        query("data") {
            resolver { -> x }.returns<T>()
        }
    }

    @Test
    fun `specifying return type explicitly allows generic query creation`() {
        val schema = defaultSchema {
            createGenericQuery(InputOne("generic"))
        }

        schema.typeByKClass(InputOne::class) shouldNotBe null
    }

    private inline fun <reified T : Any> SchemaBuilder.createGenericQueryWithoutReturns(x: T) {
        query("data") {
            resolver { -> x }
        }
    }

    @Test
    fun `not specifying return value explicitly with generic query creation throws exception`() {
        expect<SchemaException>("If you construct a query/mutation generically, you must specify the return type T explicitly with resolver{ ... }.returns<T>()") {
            defaultSchema {
                createGenericQueryWithoutReturns(InputOne("generic"))
            }
        }
    }

    @Test
    fun `specifying return type explicitly allows generic query creation that returns List of T`() {
        val schema = defaultSchema {
            createGenericQuery(listOf("generic"))
        }
        val result = deserialize(schema.executeBlocking("{data}"))
        result.extract<List<String>>("data/data") shouldBe listOf("generic")
    }

    private inline fun <T : Any, reified P : Any> TypeDSL<T>.createGenericProperty(x: P) {
        property("data") {
            resolver { _ -> x }.returns<P>()
        }
    }

    @Test
    fun `specifying return type explicitly allows generic property creation`() {
        val schema = defaultSchema {
            query("scenario") {
                resolver { -> "dummy" }
            }
            type<Scenario> {
                createGenericProperty(InputOne("generic"))
            }
        }

        schema.typeByKClass(InputOne::class) shouldNotBe null
    }

    private inline fun <T : Any, reified P : Any> TypeDSL<T>.createGenericPropertyExplicitly(returnType: KType, x: P) {
        property("data") {
            resolver { _ -> x }
            setReturnType(returnType)
        }
    }

    data class Prop<T>(val resultType: KType, val resolver: () -> T)

    @Test
    fun `creation of properties from a list`() {
        val props = listOf(
            Prop(typeOf<Int>()) { 0 },
            Prop(typeOf<String>()) { "test" })

        val schema = defaultSchema {
            query("scenario") {
                resolver { -> "dummy" }
            }
            type<Scenario> {
                props.forEach { prop ->
                    createGenericPropertyExplicitly(prop.resultType, prop.resolver())
                }
            }
        }

        schema.typeByKClass(Int::class) shouldNotBe null
        schema.typeByKClass(String::class) shouldNotBe null
    }

    // https://github.com/aPureBase/KGraphQL/issues/106
    @Test
    fun `Java class as inputType should throw an appropriate exception`() {
        expect<SchemaException>("Java class 'LatLng' as inputType is not supported") {
            KGraphQL.schema {
                query("test") {
                    resolver { radius: Double, location: LatLng ->
                        "Hello $radius. ${location.lat} ${location.lng}"
                    }
                }
            }
        }
    }

    // https://github.com/stuebingerb/KGraphQL/issues/156
    @Test
    fun `empty schema should be invalid`() {
        expect<SchemaException>("Schema must define at least one query") {
            KGraphQL.schema {}
        }
    }

    // https://github.com/stuebingerb/KGraphQL/issues/156
    @Test
    fun `schema without query should be invalid`() {
        expect<SchemaException>("Schema must define at least one query") {
            KGraphQL.schema {
                mutation("dummy") {
                    resolver { -> "dummy" }
                }
                subscription("dummy") {
                    resolver { -> "dummy" }
                }
            }
        }
    }
}
