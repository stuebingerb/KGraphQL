package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.apurebase.kgraphql.schema.execution.Executor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import nidomiro.kdataloader.ExecutionResult
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.TestFactory
import java.time.Duration
import java.time.Duration.ofSeconds
import java.util.concurrent.atomic.AtomicInteger

// This is just for safety, so when the tests fail and
// end up in an endless waiting state, they'll fail after this amount
val timeout: Duration = ofSeconds(60)
const val repeatTimes = 2

class DataLoaderTest {

    data class Person(val id: Int, val firstName: String, val lastName: String)

    private val jogvan = Person(1, "Jógvan", "Olsen")
    private val beinisson = Person(2, "Høgni", "Beinisson")
    private val juul = Person(3, "Høgni", "Juul")
    private val otherOne = Person(4, "The other one", "??")
    private val allPeople = listOf(jogvan, beinisson, juul, otherOne)

    private val colleagues = mapOf(
        jogvan.id to listOf(beinisson, juul),
        beinisson.id to listOf(jogvan, juul, otherOne),
        juul.id to listOf(beinisson, jogvan),
        otherOne.id to listOf(beinisson)
    )

    private val boss = mapOf(
        jogvan.id to juul,
        juul.id to beinisson,
        beinisson.id to otherOne
    )

    data class Tree(val id: Int, val value: String)

    data class ABC(val value: String, val personId: Int? = null)

    data class AtomicProperty(
        val loader: AtomicInteger = AtomicInteger(),
        val prepare: AtomicInteger = AtomicInteger()
    )

    data class AtomicCounters(
        val abcB: AtomicProperty = AtomicProperty(),
        val abcChildren: AtomicProperty = AtomicProperty(),
        val treeChild: AtomicProperty = AtomicProperty()
    )

