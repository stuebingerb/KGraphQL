package com.apurebase.kgraphql.stitched.schema.structure

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.ExperimentalAPI
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.request.Introspection
import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.SchemaPrinter
import com.apurebase.kgraphql.schema.SchemaPrinterConfig
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.shouldBeInstanceOf
import com.apurebase.kgraphql.stitched.StitchedKGraphQL
import com.apurebase.kgraphql.stitched.getRemoteSchema
import com.apurebase.kgraphql.stitched.schema.configuration.StitchedSchemaConfiguration
import com.apurebase.kgraphql.stitched.schema.execution.RemoteRequestExecutor
import com.fasterxml.jackson.databind.JsonNode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalAPI::class)
class StitchedSchemaTest {

    object DummyRemoteRequestExecutor : RemoteRequestExecutor {
        override suspend fun execute(node: Execution.Remote, ctx: Context) = null
    }

    data class LocalType(val value: String)
    data class RemoteType(val bar: Int)
    data class RemoteType2(val bars: List<Int?>)
    sealed class UnionType
    data class UnionType1(val one: String)
    data class UnionType2(val two: String?)

    @Suppress("unused")
    enum class RemoteEnum { ONE, TWO }

    /**
     * Executes the default introspection query against this [Schema] and returns it
     * as parsed [IntrospectedSchema]
     */
    private fun Schema.introspected(): IntrospectedSchema {
        val introspectionResponse = executeBlocking(Introspection.query())
        return IntrospectedSchema.fromIntrospectionResponse(introspectionResponse)
    }

    @Test
    fun `stitched schema should allow to configure remote executor`() {
        val customRemoteRequestExecutor = object : RemoteRequestExecutor {
            override suspend fun execute(node: Execution.Remote, ctx: Context): JsonNode? {
                return null
            }
        }
        val schema = StitchedKGraphQL.stitchedSchema {
            configure {
                remoteExecutor = customRemoteRequestExecutor
            }
            localSchema {
                type<LocalType>()
            }
            remoteSchema("remote") {
                getRemoteSchema {
                    query("dummy") {
                        resolver { -> "dummy" }
                    }
                    type<RemoteType> {
                        name = "LocalType"
                    }
                }
            }
        }

        schema.configuration shouldBeInstanceOf StitchedSchemaConfiguration::class
        (schema.configuration as StitchedSchemaConfiguration).remoteExecutor shouldBe customRemoteRequestExecutor
    }

    @Test
    fun `stitched schema should skip duplicate types by name and prefer local types`() {
        val schema = StitchedKGraphQL.stitchedSchema {
            configure {
                remoteExecutor = DummyRemoteRequestExecutor
            }
            localSchema {
                type<LocalType>()
            }
            remoteSchema("remote") {
                getRemoteSchema {
                    query("dummy") {
                        resolver { -> "dummy" }
                    }
                    type<RemoteType> {
                        name = "LocalType"
                    }
                }
            }
        }

        val expectedSDL = """
            type LocalType {
              value: String!
            }
            
            type Query {
              dummy: String!
            }
            
        """.trimIndent()

        SchemaPrinter().print(schema) shouldBe expectedSDL
        SchemaPrinter().print(schema.introspected()) shouldBe expectedSDL
    }

    @Test
    fun `stitched schema should include local and remote types with proper fields`() {
        val schema = StitchedKGraphQL.stitchedSchema {
            configure {
                remoteExecutor = DummyRemoteRequestExecutor
            }
            localSchema { type<LocalType>() }
            remoteSchema("remote") {
                getRemoteSchema {
                    query("remote") {
                        resolver { -> "dummy" }
                    }
                    query("remoteEnum") {
                        resolver { -> RemoteEnum.ONE }
                    }
                    type<RemoteType> {
                        description = "First Remote Type"
                        property(RemoteType::bar) {
                            description = "First Remote Type bar"
                        }
                    }
                    type<RemoteType2> {
                        description = "Second Remote Type"
                    }
                    enum<RemoteEnum> {}
                }
            }
        }

        val expectedSDL = """
            type LocalType {
              value: String!
            }
            
            "Query object"
            type Query {
              remote: String!
              remoteEnum: RemoteEnum!
            }

            "First Remote Type"
            type RemoteType {
              "First Remote Type bar"
              bar: Int!
            }
            
            "Second Remote Type"
            type RemoteType2 {
              bars: [Int]!
            }

            enum RemoteEnum {
              ONE
              TWO
            }
            
        """.trimIndent()

        SchemaPrinter(SchemaPrinterConfig(includeDescriptions = true)).print(schema) shouldBe expectedSDL
        SchemaPrinter(SchemaPrinterConfig(includeDescriptions = true)).print(schema.introspected()) shouldBe expectedSDL
    }

