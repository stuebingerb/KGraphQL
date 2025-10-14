package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import nidomiro.kdataloader.ExecutionResult
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong

class DataLoaderExecutionTest {

    private val fake = FakeComplicatedDataLoad()

    data class Item(val id: Int)
    data class ItemValue(val itemId: Int, val value: String)

    val schema = defaultSchema {
        query("items") {
            resolver { amount: Int? ->
                delay(Random.nextLong(50..250L))
                (1..(amount ?: 1_000)).map(::Item)
            }
        }

        type<Item> {
            dataProperty<Int, String>("name") {
                prepare { it.id }
                loader { ids ->
                    coroutineScope {
                        delay(Random.nextLong(1..15L))
                        ids.map { ExecutionResult.Success("Name-$it") }
                    }
                }
            }
            dataProperty<Int, List<ItemValue>>("values") {
                prepare { it.id }
                loader { ids ->
                    coroutineScope {
                        val start = Random.nextInt(1..1_000_000_000)
                        val delay = Random.nextLong(1..3L)
                        ids.map { id ->
                            ExecutionResult.Success((start..(start + 3)).map {
                                ItemValue(
                                    itemId = id,
                                    value = fake.loadValue("delay:$delay,start:$start", delay),
                                )
                            })
                        }
                    }
                }
            }
        }
        type<ItemValue> {
            dataProperty<Int, Item>("parent") {
                prepare { it.itemId }
                loader { ids ->
                    delay(5)
                    ids.map {
                        ExecutionResult.Success(Item(it))
                    }
                }
            }
        }
    }

    @Test
    fun `stress test with dataloaders and custom supervisor jobs`() = runTest {
        val result = deserialize(
            schema.execute(
                """
                 {
                     data1: items(amount: 250) { ...Fields }
                     data2: items(amount: 200) { ...Fields }
                     data3: items { ...Fields }
                 }
                 
                 fragment Fields on Item {
                     id
                     name
                     values {
                         parent {
                             values1: values { itemId, value }
                             values2: values { itemId, value }
                             values3: values { itemId, value }
                             values4: values { itemId, value }
                         }
                     }
                 }
                 """.trimIndent()
            )
        )

        result.extract<List<*>>("data/data1") shouldHaveSize 250
        result.extract<List<*>>("data/data2") shouldHaveSize 200
        result.extract<List<*>>("data/data3") shouldHaveSize 1000
    }
}
