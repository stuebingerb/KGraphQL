package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import nidomiro.kdataloader.ExecutionResult
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.concurrent.atomic.AtomicInteger

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
        val treeChild: AtomicProperty = AtomicProperty(),
        val payments: AtomicProperty = AtomicProperty()
    )

    sealed class Payment {
        data class CreditCard(val number: String, val expiryDate: String) : Payment()
        data class PayPal(val email: String) : Payment()
    }

    data class Wallet(val id: String)

    fun schema(block: SchemaBuilder.() -> Unit = {}): Pair<DefaultSchema, AtomicCounters> {
        val counters = AtomicCounters()

        val schema = defaultSchema {
            configure {
                useDefaultPrettyPrinter = true
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

            query("singleTree") {
                resolver { -> Tree(1, "Fisk") }
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

            type<Wallet> {
                dataProperty<String, Payment?>("payment") {
                    loader { keys ->
                        println("== Running [payment] loader with keys: $keys ==")
                        counters.payments.loader.incrementAndGet()
                        keys.map { id ->
                            ExecutionResult.Success(
                                when (id) {
                                    "1" -> Payment.CreditCard("12345", "2025-12-31")
                                    "2" -> Payment.PayPal("paypal@example.com")
                                    else -> null
                                }
                            )
                        }
                    }

                    prepare { parent ->
                        counters.payments.prepare.incrementAndGet()
                        parent.id
                    }
                }
            }
            query("wallets") {
                resolver { ->
                    listOf(
                        Wallet("1"),
                        Wallet("2"),
                        Wallet("3")
                    )
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
                    val result = schema.executeBlocking(query).deserialize()

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

    @Test
    fun `nested array loaders`() {
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

    @Test
    fun `old basic resolvers in new executor`() {
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

    @Test
    fun `very basic new level executor`() {
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

    @Test
    fun `data loader with nullable prepare keys`() {
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

    @Test
    fun `basic data loader test`() {
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

    @Test
    fun `basic data loader with single tree`() {
        val (schema, counters) = schema()

        val query = """
            {
                singleTree {
                    id
                    firstChild: child(buzz: 3) {
                        id
                        value
                    }
                    otherChild: child(buzz: 6) {
                        id
                        value
                    }
                }
            }
        """.trimIndent()

        val result = schema.executeBlocking(query).deserialize()
        result.extract<Int>("data/singleTree/id") shouldBe 1
        result.extract<Int>("data/singleTree/firstChild/id") shouldBe 14
        result.extract<Int>("data/singleTree/otherChild/id") shouldBe 17

        counters.treeChild.prepare.get() shouldBe 2
        counters.treeChild.loader.get() shouldBe 1
    }

    @Test
    fun `basic data loader with multiple trees`() {
        val (schema, counters) = schema()

        val query = """
            {
                tree { # <-- 2
                    id
                    firstChild: child(buzz: 3) {
                        id
                        value
                    }
                    otherChild: child(buzz: 6) {
                        id
                        value
                    }
                }
            }
        """.trimIndent()

        val result = schema.executeBlocking(query).deserialize()
        result.extract<Int>("data/tree[0]/id") shouldBe 1
        result.extract<Int>("data/tree[0]/firstChild/id") shouldBe 14
        result.extract<Int>("data/tree[0]/otherChild/id") shouldBe 17
        result.extract<Int>("data/tree[1]/id") shouldBe 2
        result.extract<Int>("data/tree[1]/firstChild/id") shouldBe 15
        result.extract<Int>("data/tree[1]/otherChild/id") shouldBe 18

        counters.treeChild.prepare.get() shouldBe 4
        counters.treeChild.loader.get() shouldBe 1
    }

    @Test
    fun `data loader cache per request only`() {
        val (schema, counters) = schema()

        val query = """
            {
                first: tree { id, child(buzz: 3) { id, value } }
                second: tree { id, child(buzz: 3) { id, value } }
            }
        """.trimIndent()

        var result = schema.executeBlocking(query).deserialize()
        result.extract<Int>("data/first[0]/id") shouldBe 1
        result.extract<Int>("data/first[0]/child/id") shouldBe 14
        result.extract<Int>("data/first[1]/id") shouldBe 2
        result.extract<Int>("data/first[1]/child/id") shouldBe 15
        result.extract<Int>("data/second[0]/id") shouldBe 1
        result.extract<Int>("data/second[0]/child/id") shouldBe 14
        result.extract<Int>("data/second[1]/id") shouldBe 2
        result.extract<Int>("data/second[1]/child/id") shouldBe 15

        counters.treeChild.prepare.get() shouldBe 4
        counters.treeChild.loader.get() shouldBe 1

        // New request should load again
        result = schema.executeBlocking(query).deserialize()
        result.extract<Int>("data/first[0]/id") shouldBe 1
        result.extract<Int>("data/first[0]/child/id") shouldBe 14
        result.extract<Int>("data/first[1]/id") shouldBe 2
        result.extract<Int>("data/first[1]/child/id") shouldBe 15
        result.extract<Int>("data/second[0]/id") shouldBe 1
        result.extract<Int>("data/second[0]/child/id") shouldBe 14
        result.extract<Int>("data/second[1]/id") shouldBe 2
        result.extract<Int>("data/second[1]/child/id") shouldBe 15

        counters.treeChild.prepare.get() shouldBe 8
        counters.treeChild.loader.get() shouldBe 2
    }

    @Test
    fun `multiple layers of data loaders`() {
        val (schema, counters) = schema()

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

        val result = schema.executeBlocking(query).deserialize()

        val firstLevel = result.extract<List<Map<String, Any?>>>("data/abc")
        firstLevel shouldHaveSize 3
        firstLevel.map { it["value"] as String } shouldContainExactly listOf("Testing 1", "Testing 2", "Testing 3")

        val leafNodes = (0..2).flatMap { firstLevel ->
            (0..6).flatMap { secondLevel ->
                (0..6).flatMap { thirdLevel ->
                    (0..6).flatMap { fourthLevel ->
                        (0..6).flatMap { fifthLevel ->
                            (0..6).map { sixthLevel ->
                                result.extract<Map<String, Any>>("data/abc[$firstLevel]/children[$secondLevel]/children[$thirdLevel]/children[$fourthLevel]/children[$fifthLevel]/children[$sixthLevel]")
                            }
                        }
                    }
                }
            }
        }
        leafNodes shouldHaveSize 50421

        counters.abcB.prepare.get() shouldBe 58824
        counters.abcB.loader.get() shouldBe 6

        counters.abcChildren.prepare.get() shouldBe 8403
        counters.abcChildren.loader.get() shouldBe 5
    }

    @Test
    fun `data loaders should support unions and fragments`() {
        val (schema, counters) = schema()
        val query = """
            {
                wallets {
                    id
                    payment {
                        __typename
                        ...paymentDetails
                    }
                }
            }
            
            fragment paymentDetails on Payment {
                ... on CreditCard { number expiryDate }
                ... on PayPal { email }
            }
        """.trimIndent()

        schema.executeBlocking(query) shouldBe """
            {
              "data" : {
                "wallets" : [ {
                  "id" : "1",
                  "payment" : {
                    "__typename" : "CreditCard",
                    "number" : "12345",
                    "expiryDate" : "2025-12-31"
                  }
                }, {
                  "id" : "2",
                  "payment" : {
                    "__typename" : "PayPal",
                    "email" : "paypal@example.com"
                  }
                }, {
                  "id" : "3",
                  "payment" : null
                } ]
              }
            }
        """.trimIndent()

        counters.payments.prepare.get() shouldBe 3
        counters.payments.loader.get() shouldBe 1
    }
}
