package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class ParallelExecutionTest {

    data class AType(val id: Int)

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

    private val suspendResolverSchema = KGraphQL.schema {
        repeat(1000) {
            query("automated_$it") {
                resolver { ->
                    delay(3)
                    "$it"
                }
            }
        }
    }

    private val suspendPropertySchema = KGraphQL.schema {
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
    suspend fun `suspendable property resolvers`() {
        val query = "{getAll{id,children{id}}}"
        val map = deserialize(suspendPropertySchema.execute(query))

        map.extract<Int>("data/getAll[0]/id") shouldBe 0
        map.extract<Int>("data/getAll[500]/id") shouldBe 500
        map.extract<Int>("data/getAll[766]/id") shouldBe 766

        map.extract<Int>("data/getAll[5]/children[5]/id") shouldBe 55
        map.extract<Int>("data/getAll[75]/children[9]/id") shouldBe 759
        map.extract<Int>("data/getAll[888]/children[50]/id") shouldBe 8930
    }

    @Test
    suspend fun `1000 synchronous resolvers sleeping with Thread sleep`() {
        val map = deserialize(syncResolversSchema.execute(query))
        map.extract<String>("data/automated_0") shouldBe "0"
        map.extract<String>("data/automated_271") shouldBe "271"
        map.extract<String>("data/automated_314") shouldBe "314"
        map.extract<String>("data/automated_500") shouldBe "500"
        map.extract<String>("data/automated_999") shouldBe "999"
    }

    @Test
    suspend fun `1000 suspending resolvers sleeping with suspending delay`() {
        val map = deserialize(suspendResolverSchema.execute(query))
        map.extract<String>("data/automated_0") shouldBe "0"
        map.extract<String>("data/automated_271") shouldBe "271"
        map.extract<String>("data/automated_314") shouldBe "314"
        map.extract<String>("data/automated_500") shouldBe "500"
        map.extract<String>("data/automated_999") shouldBe "999"
    }

    @Test
    suspend fun `execution should run in parallel`() {
        val duration = measureTimeMillis {
            deserialize(syncResolversSchema.execute(query))
        }
        // syncResolversSchema has 1000 resolvers, each waiting for 3ms. Usually, execution
        // takes about 300ms so if it takes 3s, we apparently ran sequentially.
        duration shouldBeLessThan 3000
    }
}
