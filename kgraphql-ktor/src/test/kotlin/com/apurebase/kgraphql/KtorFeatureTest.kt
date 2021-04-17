package com.apurebase.kgraphql

import io.ktor.application.*
import io.ktor.auth.*
import kotlinx.serialization.json.*
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class KtorFeatureTest : KtorTest() {

    data class User(val id: Int = -1, val name: String = "") : Principal

    @Test
    fun `Simple query test`() {
        val server = withServer {
            query("hello") {
                resolver { -> "World!" }
            }
        }

        server("query") {
            field("hello")
        } shouldBeEqualTo "{\"data\":{\"hello\":\"World!\"}}"

    }

    @Test
    fun `Simple mutation test`() {
        val server = withServer {
            mutation("hello") {
                resolver { -> "World! mutation" }
            }
        }

        server("mutation") {
            field("hello")
        } shouldBeEqualTo "{\"data\":{\"hello\":\"World! mutation\"}}"

    }

    data class Actor(val name: String, val age: Int)
    data class UserData(val username: String, val stuff: String)

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
                property<String>("nickname") {
                    resolver { _: Actor, ctx: Context -> "Hodor and ${ctx.get<UserData>()?.username}" }
                }

                transformation(Actor::name) { name: String, addStuff: Boolean?, ctx: Context ->
                    if (addStuff == true) name + ctx[UserData::class]?.stuff else name
                }
            }
        }

        server("query") {
            field("actor") {
                field("name(addStuff: true)")
            }
        } shouldBeEqualTo "{\"data\":{\"actor\":{\"name\":\"${georgeName}STUFF\"}}}"

        server("query") {
            field("actor") {
                field("nickname")
            }
        } shouldBeEqualTo "{\"data\":{\"actor\":{\"nickname\":\"Hodor and $georgeName\"}}}"
    }

    enum class MockEnum { M1, M2 }

    data class InputOne(val enum: MockEnum, val id: String)

    data class InputTwo(val one: InputOne, val quantity: Int, val tokens: List<String>)

    @Test
    fun `Simple variables test`() {
        val server = withServer {
            enum<MockEnum>()
            inputType<InputTwo>()
            query("test") { resolver { input: InputTwo -> "success: $input" } }
        }

        server("query") {
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
        } shouldBeEqualTo "{\"data\":{\"test\":\"success: InputTwo(one=InputOne(enum=M1, id=M1), quantity=3434, tokens=[23, 34, 21, 434])\"}}"
    }
}
