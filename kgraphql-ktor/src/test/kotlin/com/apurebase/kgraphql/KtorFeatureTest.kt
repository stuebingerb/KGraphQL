package com.apurebase.kgraphql

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test

class KtorFeatureTest : KtorTest() {

    data class User(val id: Int = -1, val name: String = "")

    @Test
    fun `Simple query test`() {
        val server = withServer {
            query("hello") {
                resolver { -> "World!" }
            }
        }

        val response = server("query") {
            field("hello")
        }
        runBlocking {
            response.bodyAsText() shouldBe "{\"data\":{\"hello\":\"World!\"}}"
            response.contentType() shouldBe ContentType.Application.Json
        }
    }

    @Test
    fun `Simple mutation test`() {
        val server = withServer {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("hello") {
                resolver { -> "World! mutation" }
            }
        }

        val response = server("mutation") {
            field("hello")
        }
        runBlocking {
            response.bodyAsText() shouldBe "{\"data\":{\"hello\":\"World! mutation\"}}"
            response.contentType() shouldBe ContentType.Application.Json
        }
    }

    data class Actor(val name: String, val age: Int)
    data class UserData(val username: String, val stuff: String)
    data class Movie(val title: String, val actors: List<Actor>)
    data class Person(val name: String, val age: Int, val favouriteMovie: Movie)

    @Test
    fun `Simple context test`() {
        val georgeName = "George"
        val contextSetup: ContextBuilder.(ApplicationCall) -> Unit = { _ ->
            +UserData(georgeName, "STUFF")
            inject("ADA")
        }

        val server = withServer(contextSetup) {
            query("actor") {
                resolver { -> Actor("George", 23) }
            }

            type<Actor> {
                property("nickname") {
                    resolver { _: Actor, ctx: Context -> "Hodor and ${ctx.get<UserData>()?.username}" }
                }

                transformation(Actor::name) { name: String, addStuff: Boolean?, ctx: Context ->
                    if (addStuff == true) {
                        name + ctx[UserData::class]?.stuff
                    } else {
                        name
                    }
                }
            }
        }

        var response = server("query") {
            field("actor") {
                field("name(addStuff: true)")
            }
        }
        runBlocking {
            response.bodyAsText() shouldBe "{\"data\":{\"actor\":{\"name\":\"${georgeName}STUFF\"}}}"
            response.contentType() shouldBe ContentType.Application.Json
        }

        response = server("query") {
            field("actor") {
                field("nickname")
            }
        }
        runBlocking {
            response.bodyAsText() shouldBe "{\"data\":{\"actor\":{\"nickname\":\"Hodor and $georgeName\"}}}"
            response.contentType() shouldBe ContentType.Application.Json
        }
    }

    @Suppress("unused")
    enum class MockEnum { M1, M2 }

    data class InputOne(val enum: MockEnum, val id: String)

    data class InputTwo(val one: InputOne, val quantity: Int, val tokens: List<String>)

    @Test
    fun `Simple variables test`() {
        val server = withServer {
            inputType<InputTwo>()
            query("test") { resolver { input: InputTwo -> "success: $input" } }
        }

        val response = server("query") {
            field("test(input: \$two)")

            variable("two", "InputTwo!") {
                putJsonObject("one") {
                    put("enum", "M1")
                    put("id", "M1")
                }
                put("quantity", 3434)
                putJsonArray("tokens") {
                    add("23")
                    add("34")
                    add("21")
                    add("434")
                }
            }
        }
        runBlocking {
            response.bodyAsText() shouldBe "{\"data\":{\"test\":\"success: InputTwo(one=InputOne(enum=M1, id=M1), quantity=3434, tokens=[23, 34, 21, 434])\"}}"
            response.contentType() shouldBe ContentType.Application.Json
        }
    }

    @Test
    fun `request error response test`() {
        val server = withServer {
            query("actor") {
                resolver { -> Actor("George", 23) }
            }
        }

        val response = server("query") {
            field("actor") {
                field("nickname")
            }
        }
        runBlocking {
            response.bodyAsText() shouldBe """
                {"errors":[{"message":"Property 'nickname' on 'Actor' does not exist","locations":[{"line":3,"column":1}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
            """.trimIndent()
            response.contentType() shouldBe ContentType.Application.Json
        }
    }

