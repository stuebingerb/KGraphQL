package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.model.FunctionWrapper
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction

/**
 * Contrary to Java 8 which has about 43 different specialized function interfaces
 * to avoid boxing and unboxing as much as possible, the Function objects compiled by Kotlin
 * only implement fully generic interfaces, effectively using the Object type for any input or output value.
 *
 * This benchmark proves, that it makes no performance difference?
 */
@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class FunctionExecutionBenchmark {

    @Param("true", "false")
    private var useFunctionWrapper = true

    private lateinit var functionWrapper: FunctionWrapper.ArityTwo<String, Int, String>
    private lateinit var biFunction: BiFunction<Int, String, String>

    private val arg1 = 3
    private val arg2 = "CODE"
    private val implementation =
        { int: Int, string: String -> "${int * ThreadLocalRandom.current().nextDouble()} $string" }

    @Setup
    fun setup() {
        if (useFunctionWrapper) {
            val implSuspend: suspend (Int, String) -> String = { int, string -> implementation(int, string) }
            functionWrapper = FunctionWrapper.ArityTwo(implSuspend, false)
        } else {
            biFunction = BiFunction(implementation)
        }
    }

    @Benchmark
    fun benchmarkFunctionExecution(): String? {
        return if (useFunctionWrapper) {
            runBlocking {
                functionWrapper.invoke(arg1, arg2)
            }
        } else {
            biFunction.apply(arg1, arg2)
        }
    }
}
