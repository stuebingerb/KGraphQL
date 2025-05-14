package com.apurebase.kgraphql

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.instanceOf
import kotlin.reflect.KClass

infix fun Any?.shouldBeInstanceOf(clazz: KClass<*>) = this shouldBe instanceOf(clazz)

inline fun <reified T : Exception> expect(message: String, block: () -> Unit) {
    shouldThrowExactly<T>(block) shouldHaveMessage message
}