    @Test
    fun `execution error response test`() {
        val server = withServer {
            type<Actor> {
                property("nickname") {
                    resolver { actor: Actor ->
                        require(actor.age <= 30) { "Actors above 30 don't have nicknames" }
                        actor.name.first().toString()
                    }
                }
            }
            query("actors") {
                resolver { -> listOf(Actor("George", 23), Actor("John", 42), Actor("Jack", 21)) }
            }
        }

        val response = server("query") {
            field("actors") {
                field("nickname")
            }
        }
        runBlocking {
            response.bodyAsText() shouldBe """
                {"errors":[{"message":"Actors above 30 don't have nicknames","locations":[{"line":3,"column":1}],"path":["actors",1,"nickname"],"extensions":{"type":"INTERNAL_SERVER_ERROR"}}],"data":null}
            """.trimIndent()
            response.contentType() shouldBe ContentType.Application.Json
        }
    }

    @Test
    fun `nested execution error response test`() {
        val server = withServer {
            type<Actor> {
                property("nickname") {
                    resolver { actor: Actor ->
                        require(actor.age <= 30) { "Actors above 30 don't have nicknames" }
                        actor.name.first().toString()
                    }
                }
            }
            query("persons") {
                resolver<List<Person>?> {
                    listOf(
                        Person("Mary", 32, Movie("Sharks", listOf(Actor("George", 23), Actor("Jack", 21)))),
                        Person("Jimmy", 11, Movie("Unknown", listOf(Actor("John", 42))))
                    )
                }
            }
        }

        val response = server("query") {
            field("persons") {
                field("name")
                field("favouriteMovie") {
                    field("actors") {
                        field("name")
                        field("nickname")
                    }
                }
            }
        }
        runBlocking {
            response.bodyAsText() shouldBe """
                {"errors":[{"message":"Actors above 30 don't have nicknames","locations":[{"line":9,"column":1}],"path":["persons",1,"favouriteMovie","actors",0,"nickname"],"extensions":{"type":"INTERNAL_SERVER_ERROR"}}],"data":{"persons":null}}
            """.trimIndent()
            response.contentType() shouldBe ContentType.Application.Json
        }
    }

    @Test
    fun `route wrapping should work`() {
        val server = withServer(authHeader = "Basic foo") {
            query("actor") {
                resolver { -> Actor("George", 23) }
            }
        }

        val response = server("query") {
            field("actor") {
                field("nickname")
            }
        }
        runBlocking {
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `should work with error handler`() {
        val errorHandler: (Throwable) -> GraphQLError = { e ->
            RequestError(
                message = e.message ?: "unknown",
                node = null,
                extensions = mapOf("type" to BuiltInErrorCodes.INTERNAL_SERVER_ERROR.name)
            )
        }

        val server = withServer(errorHandler = errorHandler) {
            query("error") {
                resolver<String> { throw Exception("Error message") }
            }
        }

        val response = server("query") {
            field("error")
        }
        runBlocking {
            response.bodyAsText() shouldBe "{\"errors\":[{\"message\":\"Error message\",\"locations\":[{\"line\":2,\"column\":1}],\"path\":[\"error\"],\"extensions\":{\"type\":\"INTERNAL_SERVER_ERROR\"}}],\"data\":null}"
            response.contentType() shouldBe ContentType.Application.Json
        }
    }

    @Test
    fun `should work without error handler`() {
        val server = withServer {
            query("error") {
                resolver<String?> { throw Exception("Error message") }
            }
        }

        val response = server("query") {
            field("error")
        }
        runBlocking {
            response.bodyAsText() shouldBe "{\"errors\":[{\"message\":\"Error message\",\"locations\":[{\"line\":2,\"column\":1}],\"path\":[\"error\"],\"extensions\":{\"type\":\"INTERNAL_SERVER_ERROR\"}}],\"data\":{\"error\":null}}"
            response.contentType() shouldBe ContentType.Application.Json
        }
    }

    @Test
    fun `should work without error handler and wrap errors`() {
        val server = withServer(wrapErrors = false) {
            query("error") {
                resolver<String> { throw Exception("Error message") }
            }
        }

        val response = server("query") {
            field("error")
        }
        runBlocking {
            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }
}
