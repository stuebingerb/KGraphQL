package de.stuebingerb.kgraphql.specification.language

import de.stuebingerb.kgraphql.Actor
import de.stuebingerb.kgraphql.KGraphQL.Companion.schema
import de.stuebingerb.kgraphql.Specification
import de.stuebingerb.kgraphql.defaultSchema
import de.stuebingerb.kgraphql.deserialize
import de.stuebingerb.kgraphql.executeEqualQueries
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

@Specification("2.6 Arguments")
class ArgumentsSpecificationTest {
    val age = 432

    val schema = defaultSchema {
        query("actor") {
            resolver { -> Actor("Boguś Linda", age) }
        }

        type<Actor> {
            property("favDishes") {
                resolver { _: Actor, size: Int, prefix: String? ->
                    listOf("steak", "burger", "soup", "salad", "bread", "bird").let { dishes ->
                        if (prefix != null) {
                            dishes.filter { it.startsWith(prefix) }
                        } else {
                            dishes
                        }
                    }.take(size)
                }
            }
            property("none") {
                resolver { actor -> actor.age }
            }
            property("one") {
                resolver { actor, one: Int -> actor.age + one }
            }
            property("two") {
                resolver { actor, one: Int, two: Int -> actor.age + one + two }
            }
            property("three") {
                resolver { actor, one: Int, two: Int, three: Int ->
                    actor.age + one + two + three
                }
            }
            property("four") {
                resolver { actor, one: Int, two: Int, three: Int, four: Int ->
                    actor.age + one + two + three + four
                }
            }
            property("five") {
                resolver { actor, one: Int, two: Int, three: Int, four: Int, five: Int ->
                    actor.age + one + two + three + four + five
                }
            }
        }
    }

    @Test
    fun `arguments are unordered`() {
        executeEqualQueries(
            schema,
            mapOf("data" to mapOf("actor" to mapOf("favDishes" to listOf("burger", "bread")))),
            "{actor{favDishes(size: 2, prefix: \"b\")}}",
            "{actor{favDishes(prefix: \"b\", size: 2)}}"
        )
    }

    @Test
    fun `many arguments can exist on given field`() {
        val response = deserialize(schema.executeBlocking("{actor{favDishes(size: 2, prefix: \"b\")}}"))
        response shouldBe mapOf<String, Any>(
            "data" to mapOf(
                "actor" to mapOf(
                    "favDishes" to listOf(
                        "burger",
                        "bread"
                    )
                )
            )
        )
    }

    @Test
    fun `all arguments to suspendResolvers`() {
        val request = """
            {
                actor {
                    none
                    one(one: 1)
                    two(one: 2, two: 3)
                    three(one: 4, two: 5, three: 6)
                    four(one: 7, two: 8, three: 9, four: 10)
                    five(one: 11, two: 12, three: 13, four: 14, five: 15)
                }
            }
        """.trimIndent()
        val response = deserialize(schema.executeBlocking(request))
        response shouldBe
            mapOf<String, Any>(
                "data" to mapOf(
                    "actor" to mapOf(
                        "none" to age,
                        "one" to age + 1,
                        "two" to age + 2 + 3,
                        "three" to age + 4 + 5 + 6,
                        "four" to age + 7 + 8 + 9 + 10,
                        "five" to age + 11 + 12 + 13 + 14 + 15
                    )
                )
            )
    }

    @Test
    fun `property arguments should accept default values`() {
        val schema = defaultSchema {
            query("actor") {
                resolver {
                    ->
                    Actor("John Doe", age)
                }
            }

            type<Actor> {
                property("greeting") {
                    resolver { actor: Actor, suffix: String ->
                        "$suffix, ${actor.name}!"
                    }.withArgs {
                        arg<String> { name = "suffix"; defaultValue = "Hello" }
                    }
                }
            }
        }

        val request = """
            {
                actor {
                    greeting
                }
            }
        """.trimIndent()

        val response = deserialize(schema.executeBlocking(request))
        response shouldBe
            mapOf<String, Any>(
                "data" to mapOf<String, Any>(
                    "actor" to mapOf<String, Any>(
                        "greeting" to "Hello, John Doe!"
                    )
                )
            )
    }

    data class Agenda(
        val date: LocalDate,
        val slots: List<Slots>,
        val hasSlotsAvailable: Boolean
    )

    data class Slots(
        val hour: Int
    )

    // https://github.com/aPureBase/KGraphQL/issues/144
    @Test
    fun `arguments with lists should preserve generic type`() {
        val schema = schema {
            stringScalar<LocalDate> {
                serialize = { date -> date.toString() }
                deserialize = { dateString -> LocalDate.parse(dateString) }
            }

            query("slots") {
                resolver { limit: Int, tags: List<String> ->
                    listOf(Agenda(date = LocalDate.of(2025, 6, 19), slots = listOf(Slots(1)), hasSlotsAvailable = true))
                }.withArgs {
                    arg<Int> { name = "limit"; defaultValue = 7 }
                    arg<List<String>> { name = "tags"; defaultValue = emptyList() }
                }
            }
        }

        val result = schema.executeBlocking(
            """
            {
                slots {
                    date
                    hasSlotsAvailable
                    slots {
                        hour
                    }
                }
            }
            """.trimIndent()
        )

        result shouldBe """
            {"data":{"slots":[{"date":"2025-06-19","hasSlotsAvailable":true,"slots":[{"hour":1}]}]}}
        """.trimIndent()
    }
}
