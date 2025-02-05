package com.apurebase.kgraphql.function

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.internal.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class MemoizeTest {
    private val slowFunctionDuration = 1000.milliseconds

    @Test
    fun `calls function on invocation`() = runTest {
        assertEquals(2, memoize(this, 1, ::slowPlusOne)(1))
        assertEquals(slowFunctionDuration.inWholeMilliseconds, testScheduler.currentTime)
    }

    @Test
    fun `calls function once on multiple invocations with same input`() = runTest {
        val memoized = memoize(this, 1, ::slowPlusOne)
        repeat(2) { assertEquals(2, memoized(1)) }
        assertEquals(slowFunctionDuration.inWholeMilliseconds, testScheduler.currentTime)
    }

    @Test
    fun `different memoized instances do not share their memory`() = runTest {
        val one = memoize(this, 2, ::slowPlusOne)
        val two = memoize(this, 2, ::slowPlusOne)

        one(1)
        two(2)

        assertEquals((slowFunctionDuration * 2).inWholeMilliseconds, testScheduler.currentTime)
    }

    private suspend fun slowPlusOne(x: Int): Int {
        delay(slowFunctionDuration)
        return x + 1
    }
}
