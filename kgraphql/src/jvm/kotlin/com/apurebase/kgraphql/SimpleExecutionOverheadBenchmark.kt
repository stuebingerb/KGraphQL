package com.apurebase.kgraphql

import com.apurebase.kgraphql.BenchmarkSchema.oneResolver
import com.apurebase.kgraphql.BenchmarkSchema.threeResolver
import com.apurebase.kgraphql.BenchmarkSchema.twoResolver
import com.apurebase.kgraphql.schema.Schema
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class SimpleExecutionOverheadBenchmark {

    @Param("true", "false")
    var withKGraphQL = true

    lateinit var schema: Schema

    lateinit var objectMapper: ObjectMapper

    @Setup
    fun setup() {
        if (withKGraphQL) {
            schema = BenchmarkSchema.create {}
        } else {
            objectMapper = jacksonObjectMapper()
        }
    }

    @Benchmark
    fun benchmark(): String {
        return if (withKGraphQL) {
            schema.executeBlocking("{one{name, quantity, active}, two(name : \"FELLA\"){range{start, endInclusive}}, three{id}}")
        } else {
            runBlocking {
                ": ${objectMapper.writeValueAsString(oneResolver())} " +
                    ": ${objectMapper.writeValueAsString(twoResolver("FELLA"))} " +
                    ": ${objectMapper.writeValueAsString(threeResolver())}"
            }
        }
    }
}