    fun schema(block: SchemaBuilder.() -> Unit = {}): Pair<DefaultSchema, AtomicCounters> {
        val counters = AtomicCounters()

        val schema = defaultSchema {
            configure {
                useDefaultPrettyPrinter = true
                executor = Executor.DataLoaderPrepared
            }

            query("people") {
                resolver { -> allPeople }
            }

            type<Person> {
                property("fullName") {
                    resolver { "${it.firstName} ${it.lastName}" }
                }

                dataProperty<Int, Person?>("respondsTo") {
                    prepare { it.id }
                    loader { keys ->
                        println("== Running [respondsTo] loader with keys: $keys ==")
                        keys.map { ExecutionResult.Success(boss[it]) }
                    }
                }
                dataProperty<Int, List<Person>>("colleagues") {
                    prepare { it.id }
                    loader { keys ->
                        delay(10)
                        println("== Running [colleagues] loader with keys: $keys ==")
                        keys.map { ExecutionResult.Success(colleagues[it] ?: listOf()) }
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
                    (1..3).map {
                        ABC(
                            "Testing $it", if (it == 2) {
                                null
                            } else {
                                it
                            }
                        )
                    }
                }
            }

            type<ABC> {

                dataProperty<String, Int>("B") {
                    loader { keys ->
                        println("== Running [B] loader with keys: $keys ==")
                        counters.abcB.loader.incrementAndGet()
                        keys.map {
                            ExecutionResult.Success(it.map(Char::code).fold(0) { a, b -> a + b })
                        }
                    }
                    prepare { parent: ABC ->
                        counters.abcB.prepare.incrementAndGet()
                        parent.value
                    }
                }

                property("simpleChild") {
                    resolver {
                        delay((1..5L).random())
                        Thread.sleep((1..5L).random())
                        delay((1..5L).random())
                        ABC("NewChild!")
                    }
                }

                dataProperty<Int?, Person?>("person") {
                    prepare { it.personId }
                    loader { personIds ->
                        personIds.map {
                            delay(1)
                            ExecutionResult.Success(
                                if (it == null || it < 1) {
                                    null
                                } else {
                                    allPeople[it - 1]
                                }
                            )
                        }
                    }
                }
                property("personOld") {
                    resolver {
                        delay(1)
                        if (it.personId == null || it.personId < 1) {
                            null
                        } else {
                            allPeople[it.personId - 1]
                        }
                    }
                }

                dataProperty<String, List<ABC>>("children") {
                    loader { keys ->
                        println("== Running [children] loader with keys: $keys ==")
                        counters.abcChildren.loader.incrementAndGet()
                        keys.map { key ->
                            val (a1, a2) = when (key) {
                                "Testing 1" -> "Hello" to "World"
                                "Testing 2" -> "Fizz" to "Buzz"
                                "Testing 3" -> "Jógvan" to "Høgni"
                                else -> "${key}Nest-0" to "${key}Nest-1"
                            }
                            delay(1)
                            ExecutionResult.Success(
                                (1..7).map {
                                    if (it % 2 == 0) {
                                        ABC(a1)
                                    } else {
                                        ABC(a2)
                                    }
                                }
                            )
                        }
                    }
                    prepare { parent ->
                        counters.abcChildren.prepare.incrementAndGet()
                        parent.value
                    }
                }

                property("childrenOld") {
                    resolver { parent ->
                        when (parent.value) {
                            "Testing 1" -> listOf(ABC("Hello"), ABC("World"))
                            "Testing 2" -> listOf(ABC("Fizz"), ABC("Buzz"))
                            "Testing 3" -> listOf(ABC("Jógvan"), ABC("Høgni"))
                            else -> listOf(ABC("${parent.value}Nest-0"), ABC("${parent.value}Nest-1"))
                        }
                    }
                }

            }

            type<Tree> {
                dataProperty<Int, Tree>("child") {
                    loader { keys ->
                        println("== Running [child] loader with keys: $keys ==")
                        counters.treeChild.loader.incrementAndGet()
                        keys.map { num -> ExecutionResult.Success(Tree(10 + num, "Fisk - $num")) }
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

    @TestFactory
    fun `stress test`(): List<DynamicTest> {
        val (schema) = schema()
        val fn: (Boolean) -> List<DynamicTest> = { withTypeName ->
            val query = """
                {
                    abc {
                        __typename
                        value
                        person: personOld {
                            fullName
                            __typename
                        }
                        simpleChild {
                            __typename
                            value
                            person: personOld {
                                __typename
                                fullName
                            }
                            simpleChild {
                                __typename
                                value
                            }
                        }
                    }
                }
            """.trimIndent().let {
                if (withTypeName) {
                    it
                } else {
                    it.replace("__typename", "")
                }
            }

            val test: (Int) -> DynamicTest = {
                DynamicTest.dynamicTest("test-$it") {
                    val result = schema.executeBlocking(query).also(::println).deserialize()

                    result.extract<String>("data/abc[0]/value") shouldBe "Testing 1"
                    result.extract<String>("data/abc[0]/person/fullName") shouldBe "Jógvan Olsen"
                    result.extract<String>("data/abc[0]/simpleChild/value") shouldBe "NewChild!"
                    result.extract<String?>("data/abc[0]/simpleChild/person") shouldBe null
                    result.extract<String?>("data/abc[0]/simpleChild/simpleChild/value") shouldBe "NewChild!"

                    result.extract<String>("data/abc[1]/value") shouldBe "Testing 2"
                    result.extract<String?>("data/abc[1]/person") shouldBe null
                    result.extract<String>("data/abc[1]/simpleChild/value") shouldBe "NewChild!"
                    result.extract<String?>("data/abc[1]/simpleChild/person") shouldBe null
                    result.extract<String?>("data/abc[1]/simpleChild/simpleChild/value") shouldBe "NewChild!"

                    result.extract<String>("data/abc[2]/value") shouldBe "Testing 3"
                    result.extract<String?>("data/abc[2]/person/fullName") shouldBe "Høgni Juul"
                    result.extract<String>("data/abc[2]/simpleChild/value") shouldBe "NewChild!"
                    result.extract<String?>("data/abc[2]/simpleChild/person") shouldBe null
                    result.extract<String?>("data/abc[2]/simpleChild/simpleChild/value") shouldBe "NewChild!"

                    if (withTypeName) {
                        result.extract<String>("data/abc[0]/__typename") shouldBe "ABC"
                        result.extract<String>("data/abc[0]/person/__typename") shouldBe "Person"
                        result.extract<String>("data/abc[0]/simpleChild/__typename") shouldBe "ABC"
                        result.extract<String>("data/abc[1]/__typename") shouldBe "ABC"
                        result.extract<String>("data/abc[1]/simpleChild/__typename") shouldBe "ABC"
                        result.extract<String>("data/abc[2]/__typename") shouldBe "ABC"
                        result.extract<String>("data/abc[2]/simpleChild/__typename") shouldBe "ABC"
                    }
                }
            }

            (1..100).chunked(25).flatMap { chunk ->
                runBlocking {
                    coroutineScope {
                        chunk.map {
                            async { test(it) }
                        }.awaitAll()
                    }
                }
            }
        }

        return fn(true) + fn(false)
    }

    @RepeatedTest(repeatTimes)
    fun `nested array loaders`() {
        assertTimeoutPreemptively(timeout) {
            val (schema) = schema()
            val query = """
                {
                    people {
                        fullName
                        colleagues {
                            fullName
                            respondsTo {
                                fullName
                            }
                        }
                    }
                }                
            """.trimIndent()

            val result = schema.executeBlocking(query).deserialize()
            result.extract<String>("data/people[0]/fullName") shouldBe "${jogvan.firstName} ${jogvan.lastName}"
            result.extract<String>("data/people[0]/colleagues[0]/fullName") shouldBe "${beinisson.firstName} ${beinisson.lastName}"
            result.extract<String>("data/people[0]/colleagues[0]/respondsTo/fullName") shouldBe "${otherOne.firstName} ${otherOne.lastName}"
            result.extract<String>("data/people[0]/colleagues[1]/fullName") shouldBe "${juul.firstName} ${juul.lastName}"
            result.extract<String>("data/people[0]/colleagues[1]/respondsTo/fullName") shouldBe "${beinisson.firstName} ${beinisson.lastName}"

            result.extract<String>("data/people[1]/fullName") shouldBe "${beinisson.firstName} ${beinisson.lastName}"
            result.extract<String>("data/people[1]/colleagues[0]/fullName") shouldBe "${jogvan.firstName} ${jogvan.lastName}"
            result.extract<String>("data/people[1]/colleagues[0]/respondsTo/fullName") shouldBe "${juul.firstName} ${juul.lastName}"
            result.extract<String>("data/people[1]/colleagues[1]/fullName") shouldBe "${juul.firstName} ${juul.lastName}"
            result.extract<String>("data/people[1]/colleagues[1]/respondsTo/fullName") shouldBe "${beinisson.firstName} ${beinisson.lastName}"
            result.extract<String>("data/people[1]/colleagues[2]/fullName") shouldBe "${otherOne.firstName} ${otherOne.lastName}"
            result.extract<String>("data/people[1]/colleagues[2]/respondsTo") shouldBe null

            result.extract<String>("data/people[2]/fullName") shouldBe "${juul.firstName} ${juul.lastName}"
            result.extract<String>("data/people[2]/colleagues[0]/fullName") shouldBe "${beinisson.firstName} ${beinisson.lastName}"
            result.extract<String>("data/people[2]/colleagues[0]/respondsTo/fullName") shouldBe "${otherOne.firstName} ${otherOne.lastName}"
            result.extract<String>("data/people[2]/colleagues[1]/fullName") shouldBe "${jogvan.firstName} ${jogvan.lastName}"
            result.extract<String>("data/people[2]/colleagues[1]/respondsTo/fullName") shouldBe "${juul.firstName} ${juul.lastName}"

            result.extract<String>("data/people[3]/fullName") shouldBe "${otherOne.firstName} ${otherOne.lastName}"
            result.extract<String>("data/people[3]/colleagues[0]/fullName") shouldBe "${beinisson.firstName} ${beinisson.lastName}"
            result.extract<String>("data/people[3]/colleagues[0]/respondsTo/fullName") shouldBe "${otherOne.firstName} ${otherOne.lastName}"
        }
    }

    @RepeatedTest(repeatTimes)
    fun `old basic resolvers in new executor`() {
        assertTimeoutPreemptively(timeout) {
            val (schema) = schema()
            val query = """
                {
                    abc {
                        value
                        simpleChild {
                            value
                        }
                    }
                }
            """.trimIndent()

            val result = schema.executeBlocking(query).deserialize()
            result.extract<String>("data/abc[0]/simpleChild/value") shouldBe "NewChild!"
        }
    }

    @RepeatedTest(repeatTimes)
    fun `very basic new level executor`() {
        assertTimeoutPreemptively(timeout) {
            val (schema) = schema()

            val query = """
                {
                    people {
                        id
                        fullName
                        respondsTo {
                            fullName
                            respondsTo {
                                fullName
                            }
                        }
                    }
                }
            """.trimIndent()

            val result = schema.executeBlocking(query).deserialize()
            result.extract<Int>("data/people[0]/id") shouldBe jogvan.id
            result.extract<String>("data/people[0]/fullName") shouldBe "${jogvan.firstName} ${jogvan.lastName}"
            result.extract<String>("data/people[0]/respondsTo/fullName") shouldBe "${juul.firstName} ${juul.lastName}"
            result.extract<String>("data/people[0]/respondsTo/respondsTo/fullName") shouldBe "${beinisson.firstName} ${beinisson.lastName}"

            result.extract<Int>("data/people[1]/id") shouldBe beinisson.id
            result.extract<String>("data/people[1]/fullName") shouldBe "${beinisson.firstName} ${beinisson.lastName}"
            result.extract<String>("data/people[1]/respondsTo/fullName") shouldBe "${otherOne.firstName} ${otherOne.lastName}"
            result.extract<String>("data/people[1]/respondsTo/respondsTo") shouldBe null

            result.extract<Int>("data/people[2]/id") shouldBe juul.id
            result.extract<String>("data/people[2]/fullName") shouldBe "${juul.firstName} ${juul.lastName}"
            result.extract<String>("data/people[2]/respondsTo/fullName") shouldBe "${beinisson.firstName} ${beinisson.lastName}"
            result.extract<String>("data/people[2]/respondsTo/respondsTo/fullName") shouldBe "${otherOne.firstName} ${otherOne.lastName}"

            result.extract<Int>("data/people[3]/id") shouldBe otherOne.id
            result.extract<String>("data/people[3]/fullName") shouldBe "${otherOne.firstName} ${otherOne.lastName}"
            result.extract<String>("data/people[3]/respondsTo") shouldBe null
        }
    }

    @RepeatedTest(repeatTimes)
    fun `dataloader with nullable prepare keys`() {
        assertTimeoutPreemptively(timeout) {
            val (schema) = schema()

            val query = """
                {
                    abc {
                        value
                        personId
                        person {
                            id
                            fullName
                        }
                    }
                }
            """.trimIndent()

            val result = schema.executeBlocking(query).deserialize()
            result.extract<String>("data/abc[0]/person/fullName") shouldBe "${jogvan.firstName} ${jogvan.lastName}"
            result.extract<String?>("data/abc[1]/person") shouldBe null
            result.extract<String>("data/abc[2]/person/fullName") shouldBe "${juul.firstName} ${juul.lastName}"
        }
    }

    @RepeatedTest(repeatTimes)
    fun `basic dataloader test`() {
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

            val result = schema.executeBlocking(query).deserialize()
            result.extract<String>("data/people[0]/respondsTo/fullName") shouldBe "${juul.firstName} ${juul.lastName}"
            result.extract<String>("data/people[1]/colleagues[0]/fullName") shouldBe "${jogvan.firstName} ${jogvan.lastName}"
        }
    }

    @RepeatedTest(repeatTimes)
    fun `basic data loader`() {
        assertTimeoutPreemptively(timeout) {
            val (schema, counters) = schema()

            val query = """
                {
                    tree { # <-- 2
                        id
                        child(buzz: 3) {
                            id
                            value
                        }
                    }
                }
            """.trimIndent()

            val result = schema.executeBlocking(query).deserialize()
            result.extract<Int>("data/tree[1]/id") shouldBe 2
            result.extract<Int>("data/tree[0]/child/id") shouldBe 14
            result.extract<Int>("data/tree[1]/child/id") shouldBe 15

            counters.treeChild.prepare.get() shouldBe 2
            counters.treeChild.loader.get() shouldBe 1
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
            result.extract<Int>("data/first[1]/id") shouldBe 2
            result.extract<Int>("data/first[0]/child/id") shouldBe 14
            result.extract<Int>("data/first[1]/child/id") shouldBe 15
            result.extract<Int>("data/second[1]/child/id") shouldBe 15

            counters.treeChild.prepare.get() shouldBe 4
            counters.treeChild.loader.get() shouldBe 1
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
                                children {
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
                        }
                    }
                }
            """.trimIndent()

            schema.executeBlocking(query).also(::println).deserialize()

//            throw TODO("Assert results")
        }
    }
}
