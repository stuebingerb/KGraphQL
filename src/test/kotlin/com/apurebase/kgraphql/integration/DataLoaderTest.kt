package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import org.amshove.kluent.shouldEqual
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.RepeatedTest
import java.time.Duration.ofSeconds
import java.util.concurrent.atomic.AtomicInteger


// This is just for safety, so when the tests fail and
// end up in an endless waiting state, they'll fail after this amount
val timeout = ofSeconds(6)!!
const val repeatTimes = 1

//@Disabled("Not supported yet!")
class DataLoaderTest {

    data class Person(val id: Int, val firstName: String, val lastName: String)

    val jogvan = Person(1, "Jógvan", "Olsen")
    val beinisson = Person(2, "Høgni", "Beinisson")
    val juul = Person(3, "Høgni", "Juul")
    val jarad = Person(4, "Jarad", "??")

    val colleagues = mapOf(
        jogvan.id to listOf(beinisson, juul),
        beinisson.id to listOf(jogvan, juul, jarad),
        juul.id to listOf(beinisson, jogvan),
        jarad.id to listOf(beinisson)
    )

    val boss = mapOf(
        jogvan.id to juul,
        juul.id to beinisson,
        beinisson.id to jarad
    )

    data class Tree(val id: Int, val value: String)

    data class ABC(val value: String)

    data class AtomicProperty(
        val loader: AtomicInteger = AtomicInteger(),
        val prepare: AtomicInteger = AtomicInteger()
    )

    data class AtomicCounters(
        val abcB: AtomicProperty = AtomicProperty(),
        val abcChildren: AtomicProperty = AtomicProperty(),
        val treeChild: AtomicProperty = AtomicProperty()
    )


    fun schema(
        block: SchemaBuilder<Unit>.() -> Unit = {}
    ): Pair<DefaultSchema, AtomicCounters> {
        val counters = AtomicCounters()

        val schema = defaultSchema {
            configure { useDefaultPrettyPrinter = true }

            query("people") {
                resolver { -> listOf(jogvan, beinisson, juul, jarad) }
            }

            type<Person> {
                property<String>("fullName") {
                    resolver { "${it.firstName} ${it.lastName}" }
                }

                dataProperty<Int, Person?>("respondsTo") {
                    setReturnType { jogvan as Person? }
                    prepare { it.id }
                    loader { keys ->
                        println("== Running [respondsTo] loader with keys: $keys ==")
                        keys.map { it to boss[it] }.toMap()
                    }
                }
                dataProperty<Int, List<Person>>("colleagues") {
                    setReturnType { listOf() }
                    prepare { it.id }
                    loader { keys ->
                        println("== Running [colleagues] loader with keys: $keys ==")
                        keys.map { it to (colleagues[it] ?: listOf()) }.toMap()
                    }
                }
            }

            query("tree") {
                resolver { ->
                    listOf(
                        Tree(1, "Fisk"),
                        Tree(2, "Fisk!")
                    )
                }
            }

            query("abc") {
                resolver { ->
                    (1..3).map { ABC("Testing $it") }
                }
            }

            type<ABC> {

                dataProperty<String, Int>("B") {
                    setReturnType { 25 }
                    loader { keys ->
                        println("== Running [B] loader with keys: $keys ==")
                        counters.abcB.loader.incrementAndGet()
                        keys.map {
                            it to it.map(Char::toInt).fold(0) { a, b -> a + b }
                        }.toMap()
                    }
                    prepare { parent: ABC ->
                        counters.abcB.prepare.incrementAndGet()
                        parent.value
                    }
                }

                dataProperty<String, List<ABC>>("children") {
                    setReturnType { listOf() }
                    loader { keys ->
                        println("== Running [children] loader with keys: $keys ==")
                        counters.abcChildren.loader.incrementAndGet()
                        keys.map {
                            val (a1, a2) = when (it) {
                                "Testing 1" -> "Hello" to "World"
                                "Testing 2" -> "Fizz" to "Buzz"
                                "Testing 3" -> "Jógvan" to "Høgni"
                                else -> "${it}Nest-0" to "${it}Nest-1"
                            }
                            it to listOf(ABC(a1), ABC(a2))
                        }.toMap()
                    }
                    prepare { parent ->
                        counters.abcChildren.prepare.incrementAndGet()
                        parent.value
                    }
                }
            }


            type<Tree> {
                dataProperty<Int, Tree>("child") {
                    setReturnType { Tree(0, "") }
                    loader { keys ->
                        println("== Running [child] loader with keys: $keys ==")
                        counters.treeChild.loader.incrementAndGet()
                        keys.map { num -> num to Tree(10 + num, "Fisk - $num") }.toMap()
                    }

                    prepare { parent, buzz: Int ->
                        counters.treeChild.prepare.incrementAndGet()
                        parent.id + buzz
                    }
                }
            }

            block(this)
        }

        return schema to counters
    }

    @RepeatedTest(repeatTimes)
    fun `Basic dataloader test`() {
        assertTimeoutPreemptively(timeout) {
            val (schema) = schema()

            val query = """
                {
                    people {
                        ...PersonInfo
                        respondsTo { ...PersonInfo }
                        colleagues { ...PersonInfo }
                    }
                }
                fragment PersonInfo on Person {
                    id
                    fullName
                }
            """.trimIndent()

            val result = deserialize(schema.executeBlocking(query))

            result.extract<String>("data/people[0]/respondsTo/fullName") shouldEqual "${juul.firstName} ${juul.lastName}"
            result.extract<String>("data/people[1]/colleagues[0]/fullName") shouldEqual "${jogvan.firstName} ${jogvan.lastName}"
        }
    }

    @RepeatedTest(repeatTimes)
    fun `basic data loader`() {
        assertTimeoutPreemptively(timeout) {
            val (schema, counters) = schema()

            val query = """
                {
                    tree {
                        id
                        child(buzz: 3) {
                            id
                            value
                        }
                    }
                }
            """.trimIndent()

            val result = deserialize(schema.executeBlocking(query))
            counters.treeChild.prepare.get() shouldEqual 2
            counters.treeChild.loader.get() shouldEqual 1

            result.extract<Int>("data/tree[1]/id") shouldEqual 2
            result.extract<Int>("data/tree[0]/child/id") shouldEqual 14
            result.extract<Int>("data/tree[1]/child/id") shouldEqual 15
        }
    }

    @RepeatedTest(repeatTimes)
    fun `data loader cache per request only`() {
        assertTimeoutPreemptively(timeout) {
            val (schema, counters) = schema()

            val query = """
                {
                    first: tree { id, child(buzz: 3) { id, value } }
                    second: tree { id, child(buzz: 3) { id, value } }
                }
            """.trimIndent()

            val result = schema.executeBlocking(query).deserialize()

            counters.treeChild.prepare.get() shouldEqual 4
             counters.treeChild.loader.get() shouldEqual 1

            result.extract<Int>("data/first[1]/id") shouldEqual 2
            result.extract<Int>("data/first[0]/child/id") shouldEqual 14
            result.extract<Int>("data/first[1]/child/id") shouldEqual 15
            result.extract<Int>("data/second[1]/child/id") shouldEqual 15

        }
    }

    @RepeatedTest(repeatTimes)
    fun `multiple layers of dataLoaders`() {
        assertTimeoutPreemptively(timeout) {
            val (schema) = schema()

            val query = """
            {
                abc {
                    value
                    B
                    children {
                        value
                        B
                        children {
                            value
                            B
                        }
                    }
                }
            }
            """.trimIndent()

            val result = schema.executeBlocking(query)

            println(result)
//            throw TODO("Assert results")
        }
    }
}
