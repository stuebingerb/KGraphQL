package com.apurebase.kgraphql

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

internal fun <T : Any> KClass<T>.defaultKQLTypeName() = this.simpleName!!

internal fun String.dropQuotes(): String = if (isLiteral()) {
    drop(1).dropLast(1)
} else {
    this
}

internal fun String.isLiteral(): Boolean = startsWith('\"') && endsWith('\"')

internal fun KClass<*>.isIterable() = isSubclassOf(Iterable::class)

internal fun KType.isIterable() = jvmErasure.isIterable() || toString().startsWith("kotlin.Array")

internal fun KType.getIterableElementType(): KType {
    require(isIterable()) { "KType $this is not collection type" }
    return arguments.firstOrNull()?.type ?: throw NoSuchElementException("KType $this has no type arguments")
}

internal suspend fun <T, R> Iterable<T>.mapIndexedParallel(block: suspend (Int, T) -> R): List<R> = coroutineScope {
    this@mapIndexedParallel.mapIndexed { index, i -> async { block(index, i) } }.awaitAll()
}