    @Test
    fun `stitched schema should include union types with proper possible types`() {
        val schema = StitchedKGraphQL.stitchedSchema {
            configure {
                remoteExecutor = DummyRemoteRequestExecutor
            }
            remoteSchema("remote") {
                getRemoteSchema {
                    query("dummy") {
                        resolver { -> "dummy" }
                    }
                    unionType<UnionType> {
                        type<UnionType1>()
                        type<UnionType2>()
                    }
                }
            }
        }

        val expectedSDL = """
            type Query {
              dummy: String!
            }
            
            type UnionType1 {
              one: String!
            }

            type UnionType2 {
              two: String
            }
            
            union UnionType = UnionType1 | UnionType2

        """.trimIndent()

        SchemaPrinter().print(schema) shouldBe expectedSDL
        SchemaPrinter().print(schema.introspected()) shouldBe expectedSDL
    }

    @Test
    fun `stitched schema should include all local and remote queries`() {
        val schema = StitchedKGraphQL.stitchedSchema {
            configure {
                remoteExecutor = DummyRemoteRequestExecutor
            }
            localSchema {
                query("localQuery") {
                    resolver { -> LocalType("foo") }
                }
            }
            remoteSchema("remote1") {
                getRemoteSchema {
                    query("remoteQuery") {
                        description = "Remote Query"
                        resolver { -> RemoteType(42) }
                    }
                    query("remoteList") {
                        resolver { -> listOf(RemoteType(42)) }
                    }
                }
            }
            remoteSchema("remote2") {
                getRemoteSchema {
                    query("secondRemoteQuery") {
                        description = "Second Remote Query"
                        resolver { first: Int, second: Int? -> RemoteType2(listOf(first, second)) }.withArgs {
                            arg<Int> { name = "first"; defaultValue = 42 }
                        }
                    }
                    stringScalar<UUID> {
                        deserialize = { uuid: String -> UUID.fromString(uuid) }
                        serialize = UUID::toString
                    }
                }
            }
        }

        val expectedSDL = """
            scalar UUID
            
            type LocalType {
              value: String!
            }

            "Query object"
            type Query {
              localQuery: LocalType!
              remoteList: [RemoteType!]!
              "Remote Query"
              remoteQuery: RemoteType!
              "Second Remote Query"
              secondRemoteQuery(first: Int! = 42, second: Int): RemoteType2!
            }

            type RemoteType {
              bar: Int!
            }
            
            type RemoteType2 {
              bars: [Int]!
            }

        """.trimIndent()

        SchemaPrinter(SchemaPrinterConfig(includeDescriptions = true)).print(schema) shouldBe expectedSDL
        SchemaPrinter(SchemaPrinterConfig(includeDescriptions = true)).print(schema.introspected()) shouldBe expectedSDL
    }

    @Test
    fun `stitched schema should include all local and remote mutations`() {
        val schema = StitchedKGraphQL.stitchedSchema {
            configure {
                remoteExecutor = DummyRemoteRequestExecutor
            }
            localSchema {
                mutation("localMutation") {
                    resolver { -> "local" }
                }
            }
            remoteSchema("remote1") {
                getRemoteSchema {
                    query("remote1") {
                        resolver { -> "dummy" }
                    }
                    mutation("remoteMutation") {
                        description = "Remote Mutation"
                        resolver { -> RemoteType(42) }
                    }
                }
            }
            remoteSchema("remote2") {
                getRemoteSchema {
                    query("remote2") {
                        resolver { -> "dummy" }
                    }
                    inputType<RemoteType> {
                        name = "RemoteInput"
                    }
                    mutation("secondRemoteMutation") {
                        description = "Second Remote Mutation"
                        resolver { remoteType: RemoteType -> remoteType }
                    }
                }
            }
        }

        val expectedSDL = """
            "Mutation object"
            type Mutation {
              localMutation: String!
              "Remote Mutation"
              remoteMutation: RemoteType!
              "Second Remote Mutation"
              secondRemoteMutation(remoteType: RemoteInput!): RemoteType!
            }
            
            "Query object"
            type Query {
              remote1: String!
              remote2: String!
            }

            type RemoteType {
              bar: Int!
            }
            
            input RemoteInput {
              bar: Int!
            }

        """.trimIndent()

        SchemaPrinter(SchemaPrinterConfig(includeDescriptions = true)).print(schema) shouldBe expectedSDL
        SchemaPrinter(SchemaPrinterConfig(includeDescriptions = true)).print(schema.introspected()) shouldBe expectedSDL
    }

