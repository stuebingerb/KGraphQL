package com.apurebase.kgraphql

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

internal fun <T : Any> KClass<T>.defaultKQLTypeName() = this.simpleName!!

internal fun String.dropQuotes(): String = if (isLiteral()) {
    drop(1).dropLast(1)
} else {
    this
}

internal fun String.isLiteral(): Boolean = startsWith('\"') && endsWith('\"')

internal fun KClass<*>.isIterable() = isSubclassOf(Iterable::class) || this in typeByPrimitiveArrayClass.keys

internal fun KType.isIterable() = jvmErasure.isIterable() || toString().startsWith("kotlin.Array")

internal val typeByPrimitiveArrayClass = mapOf(
    IntArray::class to Int::class.createType(),
    LongArray::class to Long::class.createType(),
    ShortArray::class to Short::class.createType(),
    FloatArray::class to Float::class.createType(),
    DoubleArray::class to Double::class.createType(),
    CharArray::class to Char::class.createType(),
    BooleanArray::class to Boolean::class.createType()
)

internal fun KType.getIterableElementType(): KType {
    require(isIterable()) { "KType $this is not collection type" }
    return typeByPrimitiveArrayClass[jvmErasure] ?: arguments.firstOrNull()?.type ?: throw NoSuchElementException("KType $this has no type arguments")
}

internal suspend fun <T, R> Iterable<T>.mapIndexedParallel(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    block: suspend (Int, T) -> R
): List<R> =
    withContext(dispatcher) {
        coroutineScope {
            this@mapIndexedParallel.mapIndexed { index, i -> async { block(index, i) } }.awaitAll()
        }
    }
