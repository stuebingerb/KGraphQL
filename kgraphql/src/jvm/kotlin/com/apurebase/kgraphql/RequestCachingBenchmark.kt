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
@Warmup(iterations = 10)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class RequestCachingBenchmark {

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
    fun benchmark(): String {
        return schema.executeBlocking("{one{name, quantity, active}, two(name : \"FELLA\"){range{start, endInclusive}}, three{id}}")
    }
}