    @Test
    fun `schema with remote input types should be printed as expected`() {
        data class TestObject(val name: String)

        val schema = StitchedKGraphQL.stitchedSchema {
            configure {
                remoteExecutor = DummyRemoteRequestExecutor
            }
            remoteSchema("remote") {
                getRemoteSchema {
                    query("dummy") {
                        resolver { -> "dummy" }
                    }
                    mutation("add") {
                        resolver { input: TestObject -> input }
                    }
                }
            }
        }

        val expectedSDL = """
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

        SchemaPrinter().print(schema) shouldBe expectedSDL
        SchemaPrinter().print(schema.introspected()) shouldBe expectedSDL
    }

    @Test
    fun `schema with remote extension properties should be printed as expected`() {
        data class TestObject(val name: String)

        val schema = StitchedKGraphQL.stitchedSchema {
            configure {
                remoteExecutor = DummyRemoteRequestExecutor
            }
            remoteSchema("remote") {
                getRemoteSchema {
                    query("dummy") {
                        resolver { -> "dummy" }
                    }
                    type<TestObject> {
                        property("uppercaseName") {
                            resolver { testObject: TestObject, languageTag: String ->
                                testObject.name.uppercase(
                                    Locale.forLanguageTag(
                                        languageTag
                                    )
                                )
                            }.withArgs {
                                arg<String> { name = "languageTag"; defaultValue = "\"en\"" }
                            }
                        }
                    }
                }
            }
        }

        val expectedSDL = """
            type Query {
              dummy: String!
            }
            
            type TestObject {
              name: String!
              uppercaseName(languageTag: String! = "en"): String!
            }
            
        """.trimIndent()

        SchemaPrinter().print(schema) shouldBe expectedSDL
        SchemaPrinter().print(schema.introspected()) shouldBe expectedSDL
    }

    @Test
    fun `schema with deprecated remote fields should be printed as expected`() {
        data class TestObject(val name: String)

        val schema = StitchedKGraphQL.stitchedSchema {
            configure {
                remoteExecutor = DummyRemoteRequestExecutor
            }
            remoteSchema("remote") {
                getRemoteSchema {
                    query("dummy") {
                        resolver { -> "dummy" }
                    }
                    type<TestObject> {
                        property("oldName") {
                            deprecate("deprecated old name")
                            resolver { testObject: TestObject -> testObject.name }
                        }
                    }
                }
            }
        }

        val expectedSDL = """
            type Query {
              dummy: String!
            }
            
            type TestObject {
              name: String!
              oldName: String! @deprecated(reason: "deprecated old name")
            }
            
        """.trimIndent()

        SchemaPrinter().print(schema) shouldBe expectedSDL
        SchemaPrinter().print(schema.introspected()) shouldBe expectedSDL
    }

    interface InterInter : Inter {
        val value2: Boolean
    }

    interface Inter {
        val value: String
    }

    class Face(override val value: String, override val value2: Boolean = false) : InterInter

    @Test
    fun `schema with remote interfaces should be printed as expected`() {
        val schema = StitchedKGraphQL.stitchedSchema {
            configure {
                remoteExecutor = DummyRemoteRequestExecutor
            }
            remoteSchema("remote") {
                getRemoteSchema {
                    query("interface") {
                        resolver { ->
                            @Suppress("USELESS_CAST")
                            Face("~~MOCK~~") as Inter
                        }
                    }

                    type<InterInter>()
                    type<Inter>()
                    type<Face>()
                }
            }
        }

        val expectedSDL = """
            type Face implements Inter & InterInter {
              value: String!
              value2: Boolean!
            }
            
            type Query {
              interface: Inter!
            }
            
            interface Inter {
              value: String!
            }
            
            interface InterInter implements Inter {
              value: String!
              value2: Boolean!
            }
            
        """.trimIndent()

        SchemaPrinter().print(schema) shouldBe expectedSDL
        SchemaPrinter().print(schema.introspected()) shouldBe expectedSDL
    }

    @Test
    fun `schema should prevent duplicate field names from stitching`() {
        data class SimpleClass(val existing: String)
        expect<SchemaException>("Cannot add stitched field with duplicated name 'existing'") {
            StitchedKGraphQL.stitchedSchema {
                configure {
                    remoteExecutor = DummyRemoteRequestExecutor
                }
                localSchema {
                    query("dummy") {
                        resolver { -> "dummy" }
                    }
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        query("simple") {
                            resolver { -> SimpleClass("existing") }
                        }
                        query("extension") {
                            resolver { -> "extension" }
                        }
                    }
                }
                type("SimpleClass") {
                    stitchedProperty("existing") {
                        remoteQuery("extension")
                    }
                }
            }
        }

        expect<SchemaException>("Cannot add stitched field with duplicated name 'extension' for type 'SimpleClass'") {
            StitchedKGraphQL.stitchedSchema {
                configure {
                    remoteExecutor = DummyRemoteRequestExecutor
                }
                localSchema {
                    query("dummy") {
                        resolver { -> "dummy" }
                    }
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        query("simple") {
                            resolver { -> SimpleClass("existing") }
                        }
                        query("extension") {
                            resolver { -> "extension" }
                        }
                    }
                }
                type("SimpleClass") {
                    stitchedProperty("extension") {
                        remoteQuery("extension")
                    }
                }
                type("SimpleClass") {
                    stitchedProperty("extension") {
                        remoteQuery("extension")
                    }
                }
            }
        }
    }

    @Test
    fun `schema should prevent invalid field names from stitching`() {
        data class SimpleClass(val existing: String)
        expect<SchemaException>("Illegal name '__extension'. Names starting with '__' are reserved for introspection system") {
            StitchedKGraphQL.stitchedSchema {
                configure {
                    remoteExecutor = DummyRemoteRequestExecutor
                }
                localSchema {
                    query("dummy") {
                        resolver { -> "dummy" }
                    }
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        query("simple") {
                            resolver { -> SimpleClass("existing") }
                        }
                        query("extension") {
                            resolver { -> "extension" }
                        }
                    }
                }
                type("SimpleClass") {
                    stitchedProperty("__extension") {
                        remoteQuery("extension")
                    }
                }
            }
        }
    }

    @Test
    fun `schema should prevent multiple local schema definitions`() {
        expect<SchemaException>("Local schema already defined") {
            StitchedKGraphQL.stitchedSchema {
                configure {
                    remoteExecutor = DummyRemoteRequestExecutor
                }
                localSchema {
                    query("dummy") {
                        resolver { -> "dummy" }
                    }
                }
                localSchema {
                    query("other") {
                        resolver { -> "other" }
                    }
                }
            }
        }
    }

    // TODO: make configurable? this doesn't seem like *always* intended
    @Test
    fun `stitched operations should include optional input arguments`() {
        data class SimpleClass(val existing: String)

        val schema = StitchedKGraphQL.stitchedSchema {
            configure {
                remoteExecutor = DummyRemoteRequestExecutor
            }
            localSchema {
                query("dummy") {
                    resolver { -> "dummy" }
                }
            }
            remoteSchema("remote") {
                getRemoteSchema {
                    query("extension") {
                        resolver { existingValue: String -> SimpleClass(existingValue) }
                    }
                    query("extensionWithDefault") {
                        resolver { existingValue: String -> SimpleClass(existingValue) }.withArgs {
                            arg<String> { name = "existingValue"; defaultValue = "\"default\"" }
                        }
                    }
                    query("extensionWithOptional") {
                        resolver { existingValue: String? -> SimpleClass(existingValue ?: "optional") }
                    }
                }
            }
            type("SimpleClass") {
                stitchedProperty("extension") {
                    remoteQuery("extension")
                }
                stitchedProperty("extensionWithDefault") {
                    remoteQuery("extensionWithDefault")
                }
                stitchedProperty("extensionWithOptional") {
                    remoteQuery("extensionWithOptional")
                }
            }
        }

        val expectedSDL = """
            type Query {
              dummy: String!
              extension(existingValue: String!): SimpleClass!
              extensionWithDefault(existingValue: String! = "default"): SimpleClass!
              extensionWithOptional(existingValue: String): SimpleClass!
            }
            
            type SimpleClass {
              existing: String!
              extension(existingValue: String!): SimpleClass
              extensionWithDefault(existingValue: String! = "default"): SimpleClass
              extensionWithOptional(existingValue: String): SimpleClass
            }
            
        """.trimIndent()

        SchemaPrinter().print(schema) shouldBe expectedSDL
        SchemaPrinter().print(schema.introspected()) shouldBe expectedSDL

        schema.executeBlocking(
            """
            {
              __type(name: "SimpleClass") { name kind fields { name } }
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"__type":{"name":"SimpleClass","kind":"OBJECT","fields":[{"name":"existing"},{"name":"extension"},{"name":"extensionWithDefault"},{"name":"extensionWithOptional"}]}}}
        """.trimIndent()
    }
}
