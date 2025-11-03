package com.apurebase.kgraphql.stitched.schema.execution

import com.apurebase.kgraphql.BuiltInErrorCodes
import com.apurebase.kgraphql.ExperimentalAPI
import com.apurebase.kgraphql.GraphQL
import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.GraphqlRequest
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.apurebase.kgraphql.stitched.StitchedGraphQL
import com.apurebase.kgraphql.stitched.getRemoteSchema
import com.apurebase.kgraphql.stitched.schema.structure.StitchedSchemaTest.Face
import com.apurebase.kgraphql.stitched.schema.structure.StitchedSchemaTest.Inter
import com.apurebase.kgraphql.stitched.schema.structure.StitchedSchemaTest.InterInter
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

@OptIn(ExperimentalAPI::class)
class StitchedSchemaExecutionTest {

    data class Remote1(val foo1: String)
    data class Remote2(val foo2: String, val bar2: Int)

    sealed class RemoteUnion
    data class RemoteA(val foo1: String) : RemoteUnion()
    data class RemoteB(val foo2: String, val bar2: Int) : RemoteUnion()

    data class Child(val childFoo: String)
    data class Remote1WithChild(val foo1: String, val child: Child)
    data class RemoteWithList(val foo: String, val bars: List<String>)

    enum class RemoteEnum {
        REMOTE1, REMOTE2
    }

    data class Local(val remoteFoo: String, val isLocal: Boolean = true)

    private fun SchemaBuilder.remoteSchema1() = query("remote1") {
        resolver { -> "remote1" }
    }

    private fun SchemaBuilder.complexRemoteSchema1() = query("remote1") {
        resolver { -> Remote1("remote1.foo1") }
    }

    private fun SchemaBuilder.remoteSchema1WithEnum() = run {
        query("remote1") {
            resolver { -> RemoteEnum.REMOTE1 }
        }
    }

    private fun SchemaBuilder.remoteSchema1WithMutation() = run {
        query("remote1") {
            resolver { -> "dummy" }
        }
        mutation("remote1Mutation") {
            resolver { -> Remote1("remote1.mutationFoo1") }
        }
    }

    private fun SchemaBuilder.remoteSchema1WithExtension() = run {
        query("remote1") {
            resolver { -> "dummy" }
        }
        type<Remote1> {
            property("truncatedFoo1") {
                resolver { remote1: Remote1, chars: Int ->
                    if (remote1.foo1.length > chars) {
                        remote1.foo1.take(chars) + "..."
                    } else {
                        remote1.foo1
                    }
                }.withArgs {
                    arg<Int> { name = "chars"; defaultValue = 10 }
                }
            }
        }
        mutation("createRemote1") {
            resolver { foo1: String -> Remote1(foo1) }
        }
    }

