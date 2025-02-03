package com.apurebase.kgraphql.stitched

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.SchemaPrinter
import com.apurebase.kgraphql.schema.SchemaPrinterConfig
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.UUID

class StitchedSchemaTest {

    data class LocalType(val value: String)
    data class RemoteType(val bar: Int)
    data class RemoteType2(val bars: List<Int?>)
    sealed class UnionType
    data class UnionType1(val one: String)
    data class UnionType2(val two: String?)

    @Suppress("unused")
    enum class RemoteEnum { ONE, TWO }

    @Test
    fun `stitched schema should skip duplicate types by name and prefer local types`() {
        val schema = KGraphQL.schema {
            type<LocalType>()
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

        val sdl = SchemaPrinter().print(schema)
        sdl shouldBeEqualTo """
            type LocalType {
              value: String!
            }
            
            type Query {
              dummy: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `stitched schema should include local and remote types with proper fields`() {
        val schema = KGraphQL.schema {
            type<LocalType>()
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

        val sdl = SchemaPrinter(SchemaPrinterConfig(includeDescriptions = true)).print(schema)
        sdl shouldBeEqualTo """
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
    }

    @Test
    fun `stitched schema should include union types with proper possible types`() {
        val schema = KGraphQL.schema {
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

        val sdl = SchemaPrinter().print(schema)
        sdl shouldBeEqualTo """
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
    }

    @Test
    fun `stitched schema should include all local and remote queries`() {
        val schema = KGraphQL.schema {
            query("localQuery") {
                resolver { -> LocalType("foo") }
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

        val sdl = SchemaPrinter(SchemaPrinterConfig(includeDescriptions = true)).print(schema)
        sdl shouldBeEqualTo """
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
    }

    @Test
    fun `stitched schema should include all local and remote mutations`() {
        val schema = KGraphQL.schema {
            mutation("localMutation") {
                resolver { -> "local" }
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

        val sdl = SchemaPrinter(SchemaPrinterConfig(includeDescriptions = true)).print(schema)
        sdl shouldBeEqualTo """
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
    }

    @Test
    fun `schema with remote input types should be printed as expected`() {
        data class TestObject(val name: String)

        val schema = KGraphQL.schema {
            remoteSchema("remote") {
                getRemoteSchema {
                    query("dummy") {
                        resolver { -> "dummy" }
                    }
                    inputType<TestObject> {
                        name = "TestObjectInput"
                    }
                    mutation("add") {
                        resolver { input: TestObject -> input }
                    }
                }
            }
        }

        SchemaPrinter().print(schema) shouldBeEqualTo """
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
    fun `schema with remote extension properties should be printed as expected`() {
        data class TestObject(val name: String)

        val schema = KGraphQL.schema {
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

        SchemaPrinter().print(schema) shouldBeEqualTo """
            type Query {
              dummy: String!
            }
            
            type TestObject {
              name: String!
              uppercaseName(languageTag: String! = "en"): String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `schema with deprecated remote fields should be printed as expected`() {
        data class TestObject(val name: String)

        val schema = KGraphQL.schema {
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

        SchemaPrinter().print(schema) shouldBeEqualTo """
            type Query {
              dummy: String!
            }
            
            type TestObject {
              name: String!
              oldName: String! @deprecated(reason: "deprecated old name")
            }
            
        """.trimIndent()
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
        val schema = KGraphQL.schema {
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

        SchemaPrinter().print(schema) shouldBeEqualTo """
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
    }

    @Test
    fun `schema should prevent duplicate field names from stitching`() {
        data class SimpleClass(val existing: String)
        invoking {
            KGraphQL.schema {
                query("dummy") {
                    resolver { -> "dummy" }
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
                link(
                    typeName = "SimpleClass",
                    fieldName = "existing",
                    remoteQueryName = "extension"
                )
            }
        } shouldThrow SchemaException::class withMessage "Cannot add stitched field existing with duplicate name"

        invoking {
            KGraphQL.schema {
                query("dummy") {
                    resolver { -> "dummy" }
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
                link(
                    typeName = "SimpleClass",
                    fieldName = "extension",
                    remoteQueryName = "extension"
                )
                link(
                    typeName = "SimpleClass",
                    fieldName = "extension",
                    remoteQueryName = "extension"
                )
            }
        } shouldThrow SchemaException::class withMessage "Cannot add link with duplicated field extension for type SimpleClass"
    }

    // TODO: make configurable? this doesn't seem like *always* intended
    @Test
    fun `stitched operations should include optional input arguments`() {
        data class SimpleClass(val existing: String)

        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
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
            link(
                typeName = "SimpleClass",
                fieldName = "extension",
                remoteQueryName = "extension"
            )
            link(
                typeName = "SimpleClass",
                fieldName = "extensionWithDefault",
                remoteQueryName = "extensionWithDefault"
            )
            link(
                typeName = "SimpleClass",
                fieldName = "extensionWithOptional",
                remoteQueryName = "extensionWithOptional"
            )
        }

        SchemaPrinter().print(schema) shouldBeEqualTo """
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
    }
}
