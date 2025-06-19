package com.apurebase.kgraphql.helpers

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class KGraphQLExtensionsTest {
    @ParameterizedTest
    @ValueSource(doubles = [1.0, 2.0, -3.0, 123456789.0, 0.0, -0.0, 1.0000000000])
    fun `isWholeNumber should return true for whole numbers`(input: Double) {
        input.isWholeNumber() shouldBe true
    }

    @ParameterizedTest
    @ValueSource(doubles = [1.01, 2.00000000001, -3.10, 123456789.1])
    fun `isWholeNumber should return false for numbers that are not whole`(input: Double) {
        input.isWholeNumber() shouldBe false
    }
}
