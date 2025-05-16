package com.apurebase.kgraphql.schema

import com.apurebase.kgraphql.KGraphQL
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random
import kotlin.reflect.typeOf

class SchemaPrinterTest {

    data class Author(val name: String, val books: List<Book>)
    data class Book(val title: String?, val author: Author)
    data class Scenario(val author: String, val content: String)
    data class InputObject(val id: Int, val stringInput: String, val intInput: Int, val optional: String?)
    data class TestObject(val name: String)
    enum class TestEnum {
        TYPE1, TYPE2
    }

    data class DeprecatedObject(
        val old: String,
        val new: String
    )

    interface BaseInterface {
        val base: String
    }

    interface SimpleInterface : BaseInterface {
        val simple: String
    }

    interface OtherInterface {
        val other1: String?
        val other2: List<String?>?
    }

    data class Simple(override val base: String, override val simple: String, val extra: String) : SimpleInterface
    data class Complex(
        override val base: String,
        override val other1: String?,
        override val other2: List<String?>?,
        val extra: Int
    ) : BaseInterface, OtherInterface

    data class NestedLists(
        val nested1: List<List<String?>>,
        val nested2: List<List<List<List<List<String>?>>?>>,
        val nested3: List<List<List<List<List<List<List<String?>>?>>>>>?
    )

