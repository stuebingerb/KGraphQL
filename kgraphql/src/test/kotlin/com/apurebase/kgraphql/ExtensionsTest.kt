package com.apurebase.kgraphql

import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ExtensionsTest {

    @Test
    fun `mapIndexedParallel should map with correct order and index`() = runTest {
        val inputs = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        inputs.mapIndexedParallel { index, value -> index to value } shouldContainExactly listOf(
            0 to 1,
            1 to 2,
            2 to 3,
            3 to 4,
            4 to 5,
            5 to 6,
            6 to 7,
            7 to 8,
            8 to 9,
            9 to 10
        )
    }
}
