package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ParallelExecutionTest {

    data class AType(val id: Int)

    private fun suspendResolversSchema(dispatcher: CoroutineDispatcher) = KGraphQL.schema {
        configure {
            coroutineDispatcher = dispatcher
        }
        repeat(1000) {
            query("automated_$it") {
                resolver { ->
                    delay(3)
                    "$it"
                }
            }
        }
    }

    private val syncResolversSchema = KGraphQL.schema {
        repeat(1000) {
            query("automated_$it") {
                resolver { ->
                    Thread.sleep(3)
                    "$it"
                }
            }
        }
    }

    private fun suspendPropertySchema(dispatcher: CoroutineDispatcher) = KGraphQL.schema {
        configure {
            coroutineDispatcher = dispatcher
        }
        query("getAll") {
            resolver { -> (0..999).map { AType(it) } }
        }
        type<AType> {
            property("children") {
                resolver { parent ->
                    (0..50).map {
                        delay(Random.nextLong(1, 100))
                        AType((parent.id * 10) + it)
                    }
                }
            }
        }
    }

    private val query = "{\n" + (0..999).joinToString("") { "automated_${it}\n" } + " }"

    @Test
    fun `suspendable property resolvers`() = runTest {
        val query = "{getAll{id,children{id}}}"
        val schema = suspendPropertySchema(this@runTest.coroutineContext[CoroutineDispatcher]!!)
        val map = deserialize(schema.execute(query))

        map.extract<Int>("data/getAll[0]/id") shouldBe 0
        map.extract<Int>("data/getAll[500]/id") shouldBe 500
        map.extract<Int>("data/getAll[766]/id") shouldBe 766

        map.extract<Int>("data/getAll[5]/children[5]/id") shouldBe 55
        map.extract<Int>("data/getAll[75]/children[9]/id") shouldBe 759
        map.extract<Int>("data/getAll[888]/children[50]/id") shouldBe 8930
    }

    @Test
    fun `1000 synchronous resolvers sleeping with Thread sleep`() = runTest {
        val map = deserialize(syncResolversSchema.execute(query))
        map.extract<String>("data/automated_0") shouldBe "0"
        map.extract<String>("data/automated_271") shouldBe "271"
        map.extract<String>("data/automated_314") shouldBe "314"
        map.extract<String>("data/automated_500") shouldBe "500"
        map.extract<String>("data/automated_999") shouldBe "999"
    }

    @Test
    fun `1000 suspending resolvers sleeping with suspending delay`() = runTest {
        val schema = suspendResolversSchema(this@runTest.coroutineContext[CoroutineDispatcher]!!)
        val map = deserialize(schema.execute(query))
        map.extract<String>("data/automated_0") shouldBe "0"
        map.extract<String>("data/automated_271") shouldBe "271"
        map.extract<String>("data/automated_314") shouldBe "314"
        map.extract<String>("data/automated_500") shouldBe "500"
        map.extract<String>("data/automated_999") shouldBe "999"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `execution should run in parallel`() = runTest {
        val schema = suspendResolversSchema(this@runTest.coroutineContext[CoroutineDispatcher]!!)
        deserialize(schema.execute(query))
        currentTime shouldBe 3
    }
}