    private fun SchemaBuilder.remoteSchema1WithInterfaces() = run {
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

    private fun SchemaBuilder.remoteSchema1WithChild() = query("remote1") {
        resolver { -> Remote1WithChild("remote1", Child("childFoo")) }
    }

    private fun SchemaBuilder.remoteSchema2() = query("remote2") {
        resolver { -> "remote2" }
    }

    private fun SchemaBuilder.complexRemoteSchema2() = query("remote2") {
        resolver { -> Remote2("remote2Foo", 42) }
    }

    private fun SchemaBuilder.complexRemoteSchema2WithArguments() = run {
        query("remote2") {
            resolver { fooValue: String, barValue: Int? -> Remote2(fooValue, barValue ?: 42) }
        }
        query("requiredRemote2") {
            resolver { fooValue: String, barValue: Int -> Remote2(fooValue, barValue) }
        }
    }

    private fun SchemaBuilder.complexRemoteSchema2WithInputObject() = run {
        query("remote2") {
            resolver { inputObject: Child? -> inputObject?.let { Remote2(inputObject.childFoo, 13) } }
        }
    }

    private fun SchemaBuilder.remoteSchema2WithEnum() = run {
        query("remote2") {
            resolver { -> RemoteEnum.REMOTE2 }
        }
    }

    @Test
    fun `stitched schema with simple remote query execution should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                remoteSchema1()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                remoteSchema2()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("local") {
                        resolver { -> "local" }
                    }
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        remoteSchema1()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        remoteSchema2()
                    }
                }
            }
        }

        // Query remote1 schema but from the *local* endpoint
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(graphqlRequest("{ remote1 }"))
        }.bodyAsText() shouldBe """
            {"data":{"remote1":"remote1"}}
        """.trimIndent()

        // Query remote2 schema but from the *local* endpoint
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(graphqlRequest("{ remote2 }"))
        }.bodyAsText() shouldBe """
            {"data":{"remote2":"remote2"}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with combined remote query execution should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                remoteSchema1()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                remoteSchema2()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("local") {
                        resolver { -> "local" }
                    }
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        remoteSchema1()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        remoteSchema2()
                    }
                }
            }
        }

        // Query all schemas from the *local* endpoint
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(graphqlRequest("{ local remote1 remote2 }"))
        }.bodyAsText() shouldBe """
            {"data":{"local":"local","remote1":"remote1","remote2":"remote2"}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with complex remote query execution should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote")) {
            endpoint = "remote"
            schema {
                complexRemoteSchema1()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("local") {
                        resolver { -> "local" }
                    }
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        complexRemoteSchema1()
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              local: String!
              remote1: Remote1!
            }

            type Remote1 {
              foo1: String!
            }

        """.trimIndent()

        // Query remote1 schema but from the *local* endpoint
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(graphqlRequest("{ remote1 { __typename foo1 } }"))
        }.bodyAsText() shouldBe """
            {"data":{"remote1":{"__typename":"Remote1","foo1":"remote1.foo1"}}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with nested remote objects should work as expected`() = testApplication {
        data class RemoteChild(val childFoo: String, val childChild: RemoteChild?)
        data class RemoteParent(val foo: String, val child: RemoteChild)

        fun SchemaBuilder.remoteSchema() = query("remote") {
            resolver { -> RemoteParent("parent", RemoteChild("child", RemoteChild("childChild", null))) }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote")) {
            endpoint = "remote"
            schema {
                remoteSchema()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("local") {
                        resolver { -> "local" }
                    }
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        remoteSchema()
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              local: String!
              remote: RemoteParent!
            }

            type RemoteChild {
              childChild: RemoteChild
              childFoo: String!
            }

            type RemoteParent {
              child: RemoteChild!
              foo: String!
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(graphqlRequest("{ remote { __typename foo child { __typename childFoo childChild { __typename childFoo childChild { __typename } } } } }"))
        }.bodyAsText() shouldBe """
            {"data":{"remote":{"__typename":"RemoteParent","foo":"parent","child":{"__typename":"RemoteChild","childFoo":"child","childChild":{"__typename":"RemoteChild","childFoo":"childChild","childChild":null}}}}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with nested remote objects across different schemas should work as expected`() =
        testApplication {
            data class RemoteChildChild(val childChildFoo: String)
            data class RemoteChild(val childFoo: String, val childChild: RemoteChildChild)
            data class RemoteParent(val foo: String, val child: RemoteChild)

            fun SchemaBuilder.remote1Schema() = query("remote1") {
                resolver { -> RemoteParent("parent", RemoteChild("child", RemoteChildChild("childChild"))) }
            }

            fun SchemaBuilder.remote2Schema() = query("remote2") {
                resolver { -> "fromRemote2" }
            }
            install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
                endpoint = "remote1"
                schema {
                    remote1Schema()
                }
            }
            install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
                endpoint = "remote2"
                schema {
                    remote2Schema()
                }
            }
            install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
                endpoint = "local"
                stitchedSchema {
                    configure {
                        remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                    }
                    localSchema {
                        query("local") {
                            resolver { -> "local" }
                        }
                    }
                    remoteSchema("remote1") {
                        getRemoteSchema {
                            remote1Schema()
                        }
                    }
                    remoteSchema("remote2") {
                        getRemoteSchema {
                            remote2Schema()
                        }
                    }
                    type("RemoteChildChild") {
                        stitchedProperty("stitchedField") {
                            remoteQuery("remote2")
                        }
                    }
                }
            }

            val sdl = client.get("local?schema").bodyAsText()
            sdl shouldBe """
                type Query {
                  local: String!
                  remote1: RemoteParent!
                  remote2: String!
                }

                type RemoteChild {
                  childChild: RemoteChildChild!
                  childFoo: String!
                }

                type RemoteChildChild {
                  childChildFoo: String!
                  stitchedField: String
                }

                type RemoteParent {
                  child: RemoteChild!
                  foo: String!
                }

            """.trimIndent()

            client.post("local") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(graphqlRequest("{ remote1 { __typename foo child { __typename childFoo childChild { __typename childChildFoo stitchedField } } } }"))
            }.bodyAsText() shouldBe """
            {"data":{"remote1":{"__typename":"RemoteParent","foo":"parent","child":{"__typename":"RemoteChild","childFoo":"child","childChild":{"__typename":"RemoteChildChild","childChildFoo":"childChild","stitchedField":"fromRemote2"}}}}}
        """.trimIndent()
        }

    @Test
    fun `stitched schema with union remote query execution should work as expected`() = testApplication {
        fun SchemaBuilder.unionRemoteSchema() = run {
            query("remote") {
                resolver { type: String ->
                    when (type) {
                        "A" -> RemoteA("remote.union.A")
                        "B" -> RemoteB("remote.union.B", 42)
                        else -> null
                    }
                }
            }
        }

        install(GraphQL.FeatureInstance("KGraphql - Remote")) {
            endpoint = "remote"
            schema {
                unionRemoteSchema()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("local") {
                        resolver { -> "local" }
                    }
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        unionRemoteSchema()
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              local: String!
              remote(type: String!): RemoteUnion
            }

            type RemoteA {
              foo1: String!
            }

            type RemoteB {
              bar2: Int!
              foo2: String!
            }

            union RemoteUnion = RemoteA | RemoteB

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                        remote(type: "B") {
                            __typename
                            ...on RemoteA { foo1 }
                            ...on RemoteB { foo2 bar2 }
                        }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote":{"__typename":"RemoteB","foo2":"remote.union.B","bar2":42}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                        remote(type: "A") {
                            __typename
                            ...on RemoteA { foo1 }
                            ...on RemoteB { foo2 bar2 }
                        }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote":{"__typename":"RemoteA","foo1":"remote.union.A"}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                        remote(type: "") {
                            __typename
                            ...on RemoteA { foo1 }
                            ...on RemoteB { foo2 bar2 }
                        }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote":null}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with named fragments should work as expected`() = testApplication {
        data class Local(val localFoo: String)
        data class RemoteWithUnionChild(val foo: String, val child: RemoteUnion, val localChild: Local)

        fun SchemaBuilder.unionRemoteSchema2() = run {
            query("remote2") {
                resolver<RemoteUnion> { RemoteB("remote.union", 42) }
            }
            query("remote2WithChild") {
                resolver<RemoteWithUnionChild> {
                    RemoteWithUnionChild(
                        "foo",
                        RemoteB("foo2", 1337),
                        Local("fromRemote2WithChild")
                    )
                }
            }
        }

        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                complexRemoteSchema1()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                unionRemoteSchema2()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("local") {
                        resolver { -> Local("local") }
                    }
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        complexRemoteSchema1()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        unionRemoteSchema2()
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Local {
              localFoo: String!
            }

            type Query {
              local: Local!
              remote1: Remote1!
              remote2: RemoteUnion!
              remote2WithChild: RemoteWithUnionChild!
            }

            type Remote1 {
              foo1: String!
            }

            type RemoteA {
              foo1: String!
            }

            type RemoteB {
              bar2: Int!
              foo2: String!
            }

            type RemoteWithUnionChild {
              child: RemoteUnion!
              foo: String!
              localChild: Local!
            }

            union RemoteUnion = RemoteA | RemoteB

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                        local { ...localFragment }
                        remote1 {
                            __typename
                            ...remoteFragment1
                        }
                        remote2 {
                            __typename
                            ...remoteFragment2
                        }
                    }

                    fragment remoteFragment1 on Remote1 {
                        foo1
                    }

                    fragment remoteFragment2 on RemoteUnion {
                        ...on RemoteA { foo1 }
                        ...nestedRemoteFragment2
                    }

                    fragment nestedRemoteFragment2 on RemoteB {
                        foo2 bar2
                    }

                    fragment localFragment on Local {
                        localFoo
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"local":{"localFoo":"local"},"remote1":{"__typename":"Remote1","foo1":"remote1.foo1"},"remote2":{"__typename":"RemoteB","foo2":"remote.union","bar2":42}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                        remote2WithChild {
                            __typename
                            foo
                            child {
                                __typename
                                ...remoteFragment2
                            }
                            localChild {
                                ...localFragment
                            }
                        }
                    }

                    fragment remoteFragment2 on RemoteUnion {
                        ...on RemoteA { foo1 }
                        ...nestedRemoteFragment2
                    }

                    fragment nestedRemoteFragment2 on RemoteB {
                        foo2 bar2
                    }

                    fragment localFragment on Local {
                        __typename
                        localFoo
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote2WithChild":{"__typename":"RemoteWithUnionChild","foo":"foo","child":{"__typename":"RemoteB","foo2":"foo2","bar2":1337},"localChild":{"__typename":"Local","localFoo":"fromRemote2WithChild"}}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                        remote2WithChild {
                            __typename
                            foo
                            child {
                                __typename
                                ...on RemoteA { foo1 }
                                ...on RemoteB { ...nestedRemoteFragment2 }
                            }
                        }
                    }

                    fragment nestedRemoteFragment2 on RemoteB {
                        foo2 bar2
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote2WithChild":{"__typename":"RemoteWithUnionChild","foo":"foo","child":{"__typename":"RemoteB","foo2":"foo2","bar2":1337}}}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with stitched properties inside fragments should work as expected`() = testApplication {
        data class Local(val localFoo: String)

        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                complexRemoteSchema1()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                complexRemoteSchema2()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                    localUrl = endpoint
                }
                localSchema {
                    query("local") {
                        resolver { -> Local("local") }
                    }
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        complexRemoteSchema1()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        complexRemoteSchema2()
                    }
                }
                type("Remote1") {
                    stitchedProperty("stitchedRemote") {
                        nullable = false
                        remoteQuery("remote2")
                    }
                }
                // Specifying multiple properties in different type {...} sections should also work
                type("Remote1") {
                    stitchedProperty("stitchedLocal") {
                        nullable = false
                        remoteQuery("local")
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Local {
              localFoo: String!
            }

            type Query {
              local: Local!
              remote1: Remote1!
              remote2: Remote2!
            }

            type Remote1 {
              foo1: String!
              stitchedLocal: Local!
              stitchedRemote: Remote2!
            }

            type Remote2 {
              bar2: Int!
              foo2: String!
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                        remote1 {
                            __typename
                            ...remoteFragment1
                        }
                    }

                    fragment remoteFragment1 on Remote1 {
                        foo1
                        stitchedRemote { ...remoteFragment2 }
                        stitchedLocal { ...localFragment }
                    }

                    fragment remoteFragment2 on Remote2 {
                        __typename
                        foo2
                        bar2
                    }

                    fragment localFragment on Local {
                        __typename
                        localFoo
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote1":{"__typename":"Remote1","foo1":"remote1.foo1","stitchedRemote":{"__typename":"Remote2","foo2":"remote2Foo","bar2":42},"stitchedLocal":{"__typename":"Local","localFoo":"local"}}}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with enums should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote")) {
            endpoint = "remote"
            schema {
                remoteSchema1WithEnum()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("local") {
                        resolver { -> "local" }
                    }
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        remoteSchema1WithEnum()
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              local: String!
              remote1: RemoteEnum!
            }

            enum RemoteEnum {
              REMOTE1
              REMOTE2
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                        remote1
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote1":"REMOTE1"}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with shared enums should work as expected`() = testApplication {
        // All schemas use the same enum class; this must not cause problems
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                remoteSchema1WithEnum()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                remoteSchema2WithEnum()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("local") {
                        resolver<RemoteEnum?> { null }
                    }
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        remoteSchema1WithEnum()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        remoteSchema2WithEnum()
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              local: RemoteEnum
              remote1: RemoteEnum!
              remote2: RemoteEnum!
            }

            enum RemoteEnum {
              REMOTE1
              REMOTE2
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                        remote1
                        remote2
                        local
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote1":"REMOTE1","remote2":"REMOTE2","local":null}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with simple remote mutation should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                remoteSchema1WithMutation()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                remoteSchema2()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("local") {
                        resolver { -> "local" }
                    }
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        remoteSchema1WithMutation()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        remoteSchema2()
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Mutation {
              remote1Mutation: Remote1!
            }

            type Query {
              local: String!
              remote1: String!
              remote2: String!
            }

            type Remote1 {
              foo1: String!
            }

        """.trimIndent()

        // Query remote1 schema but from the *local* endpoint
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(graphqlRequest("mutation { remote1Mutation { foo1 } }"))
        }.bodyAsText() shouldBe """
            {"data":{"remote1Mutation":{"foo1":"remote1.mutationFoo1"}}}
        """.trimIndent()

        // Query remote2 schema but from the *local* endpoint
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(graphqlRequest("{ remote2 }"))
        }.bodyAsText() shouldBe """
            {"data":{"remote2":"remote2"}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with complex remote mutations should work as expected`() = testApplication {
        fun SchemaBuilder.remoteSchemaWithComplexMutations() = run {
            query("remote1") {
                resolver { -> "dummy" }
            }
            mutation("objectMutation") {
                resolver { input: Remote1 -> input.copy(foo1 = "${input.foo1}-processed") }
            }
            mutation("booleanMutation") {
                resolver { check: Boolean -> Remote1(check.toString()) }
            }
            mutation("intMutation") {
                resolver { number: Int -> Remote1(number.toString()) }
            }
            mutation("optionalIntMutation") {
                resolver { number: Int? -> number?.let { Remote1(number.toString()) } }
            }
            mutation("defaultIntMutation") {
                resolver { number: Int -> Remote1(number.toString()) }.withArgs {
                    arg<Int> {
                        name = "number"; defaultValue = 42
                    }
                }
            }
            mutation("stringMutation") {
                resolver { string: String -> Remote1(string) }
            }
            mutation("enumMutation") {
                resolver { enum: RemoteEnum -> Remote1(enum.name) }
            }
            mutation("enumListMutation") {
                resolver { enums: List<RemoteEnum> -> enums.map { Remote1(it.name) } }
            }
        }

        data class Local(val foo1: String)
        install(GraphQL.FeatureInstance("KGraphql - Remote")) {
            endpoint = "remote"
            schema {
                remoteSchemaWithComplexMutations()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("local") {
                        resolver { -> "local" }
                    }
                    mutation("localMutation") {
                        resolver { number: Int? -> number?.let { Local(number.toString()) } }
                    }
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        remoteSchemaWithComplexMutations()
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Local {
              foo1: String!
            }

            type Mutation {
              booleanMutation(check: Boolean!): Remote1!
              defaultIntMutation(number: Int! = 42): Remote1!
              enumListMutation(enums: [RemoteEnum!]!): [Remote1!]!
              enumMutation(enum: RemoteEnum!): Remote1!
              intMutation(number: Int!): Remote1!
              localMutation(number: Int): Local
              objectMutation(input: Remote1Input!): Remote1!
              optionalIntMutation(number: Int): Remote1
              stringMutation(string: String!): Remote1!
            }

            type Query {
              local: String!
              remote1: String!
            }

            type Remote1 {
              foo1: String!
            }

            enum RemoteEnum {
              REMOTE1
              REMOTE2
            }

            input Remote1Input {
              foo1: String!
            }

        """.trimIndent()

        // Query remote1 schema but from the *local* endpoint
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    mutation {
                      localMutation { __typename foo1 }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"localMutation":null}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    mutation {
                      optionalIntMutation { __typename foo1 }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"optionalIntMutation":null}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    mutation {
                      booleanMutation(check: true) { foo1 }
                      enumMutation(enum: REMOTE1) { foo1 }
                      enumListMutation(enums: [REMOTE2, REMOTE1]) { foo1 }
                      stringMutation(string: "bar1") { foo1 }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"booleanMutation":{"foo1":"true"},"enumMutation":{"foo1":"REMOTE1"},"enumListMutation":[{"foo1":"REMOTE2"},{"foo1":"REMOTE1"}],"stringMutation":{"foo1":"bar1"}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    mutation {
                      defaultIntMutation { foo1 }
                      intMutation(number: 1337) { foo1 }
                      optionalIntMutation { foo1 }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"defaultIntMutation":{"foo1":"42"},"intMutation":{"foo1":"1337"},"optionalIntMutation":null}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    mutation {
                      objectMutation(input: { foo1:"complexMutationFoo" }) { foo1 }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"objectMutation":{"foo1":"complexMutationFoo-processed"}}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with variables should work as expected`() = testApplication {
        data class VariableObject(
            val foo: String,
            val bar: Int,
            val foobar: Double,
            val enum: RemoteEnum,
            val list: List<Boolean>
        )

        fun SchemaBuilder.remoteSchema1WithVariables() = run {
            query("getBoolean") {
                resolver { toGet: Boolean -> toGet }
            }
            query("getInt") {
                resolver { toGet: Int -> toGet }
            }
            query("getFloat") {
                resolver { toGet: Double -> toGet }
            }
            query("getObject") {
                resolver { toGet: VariableObject -> toGet }
            }
            mutation("invertBoolean") {
                resolver { toInvert: Boolean -> !toInvert }
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote"
            schema {
                remoteSchema1WithVariables()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        remoteSchema1WithVariables()
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Mutation {
              invertBoolean(toInvert: Boolean!): Boolean!
            }

            type Query {
              getBoolean(toGet: Boolean!): Boolean!
              getFloat(toGet: Float!): Float!
              getInt(toGet: Int!): Int!
              getObject(toGet: VariableObjectInput!): VariableObject!
            }

            type VariableObject {
              bar: Int!
              enum: RemoteEnum!
              foo: String!
              foobar: Float!
              list: [Boolean!]!
            }

            enum RemoteEnum {
              REMOTE1
              REMOTE2
            }

            input VariableObjectInput {
              bar: Int!
              enum: RemoteEnum!
              foo: String!
              foobar: Float!
              list: [Boolean!]!
            }

        """.trimIndent()

        // Query remote1 schema but from the *local* endpoint
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                      getBoolean(toGet: true)
                      getInt(toGet: 7)
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"getBoolean":true,"getInt":7}}
        """.trimIndent()

        val variables = decodeFromString<JsonObject>(
            """
            {
              "boolVar": true,
              "intVar": 7,
              "floatVar": 13.37,
              "objectVar": { "bar": 8, "foo": "foo\nbar", "foobar": 3.51, "enum": "REMOTE1", "list": [true, false, true] }
            }
            """.trimIndent()
        )
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query(${'$'}boolVar: Boolean!, ${'$'}intVar: Int!, ${'$'}floatVar: Float!, ${'$'}objectVar: VariableObjectInput!) {
                      getBoolean(toGet: ${'$'}boolVar)
                      getInt(toGet: ${'$'}intVar)
                      getFloat(toGet: ${'$'}floatVar)
                      getObject(toGet: ${'$'}objectVar) { foo bar foobar enum list }
                    }
                    """.trimIndent(),
                    variables
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"getBoolean":true,"getInt":7,"getFloat":13.37,"getObject":{"foo":"foo\nbar","bar":8,"foobar":3.51,"enum":"REMOTE1","list":[true,false,true]}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    mutation(${'$'}boolVar: Boolean!) {
                      invertBoolean(toInvert: ${'$'}boolVar)
                    }
                    """.trimIndent(),
                    variables
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"invertBoolean":false}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with nested and linked variables should work as expected`() = testApplication {
        data class RemoteChild(val bar: String)
        data class RemoteObject(
            val foo: String
        )

        fun SchemaBuilder.remoteSchema() = run {
            query("getRemoteObject") {
                resolver { foo: String -> RemoteObject(foo) }
            }
            query("getRemoteChild") {
                resolver { bar: String -> RemoteChild(bar) }
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote"
            schema {
                remoteSchema()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        remoteSchema()
                    }
                }
                type("RemoteObject") {
                    stitchedProperty("stitchedChild") {
                        nullable = false
                        remoteQuery("getRemoteChild").withArgs {
                            arg { name = "bar" }
                        }
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              getRemoteChild(bar: String!): RemoteChild!
              getRemoteObject(foo: String!): RemoteObject!
            }

            type RemoteChild {
              bar: String!
            }

            type RemoteObject {
              foo: String!
              stitchedChild(bar: String!): RemoteChild!
            }

        """.trimIndent()

        // Query remote1 schema but from the *local* endpoint
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                      getRemoteObject(foo: "foo1") {
                        foo
                        stitchedChild(bar: "bar1") {
                          bar
                        }
                      }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"getRemoteObject":{"foo":"foo1","stitchedChild":{"bar":"bar1"}}}}
        """.trimIndent()

        val variables = decodeFromString<JsonObject>(
            """
            {
              "foo": "fooVar",
              "bar": "barVar"
            }
            """.trimIndent()
        )
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query(${'$'}foo: String!, ${'$'}bar: String!) {
                      getRemoteObject(foo: ${'$'}foo) {
                        foo
                        stitchedChild(bar: ${'$'}bar) {
                          bar
                        }
                      }
                    }
                    """.trimIndent(),
                    variables
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"getRemoteObject":{"foo":"fooVar","stitchedChild":{"bar":"barVar"}}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query(${'$'}foo: String!, ${'$'}bar: String!) {
                      getRemoteObject(foo: ${'$'}foo) {
                        foo
                        ...objectFragment
                      }
                    }

                    fragment objectFragment on RemoteObject {
                      __typename
                      stitchedChild(bar: ${'$'}bar) {
                        bar
                      }
                    }
                    """.trimIndent(),
                    variables
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"getRemoteObject":{"foo":"fooVar","__typename":"RemoteObject","stitchedChild":{"bar":"barVar"}}}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with variables and renamed properties should work as expected`() = testApplication {
        data class RemoteChild(val bar: String)
        data class RemoteObject(
            val foo: String
        )

        fun SchemaBuilder.remoteSchema() = run {
            type<RemoteObject> {
                property(RemoteObject::foo) {
                    name = "notFoo"
                }
            }
            query("getRemoteObject") {
                resolver { foo: String -> RemoteObject(foo) }
            }
            query("getRemoteChild") {
                resolver { bar: String -> RemoteChild(bar) }
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote"
            schema {
                remoteSchema()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        remoteSchema()
                    }
                }
                type("RemoteObject") {
                    stitchedProperty("stitchedChild") {
                        nullable = false
                        remoteQuery("getRemoteChild").withArgs {
                            arg { name = "bar" }
                        }
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              getRemoteChild(bar: String!): RemoteChild!
              getRemoteObject(foo: String!): RemoteObject!
            }

            type RemoteChild {
              bar: String!
            }

            type RemoteObject {
              notFoo: String!
              stitchedChild(bar: String!): RemoteChild!
            }

        """.trimIndent()

        // Query remote1 schema but from the *local* endpoint
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                      getRemoteObject(foo: "foo1") {
                        notFoo
                        stitchedChild(bar: "bar1") {
                          bar
                        }
                      }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"getRemoteObject":{"notFoo":"foo1","stitchedChild":{"bar":"bar1"}}}}
        """.trimIndent()

        val variables = decodeFromString<JsonObject>(
            """
            {
              "varFoo": "fooVar",
              "bar": "barVar"
            }
            """.trimIndent()
        )
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query(${'$'}varFoo: String!, ${'$'}bar: String!) {
                      getRemoteObject(foo: ${'$'}varFoo) {
                        notFoo
                        stitchedChild(bar: ${'$'}bar) {
                          bar
                        }
                      }
                    }
                    """.trimIndent(),
                    variables
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"getRemoteObject":{"notFoo":"fooVar","stitchedChild":{"bar":"barVar"}}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query(${'$'}varFoo: String!, ${'$'}bar: String!) {
                      getRemoteObject(foo: ${'$'}varFoo) {
                        notFoo
                        ...objectFragment
                      }
                    }

                    fragment objectFragment on RemoteObject {
                      __typename
                      stitchedChild(bar: ${'$'}bar) {
                        bar
                      }
                    }
                    """.trimIndent(),
                    variables
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"getRemoteObject":{"notFoo":"fooVar","__typename":"RemoteObject","stitchedChild":{"bar":"barVar"}}}}
        """.trimIndent()
    }

    @Test
    fun `schema with remote extension properties and aliases should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote")) {
            endpoint = "remote"
            schema {
                remoteSchema1WithExtension()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        remoteSchema1WithExtension()
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Mutation {
              createRemote1(foo1: String!): Remote1!
            }

            type Query {
              remote1: String!
            }

            type Remote1 {
              foo1: String!
              truncatedFoo1(chars: Int! = 10): String!
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    mutation {
                      createRemote1(foo1: "a very very long foo") {
                        foo1
                        truncatedFoo1
                        veryTruncatedFoo1: truncatedFoo1(chars: 2)
                      }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"createRemote1":{"foo1":"a very very long foo","truncatedFoo1":"a very ver...","veryTruncatedFoo1":"a ..."}}}
        """.trimIndent()
    }

    @Test
    fun `schema with stitched extension properties should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                complexRemoteSchema1()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                remoteSchema2()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        complexRemoteSchema1()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        remoteSchema2()
                    }
                }
                type("Remote1") {
                    stitchedProperty("stitchedRemote2") {
                        remoteQuery("remote2")
                    }
                    stitchedProperty("stitchedRemote2Required") {
                        nullable = false
                        remoteQuery("remote2")
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              remote1: Remote1!
              remote2: String!
            }

            type Remote1 {
              foo1: String!
              stitchedRemote2: String
              stitchedRemote2Required: String!
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remote1 { foo1 }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote1":{"foo1":"remote1.foo1"}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remote1 { foo1 stitchedRemote2 }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote1":{"foo1":"remote1.foo1","stitchedRemote2":"remote2"}}}
        """.trimIndent()
    }

    @Test
    fun `schema with stitched enum extension properties should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                remoteSchema1WithEnum()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                complexRemoteSchema2()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        remoteSchema1WithEnum()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        complexRemoteSchema2()
                    }
                }
                type("Remote2") {
                    stitchedProperty("stitchedEnum") {
                        remoteQuery("remote1")
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              remote1: RemoteEnum!
              remote2: Remote2!
            }

            type Remote2 {
              bar2: Int!
              foo2: String!
              stitchedEnum: RemoteEnum
            }

            enum RemoteEnum {
              REMOTE1
              REMOTE2
            }

        """.trimIndent()

        // Regular call should work
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remote2 { bar2 stitchedEnum }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote2":{"bar2":42,"stitchedEnum":"REMOTE1"}}}
        """.trimIndent()

        // Call with *only* stitched fields should work (without complaining about missing selection sets)
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remote2 { s1:stitchedEnum s2:stitchedEnum }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote2":{"s1":"REMOTE1","s2":"REMOTE1"}}}
        """.trimIndent()

        // But validation on incorrect fields should still work
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remote2 { nonexisting stitchedEnum }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"errors":[{"message":"Property nonexisting on Remote2 does not exist","locations":[{"line":2,"column":13}],"path":[],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()
    }

    @Test
    fun `schema with stitched local types should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote")) {
            endpoint = "remote"
            schema {
                remoteSchema1()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("local") {
                        resolver { -> Local("local") }
                    }
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        remoteSchema1()
                    }
                }
                type("Local") {
                    stitchedProperty("stitched") {
                        remoteQuery("remote1")
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Local {
              isLocal: Boolean!
              remoteFoo: String!
              stitched: String
            }
            
            type Query {
              local: Local!
              remote1: String!
            }

        """.trimIndent()

        // Regular call should work
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      local { remoteFoo stitched }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"local":{"remoteFoo":"local","stitched":"remote1"}}}
        """.trimIndent()
    }

    @Test
    fun `schema with stitched enum extension properties from parent should work as expected`() = testApplication {
        fun SchemaBuilder.remote1Schema() = run {
            query("remote1") {
                resolver { enum: RemoteEnum -> enum }
            }
        }

        data class Remote2TypeWithEnum(val localEnum: RemoteEnum)

        fun SchemaBuilder.remote2Schema() = run {
            query("remote2") {
                resolver { enum: RemoteEnum -> Remote2TypeWithEnum(enum) }
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                remote1Schema()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                remote2Schema()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        remote1Schema()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        remote2Schema()
                    }
                }
                type("Remote2TypeWithEnum") {
                    stitchedProperty("stitchedEnum") {
                        remoteQuery("remote1").withArgs {
                            arg { name = "enum"; parentFieldName = "localEnum" }
                        }
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              remote1(enum: RemoteEnum!): RemoteEnum!
              remote2(enum: RemoteEnum!): Remote2TypeWithEnum!
            }

            type Remote2TypeWithEnum {
              localEnum: RemoteEnum!
              stitchedEnum: RemoteEnum
            }

            enum RemoteEnum {
              REMOTE1
              REMOTE2
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      one: remote2(enum: REMOTE1) { localEnum stitchedEnum }
                      two: remote2(enum: REMOTE2) { localEnum stitchedEnum }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"one":{"localEnum":"REMOTE1","stitchedEnum":"REMOTE1"},"two":{"localEnum":"REMOTE2","stitchedEnum":"REMOTE2"}}}
        """.trimIndent()
    }

    @Test
    fun `schema with complex stitched extension properties should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                complexRemoteSchema1()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                complexRemoteSchema2()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                    localUrl = endpoint
                }
                localSchema {
                    query("local") {
                        resolver { remoteFoo: String -> Local(remoteFoo) }
                    }
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        complexRemoteSchema1()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        complexRemoteSchema2()
                    }
                }
                type("Remote1") {
                    stitchedProperty("stitchedRemote2") {
                        nullable = false
                        remoteQuery("remote2")
                    }
                    stitchedProperty("stitchedLocal") {
                        nullable = false
                        remoteQuery("local").withArgs {
                            arg { name = "remoteFoo"; parentFieldName = "foo1" }
                        }
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Local {
              isLocal: Boolean!
              remoteFoo: String!
            }

            type Query {
              local(remoteFoo: String!): Local!
              remote1: Remote1!
              remote2: Remote2!
            }

            type Remote1 {
              foo1: String!
              stitchedLocal: Local!
              stitchedRemote2: Remote2!
            }

            type Remote2 {
              bar2: Int!
              foo2: String!
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remote1 { foo1 stitchedRemote2 { foo2 bar2 } }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote1":{"foo1":"remote1.foo1","stitchedRemote2":{"foo2":"remote2Foo","bar2":42}}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remote1 { foo1 stitchedRemote2 { foo2 } stitchedLocal { remoteFoo isLocal } }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote1":{"foo1":"remote1.foo1","stitchedRemote2":{"foo2":"remote2Foo"},"stitchedLocal":{"remoteFoo":"remote1.foo1","isLocal":true}}}}
        """.trimIndent()
    }

    @Test
    fun `schema with null parent arguments should skip remote operation with non-nullable remote argument`() =
        testApplication {
            data class Remote(val foo: String?)

            fun SchemaBuilder.remoteSchema() = run {
                query("remoteFoo") {
                    resolver { -> Remote("foo") }
                }
                query("remoteNull") {
                    resolver { -> Remote(null) }
                }
            }
            install(GraphQL.FeatureInstance("KGraphql - Remote")) {
                endpoint = "remote"
                schema {
                    remoteSchema()
                }
            }
            install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
                endpoint = "local"
                stitchedSchema {
                    configure {
                        remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                        localUrl = endpoint
                    }
                    localSchema {
                        query("local") {
                            resolver { fooInput: String -> fooInput }
                        }
                    }
                    remoteSchema("remote") {
                        getRemoteSchema {
                            remoteSchema()
                        }
                    }
                    type("Remote") {
                        stitchedProperty("stitchedLocal") {
                            remoteQuery("local").withArgs {
                                arg { name = "fooInput"; parentFieldName = "foo" }
                            }
                        }
                    }
                }
            }

            val sdl = client.get("local?schema").bodyAsText()
            sdl shouldBe """
                type Query {
                  local(fooInput: String!): String!
                  remoteFoo: Remote!
                  remoteNull: Remote!
                }

                type Remote {
                  foo: String
                  stitchedLocal: String
                }

            """.trimIndent()

            client.post("local") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(
                    graphqlRequest(
                        """
                    {
                      remoteFoo { foo stitchedLocal }
                    }
                    """.trimIndent()
                    )
                )
            }.bodyAsText() shouldBe """
                {"data":{"remoteFoo":{"foo":"foo","stitchedLocal":"foo"}}}
            """.trimIndent()

            client.post("local") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(
                    graphqlRequest(
                        """
                    {
                      remoteNull { foo stitchedLocal }
                    }
                    """.trimIndent()
                    )
                )
            }.bodyAsText() shouldBe """
                {"data":{"remoteNull":{"foo":null,"stitchedLocal":null}}}
            """.trimIndent()
        }

    @Test
    fun `schema with null parent arguments should execute remote operation with nullable remote argument`() =
        testApplication {
            data class Remote(val foo: String?)

            fun SchemaBuilder.remoteSchema() = run {
                query("remoteFoo") {
                    resolver { -> Remote("foo") }
                }
                query("remoteNull") {
                    resolver { -> Remote(null) }
                }
            }
            install(GraphQL.FeatureInstance("KGraphql - Remote")) {
                endpoint = "remote"
                schema {
                    remoteSchema()
                }
            }
            install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
                endpoint = "local"
                stitchedSchema {
                    configure {
                        remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                        localUrl = endpoint
                    }
                    localSchema {
                        query("local") {
                            resolver { optionalFooInput: String? -> optionalFooInput ?: "called with null" }
                        }
                    }
                    remoteSchema("remote") {
                        getRemoteSchema {
                            remoteSchema()
                        }
                    }
                    type("Remote") {
                        stitchedProperty("stitchedLocal") {
                            remoteQuery("local").withArgs {
                                arg { name = "optionalFooInput"; parentFieldName = "foo" }
                            }
                        }
                    }
                }
            }

            val sdl = client.get("local?schema").bodyAsText()
            sdl shouldBe """
                type Query {
                  local(optionalFooInput: String): String!
                  remoteFoo: Remote!
                  remoteNull: Remote!
                }

                type Remote {
                  foo: String
                  stitchedLocal: String
                }

            """.trimIndent()

            client.post("local") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(
                    graphqlRequest(
                        """
                        {
                          remoteFoo { foo stitchedLocal }
                        }
                        """.trimIndent()
                    )
                )
            }.bodyAsText() shouldBe """
                {"data":{"remoteFoo":{"foo":"foo","stitchedLocal":"foo"}}}
            """.trimIndent()