    @Test
    fun `schema with types should be printed as expected`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            type<Book>()
            type<Author>()
        }

        SchemaPrinter().print(schema) shouldBe """
            type Author {
              books: [Book!]!
              name: String!
            }

            type Book {
              author: Author!
              title: String
            }
            
            type Query {
              dummy: String!
            }

        """.trimIndent()
    }

    @Test
    fun `schema with nested lists should be printed as expected`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            type<NestedLists>()
        }

        SchemaPrinter().print(schema) shouldBe """
            type NestedLists {
              nested1: [[String]!]!
              nested2: [[[[[String!]]!]]!]!
              nested3: [[[[[[[String]!]]!]!]!]!]
            }
            
            type Query {
              dummy: String!
            }

        """.trimIndent()
    }

    @Test
    fun `schema with union types should be printed as expected`() {
        val schema = KGraphQL.schema {
            query("scenario") {
                resolver { -> Scenario("Gamil Kalus", "TOO LONG") }
            }

            val linked = unionType("Linked") {
                type<Author>()
                type<Scenario>()
            }

            type<Scenario> {
                unionProperty("pdf") {
                    returnType = linked
                    description = "link to pdf representation of scenario"
                    resolver { scenario: Scenario ->
                        if (scenario.author.startsWith("Gamil")) {
                            Scenario("gambino", "nope")
                        } else {
                            Author("Chance", emptyList())
                        }
                    }
                }
                unionProperty("nullablePdf") {
                    returnType = linked
                    nullable = true
                    description = "link to pdf representation of scenario"
                    resolver { null }
                }
            }
        }

        SchemaPrinter().print(schema) shouldBe """
            type Author {
              books: [Book!]!
              name: String!
            }
            
            type Book {
              author: Author!
              title: String
            }

            type Query {
              scenario: Scenario!
            }

            type Scenario {
              author: String!
              content: String!
              nullablePdf: Linked
              pdf: Linked!
            }
            
            union Linked = Author | Scenario
            
        """.trimIndent()
    }

    sealed class Child
    data class Child1(val one: String) : Child()
    data class Child2(val two: String?) : Child()

    @Test
    fun `schema with union types out of sealed classes should be printed as expected`() {
        val schema = KGraphQL.schema {
            query("child") {
                resolver<Child> { Child1("one") }
            }
            query("childs") {
                resolver<List<Child>> { listOf(Child2("one")) }
            }
            query("nullchilds") {
                resolver<List<Child?>?> { null }
            }
        }

        SchemaPrinter().print(schema) shouldBe """
            type Child1 {
              one: String!
            }
            
            type Child2 {
              two: String
            }
            
            type Query {
              child: Child!
              childs: [Child!]!
              nullchilds: [Child]
            }
            
            union Child = Child1 | Child2
            
        """.trimIndent()
    }

    @Test
    fun `schema with interfaces should be printed as expected`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            type<Simple>()
            type<Complex>()
            type<BaseInterface>()
            type<SimpleInterface>()
            type<OtherInterface>()
        }

        SchemaPrinter().print(schema) shouldBe """
            type Complex implements BaseInterface & OtherInterface {
              base: String!
              extra: Int!
              other1: String
              other2: [String]
            }
            
            type Query {
              dummy: String!
            }
            
            type Simple implements BaseInterface & SimpleInterface {
              base: String!
              extra: String!
              simple: String!
            }
            
            interface BaseInterface {
              base: String!
            }
            
            interface OtherInterface {
              other1: String
              other2: [String]
            }
            
            interface SimpleInterface implements BaseInterface {
              base: String!
              simple: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema with input types should be printed as expected`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("add") {
                resolver { input: TestObject -> input }
            }
        }

        SchemaPrinter().print(schema) shouldBe """
            type Mutation {
              add(input: TestObjectInput!): TestObject!
            }
            
            type Query {
              dummy: String!
            }
            
            type TestObject {
              name: String!
            }
            
            input TestObjectInput {
              name: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema with custom scalars should be printed as expected`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            stringScalar<UUID> {
                deserialize = UUID::fromString
                serialize = UUID::toString
            }
            stringScalar<LocalDate> {
                deserialize = LocalDate::parse
                serialize = LocalDate::toString
            }
        }

        SchemaPrinter().print(schema) shouldBe """
            scalar LocalDate
            
            scalar UUID
            
            type Query {
              dummy: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema with custom type extensions should be printed as expected`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            type<TestObject> {
                property("addedProperty") {
                    resolver { _ -> "added" }
                }
            }
        }

        SchemaPrinter().print(schema) shouldBe """
            type Query {
              dummy: String!
            }
            
            type TestObject {
              addedProperty: String!
              name: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema with queries should be printed as expected`() {
        val schema = KGraphQL.schema {
            query("getString") {
                resolver { -> "foo" }
            }
            query("randomString") {
                resolver { possibleReturns: List<String> -> possibleReturns.random() }
            }
            query("randomInt") {
                resolver { min: Int, max: Int? -> Random.nextInt(min, max ?: Integer.MAX_VALUE) }
            }
            query("getNullString") {
                resolver<String?> { null }
            }
            query("getObject") {
                resolver { nullObject: Boolean ->
                    if (nullObject) {
                        null
                    } else {
                        TestObject("foo")
                    }
                }
            }
        }

        SchemaPrinter().print(schema) shouldBe """
            type Query {
              getNullString: String
              getObject(nullObject: Boolean!): TestObject
              getString: String!
              randomInt(max: Int, min: Int!): Int!
              randomString(possibleReturns: [String!]!): String!
            }
            
            type TestObject {
              name: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema with mutations should be printed as expected`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("addString") {
                resolver { string: String -> string }
            }
            mutation("addFloat") {
                // Float is Kotlin Double
                resolver { float: Double -> float }
            }
        }

        SchemaPrinter().print(schema) shouldBe """
            type Mutation {
              addFloat(float: Float!): Float!
              addString(string: String!): String!
            }
            
            type Query {
              dummy: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema with enums should be printed as expected`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            enum<TestEnum>()
        }

        SchemaPrinter().print(schema) shouldBe """
            type Query {
              dummy: String!
            }
            
            enum TestEnum {
              TYPE1
              TYPE2
            }

        """.trimIndent()
    }

    @Test
    fun `schema with default values should be printed as expected`() {
        val schema = KGraphQL.schema {
            enum<TestEnum>()

            query("getStringWithDefault") {
                resolver { type: TestEnum, string: String -> type.name + string }.withArgs {
                    arg<TestEnum> { name = "type"; defaultValue = TestEnum.TYPE1 }
                }
            }
            query("getStringsForTypes") {
                resolver { types: List<TestEnum>? -> types.orEmpty().map { it.name } }.withArgs {
                    arg(List::class, typeOf<List<TestEnum>?>()) {
                        name = "types"; defaultValue = listOf(TestEnum.TYPE1, TestEnum.TYPE2)
                    }
                }
            }
            mutation("addStringWithDefault") {
                resolver { prefix: String, string: String, suffix: String? -> prefix + string + suffix }.withArgs {
                    arg<String> { name = "prefix"; defaultValue = "\"_\"" }
                    arg(String::class, typeOf<String?>()) { name = "suffix"; defaultValue = null }
                }
            }
        }

        SchemaPrinter().print(schema) shouldBe """
            type Mutation {
              addStringWithDefault(prefix: String! = "_", string: String!, suffix: String): String!
            }
            
            type Query {
              getStringsForTypes(types: [TestEnum!] = [TYPE1, TYPE2]): [String!]!
              getStringWithDefault(string: String!, type: TestEnum! = TYPE1): String!
            }
            
            enum TestEnum {
              TYPE1
              TYPE2
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema with deprecations should be printed as expected`() {
        val schema = KGraphQL.schema {
            type<DeprecatedObject> {
                property(DeprecatedObject::old) {
                    deprecate("deprecated old value")
                }
            }
            enum<TestEnum> {
                value(TestEnum.TYPE2) {
                    deprecate("deprecated enum value")
                }
            }
            mutation("doStuff") {
                resolver { inputObject: InputObject -> inputObject.id }
            }
            inputType<InputObject> {
                InputObject::optional.configure {
                    deprecate("deprecated old input value")
                }
            }
            query("data") {
                @Suppress("UNUSED_ANONYMOUS_PARAMETER")
                resolver { oldOptional: String?, new: String -> "" }.withArgs {
                    arg(String::class, typeOf<String?>()) {
                        name = "oldOptional"; defaultValue = "\"\""; deprecate("deprecated arg")
                    }
                    arg<String> { name = "new" }
                }
            }
        }

        SchemaPrinter().print(schema) shouldBe """
            type DeprecatedObject {
              new: String!
              old: String! @deprecated(reason: "deprecated old value")
            }
            
            type Mutation {
              doStuff(inputObject: InputObject!): Int!
            }
            
            type Query {
              data(
                new: String!
                oldOptional: String = "" @deprecated(reason: "deprecated arg")
              ): String!
            }
            
            enum TestEnum {
              TYPE1
              TYPE2 @deprecated(reason: "deprecated enum value")
            }
            
            input InputObject {
              id: Int!
              intInput: Int!
              optional: String @deprecated(reason: "deprecated old input value")
              stringInput: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema with descriptions should be printed as expected if descriptions are included`() {
        val schema = KGraphQL.schema {
            extendedScalars()
            type<TestObject> {
                description = "Description for query object"
                property(TestObject::name) {
                    description = "This is the name"
                }
            }
            inputType<TestObject> {
                name = "TestObjectInput"
                description = "Description for input object"
            }
            enum<TestEnum> {
                value(TestEnum.TYPE1) {
                    description = "Enum value description"
                }
            }
            query("getObject") {
                description = "Get a test object"
                resolver { name: String -> TestObject(name) }.withArgs {
                    arg<String> { name = "name"; description = "The desired name" }
                }
            }
            mutation("addObject") {
                description = """
                    Add a test object
                    With some multi-line description
                    (& special characters like " and \n)
                """.trimIndent()
                resolver { toAdd: TestObject -> toAdd }
            }
            subscription("subscribeObject") {
                description = "Subscribe to an object"
                resolver { -> TestObject("name") }
            }
        }

        SchemaPrinter(
            SchemaPrinterConfig(
                includeSchemaDefinition = true,
                includeDescriptions = true
            )
        ).print(schema) shouldBe """
            schema {
              "Query object"
              query: Query
              "Mutation object"
              mutation: Mutation
              "Subscription object"
              subscription: Subscription
            }
            
            "The Long scalar type represents a signed 64-bit numeric non-fractional value"
            scalar Long

            "The Short scalar type represents a signed 16-bit numeric non-fractional value"
            scalar Short
            
            "Mutation object"
            type Mutation {
              "Add a test object"
              "With some multi-line description"
              "(& special characters like " and \n)"
              addObject(toAdd: TestObjectInput!): TestObject!
            }
            
            "Query object"
            type Query {
              "Get a test object"
              getObject(
                "The desired name"
                name: String!
              ): TestObject!
            }
            
            "Subscription object"
            type Subscription {
              "Subscribe to an object"
              subscribeObject: TestObject!
            }
            
            "Description for query object"
            type TestObject {
              "This is the name"
              name: String!
            }
            
            enum TestEnum {
              "Enum value description"
              TYPE1
              TYPE2
            }
            
            "Description for input object"
            input TestObjectInput {
              name: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema with descriptions should be printed as expected if descriptions are excluded`() {
        val schema = KGraphQL.schema {
            type<TestObject> {
                property(TestObject::name) {
                    description = "This is the name"
                }
            }
            enum<TestEnum> {
                value(TestEnum.TYPE1) {
                    description = "Enum value description"
                }
            }
            query("getObject") {
                description = "Get a test object"
                resolver { -> TestObject("name") }
            }
            mutation("addObject") {
                description = """
                    Add a test object
                    With some multi-line description
                    (& special characters like " and \n)
                """.trimIndent()
                resolver { toAdd: TestObject -> toAdd }
            }
            subscription("subscribeObject") {
                description = "Subscribe to an object"
                resolver { -> TestObject("name") }
            }
        }

        SchemaPrinter(SchemaPrinterConfig(includeDescriptions = false)).print(schema) shouldBe """
            type Mutation {
              addObject(toAdd: TestObjectInput!): TestObject!
            }
            
            type Query {
              getObject: TestObject!
            }
            
            type Subscription {
              subscribeObject: TestObject!
            }
            
            type TestObject {
              name: String!
            }
            
            enum TestEnum {
              TYPE1
              TYPE2
            }
            
            input TestObjectInput {
              name: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema built-in directives should be printed as expected if built-in directives are included`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            type<TestObject>()
        }

        SchemaPrinter(SchemaPrinterConfig(includeBuiltInDirectives = true)).print(schema) shouldBe """
            type Query {
              dummy: String!
            }
            
            type TestObject {
              name: String!
            }
            
            directive @deprecated(reason: String) on FIELD_DEFINITION | ARGUMENT_DEFINITION | INPUT_FIELD_DEFINITION | ENUM_VALUE

            directive @include(if: Boolean!) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

            directive @skip(if: Boolean!) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT
            
        """.trimIndent()
    }

    @Test
    fun `schema itself should be included if enforced`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            type<TestObject>()
        }

        SchemaPrinter(SchemaPrinterConfig(includeSchemaDefinition = true)).print(schema) shouldBe """
            schema {
              query: Query
            }
            
            type Query {
              dummy: String!
            }
            
            type TestObject {
              name: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema itself should by default be included if required - other type named Mutation`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            type<TestObject> {
                name = "Mutation"
            }
        }

        SchemaPrinter().print(schema) shouldBe """
            schema {
              query: Query
            }
            
            type Mutation {
              name: String!
            }
            
            type Query {
              dummy: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema itself should by default be included if required - other type named Subscription`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            type<TestObject> {
                name = "Subscription"
            }
        }

        SchemaPrinter().print(schema) shouldBe """
            schema {
              query: Query
            }
            
            type Query {
              dummy: String!
            }
            
            type Subscription {
              name: String!
            }
            
        """.trimIndent()
    }
}
