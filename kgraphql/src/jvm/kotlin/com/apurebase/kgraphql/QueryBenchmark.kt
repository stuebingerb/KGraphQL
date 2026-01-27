package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.scalar.ID
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import nidomiro.kdataloader.ExecutionResult
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.SECONDS)
open class QueryBenchmark {
    data class Node(val id: ID, val name: String, val children: List<Node>)

    val largeList = (1..50_000).map { Node(ID("$it"), "Node $it", emptyList()) }
    val nestedObject = Node(
        ID("1"),
        "Node 1",
        listOf(
            Node(
                ID("2"),
                "Node 2",
                listOf(
                    Node(
                        ID("3"),
                        "Node 3",
                        listOf(
                            Node(
                                ID("4"),
                                "Node 4",
                                listOf(
                                    Node(
                                        ID("5"),
                                        "Node 5",
                                        listOf(Node(ID("6"), "Node 6", listOf(Node(ID("7"), "Node 7", listOf()))))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )
    val manyChildren = Node(ID("1"), "Parent 1", (1..1_000).map { Node(ID("child"), "Child", emptyList()) })
    val manyOperations = (1..1_000).joinToString(
        separator = System.lineSeparator(),
        prefix = "{",
        postfix = "}"
    ) { "nestedObject$it: nestedObject { id name }" }

    val schema = KGraphQL.schema {
        type<Node> {
            property<Node>("parent") {
                resolver { Node(it.id, "Parent ${it.id.value}", emptyList()) }
            }
            dataProperty<ID, Node>("dataParent") {
                prepare { node -> node.id }
                loader { ids -> ids.map { ExecutionResult.Success(Node(it, "Parent ${it.value}", emptyList())) } }
            }
        }

        query("largeList") {
            resolver { -> largeList }
        }
        query("nestedObject") {
            resolver { -> nestedObject }
        }
        query("manyChildren") {
            resolver { -> manyChildren }
        }
    }

    @Benchmark
    fun nestedObject(): String {
        return schema.executeBlocking("{nestedObject{id name children {id name children {id name children {id name children {id name children {id name children {id name}}}}}}}}")
    }

    @Benchmark
    fun largeList(): String {
        return schema.executeBlocking("{largeList{id name children {id name}}}")
    }

    @Benchmark
    fun manyChildren(): String {
        return schema.executeBlocking("{manyChildren{id name children {id name parent {id name}}}}")
    }

    @Benchmark
    fun manyDataChildren(): String {
        return schema.executeBlocking("{manyChildren{id name children {id name dataParent {id name}}}}")
    }

    @Benchmark
    fun manyOperations(): String {
        return schema.executeBlocking(manyOperations)
    }
}
