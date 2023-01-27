package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.schema.execution.Executor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nidomiro.kdataloader.ExecutionResult
import org.junit.jupiter.api.RepeatedTest
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong

class DataLoaderExecutionTest {

    private val fake = FakeComplicatedDataLoad()

    data class Item(val id: Int)
    data class ItemValue(val itemId: Int, val value: String)

    val schema = defaultSchema {
        configure {
            executor = Executor.DataLoaderPrepared
            timeout = null
        }

        query("items") {
            resolver { amount: Int? ->
                println("query:items")
                delay(Random.nextLong(50..250L))
                (1..(amount ?: 1_000)).map(::Item)
            }
        }

        type<Item> {
            dataProperty<Int, String>("name") {
                prepare { it.id }
                loader { ids ->
                    println("loader:name ${ids.size}")
                    coroutineScope {
                        delay(Random.nextLong(1..15L))
                        ids.map { ExecutionResult.Success("Name-$it")}
                    }
                }
            }
            dataProperty<Int, List<ItemValue>>("values") {
                prepare { it.id }
                loader { ids ->
                    coroutineScope {
                        val start = Random.nextInt(1..1_000_000_000)
                        val delay = Random.nextLong(1..3L)
                        println("loader:values [size: ${ids.size}, delay: $delay]")
                        ids.map { id ->
                            ExecutionResult.Success((start..(start+3)).map {
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
                    println("loader:parent ${ids.size}")
                    delay(5)
                    ids.map {
                        ExecutionResult.Success(Item(it))
                    }
                }
            }
        }
    }

     @RepeatedTest(50)
     fun Stress_test_with_dataloaders_and_custom_superviser_jobs() {
//         DebugProbes.install()
//         withTimeout(60_000) {
             val result = schema.executeBlocking("""
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
             """.trimIndent())

             println(result)
//         }
     }

}
