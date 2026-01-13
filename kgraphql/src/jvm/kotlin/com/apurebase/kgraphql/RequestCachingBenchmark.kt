package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.Schema
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class RequestCachingBenchmark {
    private val smallRequest = """
        { one { name quantity active } }
    """.trimIndent()
    private val largeRequest = """
        {
            one {
                name
                quantity
                active
            }
            secondOne: one {
                name
                quantity
                secondQuantity: quantity
                active
            }
            two(name: "FELLA") {
                range {
                    start
                    endInclusive
                }
            }
            three {
                id
            }
        }
    """.trimIndent()
    private val invalidRequest = """
        {
            one {
                name
                quantity
                active
            
        }
    """.trimIndent()

    @Param("true", "false")
    var caching = true

    lateinit var schema: Schema

    @Setup
    fun setup() {
        schema = BenchmarkSchema.create {
            configure {
                useCachingDocumentParser = caching
            }
        }
    }

    @Benchmark
    fun smallRequest(): String {
        return schema.executeBlocking(smallRequest)
    }

    @Benchmark
    fun largeRequest(): String {
        return schema.executeBlocking(largeRequest)
    }

    @Benchmark
    fun invalidRequest(): String {
        return runCatching { schema.executeBlocking(invalidRequest) }.getOrDefault("")
    }
}