            client.post("local") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(
                    graphqlRequest(
                        """
                        {
                          remoteNull { foo stitchedLocal }
                        }
                        """.trimIndent()
                    )
                )
            }.bodyAsText() shouldBe """
                {"data":{"remoteNull":{"foo":null,"stitchedLocal":"called with null"}}}
            """.trimIndent()
        }

    @Test
    fun `schema with parent arguments and special characters should work as expected`() = testApplication {
        data class Remote(val foo: String?)

        fun SchemaBuilder.remoteSchema() = run {
            query("remoteFoo") {
                resolver { input: String -> Remote(input) }
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote")) {
            endpoint = "remote"
            schema {
                remoteSchema()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                    localUrl = endpoint
                }
                localSchema {
                    query("local") {
                        resolver { fooInput: String -> fooInput }
                    }
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        remoteSchema()
                    }
                }
                type("Remote") {
                    stitchedProperty("stitchedLocal") {
                        remoteQuery("local").withArgs {
                            arg { name = "fooInput"; parentFieldName = "foo" }
                        }
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              local(fooInput: String!): String!
              remoteFoo(input: String!): Remote!
            }

            type Remote {
              foo: String
              stitchedLocal: String
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remoteFoo(input: "foo") { foo stitchedLocal }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remoteFoo":{"foo":"foo","stitchedLocal":"foo"}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      local(fooInput: "a very \"special\" kind\t\r\b\f\n\n of \\t foo")
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"local":"a very \"special\" kind\t\r\bf\n\n of \\t foo"}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remoteFoo(input: "a very \"special\" kind\t\r\b\f\n\n of \\t foo") { foo stitchedLocal }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remoteFoo":{"foo":"a very \"special\" kind\t\r\bf\n\n of \\t foo","stitchedLocal":"a very \"special\" kind\t\r\bf\n\n of \\t foo"}}}
        """.trimIndent()
    }

    @Test
    fun `schema with stitched extension properties and arguments should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                complexRemoteSchema1()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                complexRemoteSchema2WithArguments()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        complexRemoteSchema1()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        complexRemoteSchema2WithArguments()
                    }
                }
                type("Remote1") {
                    stitchedProperty("stitchedRemote2") {
                        nullable = false
                        remoteQuery("remote2").withArgs {
                            arg { name = "barValue" }
                            arg { name = "fooValue"; parentFieldName = "foo1" }
                        }
                    }
                    stitchedProperty("stitchedRequiredRemote2") {
                        nullable = false
                        remoteQuery("requiredRemote2").withArgs {
                            arg { name = "barValue" }
                            arg { name = "fooValue" }
                        }
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              remote1: Remote1!
              remote2(barValue: Int, fooValue: String!): Remote2!
              requiredRemote2(barValue: Int!, fooValue: String!): Remote2!
            }

            type Remote1 {
              foo1: String!
              stitchedRemote2(barValue: Int): Remote2!
              stitchedRequiredRemote2(barValue: Int!, fooValue: String!): Remote2!
            }

            type Remote2 {
              bar2: Int!
              foo2: String!
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remote1 { foo1 stitchedRemote2 { foo2 bar2 } }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote1":{"foo1":"remote1.foo1","stitchedRemote2":{"foo2":"remote1.foo1","bar2":42}}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remote1 { foo1 stitchedRequiredRemote2(barValue: 13,fooValue:"foo") { foo2 bar2 } }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote1":{"foo1":"remote1.foo1","stitchedRequiredRemote2":{"foo2":"foo","bar2":13}}}}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remote1 { foo1 stitchedRemote2(barValue: 43) { foo2 bar2 } }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote1":{"foo1":"remote1.foo1","stitchedRemote2":{"foo2":"remote1.foo1","bar2":43}}}}
        """.trimIndent()
    }

    @Test
    fun `schema with stitched extension properties and input objects should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                remoteSchema1WithChild()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                complexRemoteSchema2WithInputObject()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        remoteSchema1WithChild()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        complexRemoteSchema2WithInputObject()
                    }
                }
                type("Remote1WithChild") {
                    stitchedProperty("stitchedRemote2") {
                        nullable = true
                        remoteQuery("remote2").withArgs {
                            arg { name = "inputObject"; parentFieldName = "child" }
                        }
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Child {
              childFoo: String!
            }

            type Query {
              remote1: Remote1WithChild!
              remote2(inputObject: ChildInput): Remote2
            }

            type Remote1WithChild {
              child: Child!
              foo1: String!
              stitchedRemote2: Remote2
            }

            type Remote2 {
              bar2: Int!
              foo2: String!
            }

            input ChildInput {
              childFoo: String!
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remote1 { __typename foo1 stitchedRemote2 { __typename foo2 bar2 } child { __typename childFoo } }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote1":{"__typename":"Remote1WithChild","foo1":"remote1","stitchedRemote2":{"__typename":"Remote2","foo2":"childFoo","bar2":13},"child":{"__typename":"Child","childFoo":"childFoo"}}}}
        """.trimIndent()
    }

    @Test
    fun `schema with stitched list properties should work as expected`() = testApplication {
        fun SchemaBuilder.schema1() = query("remote1") {
            // TODO: make it work for a single foo and multiple queries as well, i.e. a list(1, 2, 3) results in 3 queries
            resolver { foos: List<String> -> foos.map { Remote1(it) } }
        }

        fun SchemaBuilder.schema2() = query("remote2WithList") {
            resolver { -> RemoteWithList("remoteWithList", listOf("bar1", "bar3", "bar2")) }
        }

        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                schema1()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                schema2()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        schema1()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        schema2()
                    }
                }
                type("RemoteWithList") {
                    stitchedProperty("barObjects") {
                        remoteQuery("remote1").withArgs {
                            arg { name = "foos"; parentFieldName = "bars" }
                        }
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type Query {
              remote1(foos: [String!]!): [Remote1!]!
              remote2WithList: RemoteWithList!
            }

            type Remote1 {
              foo1: String!
            }

            type RemoteWithList {
              barObjects: [Remote1!]
              bars: [String!]!
              foo: String!
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    {
                      remote2WithList { foo bars barObjects { foo1 } }
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote2WithList":{"foo":"remoteWithList","bars":["bar1","bar3","bar2"],"barObjects":[{"foo1":"bar1"},{"foo1":"bar3"},{"foo1":"bar2"}]}}}
        """.trimIndent()
    }

    @Test
    fun `schema with remote interfaces should work as expected`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Remote")) {
            endpoint = "remote"
            schema {
                remoteSchema1WithInterfaces()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        remoteSchema1WithInterfaces()
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
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

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest("{interface{value, __typename ... on Face{value2}}}")
            )
        }.bodyAsText() shouldBe """
            {"data":{"interface":{"value":"~~MOCK~~","__typename":"Face","value2":false}}}
        """.trimIndent()
    }

    @Test
    fun `stitched schema with common remote types should work as expected`() = testApplication {
        data class CommonType(val fromSchema: String)
        data class Remote1Type(val foo: String, val common: CommonType)
        data class Remote2Type(val foo: String, val common: CommonType)

        fun SchemaBuilder.remoteSchema1() = query("remote1") {
            resolver { foo: String -> Remote1Type(foo, CommonType("remote1")) }
        }

        fun SchemaBuilder.remoteSchema2() = query("remote2") {
            resolver { foo: String -> Remote2Type(foo, CommonType("remote2")) }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote1")) {
            endpoint = "remote1"
            schema {
                remoteSchema1()
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote2")) {
            endpoint = "remote2"
            schema {
                remoteSchema2()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("local") {
                        resolver { -> "local" }
                    }
                }
                remoteSchema("remote1") {
                    getRemoteSchema {
                        remoteSchema1()
                    }
                }
                remoteSchema("remote2") {
                    getRemoteSchema {
                        remoteSchema2()
                    }
                }
            }
        }

        val sdl = client.get("local?schema").bodyAsText()
        sdl shouldBe """
            type CommonType {
              fromSchema: String!
            }

            type Query {
              local: String!
              remote1(foo: String!): Remote1Type!
              remote2(foo: String!): Remote2Type!
            }

            type Remote1Type {
              common: CommonType!
              foo: String!
            }

            type Remote2Type {
              common: CommonType!
              foo: String!
            }

        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(
                graphqlRequest(
                    """
                    query {
                      remote1(foo: "remote1Foo") {
                        __typename
                        foo
                        common {
                          ...common
                        }
                      }
                      remote2(foo: "remote2Foo") {
                        __typename
                        foo
                        common {
                          ...common
                        }
                      }
                    }

                    fragment common on CommonType {
                      __typename
                      fromSchema
                    }
                    """.trimIndent()
                )
            )
        }.bodyAsText() shouldBe """
            {"data":{"remote1":{"__typename":"Remote1Type","foo":"remote1Foo","common":{"__typename":"CommonType","fromSchema":"remote1"}},"remote2":{"__typename":"Remote2Type","foo":"remote2Foo","common":{"__typename":"CommonType","fromSchema":"remote2"}}}}
        """.trimIndent()
    }

    @Test
    fun `errors from remote execution should be propagated correctly`() = testApplication {
        fun SchemaBuilder.remoteSchema() = run {
            query("failRemote") {
                resolver<String> {
                    throw GraphQLError(
                        message = "don't call me remote!",
                        extensions = mapOf(
                            "type" to BuiltInErrorCodes.BAD_USER_INPUT.name,
                            "remoteErrorKey" to listOf(
                                "remoteErrorValue1",
                                "remoteErrorValue2"
                            )
                        )
                    )
                }
            }
            query("failRemote2") {
                resolver<String> {
                    throw IllegalStateException()
                }
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Remote")) {
            endpoint = "remote"
            schema {
                remoteSchema()
            }
        }
        install(StitchedGraphQL.FeatureInstance("KGraphql - Local")) {
            endpoint = "local"
            stitchedSchema {
                configure {
                    remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
                }
                localSchema {
                    query("failLocal") {
                        resolver<String> {
                            throw GraphQLError(
                                message = "don't call me local!",
                                extensions = mapOf(
                                    "type" to BuiltInErrorCodes.INTERNAL_SERVER_ERROR.name,
                                    "detail" to mapOf("localErrorKey" to "localErrorValue")
                                )
                            )
                        }
                    }
                }
                remoteSchema("remote") {
                    getRemoteSchema {
                        remoteSchema()
                    }
                }
            }
        }

        // Query with invalid syntax must not contain a data key
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(graphqlRequest("{ failSyntax }"))
        }.bodyAsText() shouldBe """
            {"errors":[{"message":"Property failSyntax on Query does not exist","locations":[{"line":1,"column":3}],"path":[],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()

        // Query that failed during execution
        // TODO: should IMHO contain a data key according to https://spec.graphql.org/draft/#sec-Response-Format
        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(graphqlRequest("{ failLocal }"))
        }.bodyAsText() shouldBe """
            {"errors":[{"message":"don't call me local!","locations":[],"path":[],"extensions":{"type":"INTERNAL_SERVER_ERROR","detail":{"localErrorKey":"localErrorValue"}}}]}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(graphqlRequest("{ failRemote }"))
        }.bodyAsText() shouldBe """
            {"errors":[{"message":"don't call me remote!","locations":[{"line":1,"column":3}],"path":[],"extensions":{"remoteUrl":"remote","remoteOperation":"failRemote","type":"BAD_USER_INPUT","remoteErrorKey":["remoteErrorValue1","remoteErrorValue2"]}}]}
        """.trimIndent()

        client.post("local") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(graphqlRequest("{ failRemote2 }"))
        }.bodyAsText() shouldBe """
            {"errors":[{"message":"Error(s) during remote execution","locations":[{"line":1,"column":3}],"path":[],"extensions":{"remoteUrl":"remote","remoteOperation":"failRemote2","type":"INTERNAL_SERVER_ERROR"}}]}
        """.trimIndent()
    }

    private fun graphqlRequest(query: String, variables: JsonObject? = null): String =
        encodeToString<GraphqlRequest>(GraphqlRequest(query = query, variables = variables))
}

// TODO:
//  test some error cases (like field validation on remote properties etc)
