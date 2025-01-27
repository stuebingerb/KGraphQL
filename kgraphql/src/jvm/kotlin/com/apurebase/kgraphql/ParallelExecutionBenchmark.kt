package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.Schema
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class ParallelExecutionBenchmark {

    @Param("true", "false")
    var withSuspendResolvers = false

    private lateinit var schema: Schema

    private val query = buildString {
        append("{")
        (0..999).forEach { appendLine("automated$it".prependIndent("  ")) }
        append("}")
    }

    @Setup
    fun setup() {
        schema = KGraphQL.schema {
            if (!withSuspendResolvers)
                repeat(1000) {
                    query("automated$it") {
                        resolver { ->
                            Thread.sleep(3)
                            "$it"
                        }
                    }
                } else {
                repeat(1000) {
                    query("automated$it") {
                        resolver { ->
                            delay(3)
                            "$it"
                        }
                    }
                }
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    fun queryBenchmark(): String =
        schema.executeBlocking(query)
}
