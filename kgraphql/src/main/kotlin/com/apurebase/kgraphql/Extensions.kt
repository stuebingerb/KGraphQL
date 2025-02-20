package com.apurebase.kgraphql

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

fun <T : Any> KClass<T>.defaultKQLTypeName() = this.simpleName!!

internal fun String.dropQuotes(): String = if (isLiteral()) {
    drop(1).dropLast(1)
} else {
    this
}

internal fun String.isLiteral(): Boolean = startsWith('\"') && endsWith('\"')

fun KClass<*>.isIterable() = isSubclassOf(Iterable::class)

fun KType.isIterable() = jvmErasure.isIterable() || toString().startsWith("kotlin.Array")

fun KType.getIterableElementType(): KType {
    require(isIterable()) { "KType $this is not collection type" }
    return arguments.firstOrNull()?.type ?: throw NoSuchElementException("KType $this has no type arguments")
}

internal suspend fun <T, R> Collection<T>.toMapAsync(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    block: suspend (T) -> R
): Map<T, R> = coroutineScope {
    val channel = Channel<Pair<T, R>>()
    val jobs = map { item ->
        launch(dispatcher) {
            try {
                val res = block(item)
                channel.send(item to res)
            } catch (e: Exception) {
                channel.close(e)
            }
        }
    }
    val resultMap = mutableMapOf<T, R>()
    repeat(size) {
        try {
            val (item, result) = channel.receive()
            resultMap[item] = result
        } catch (e: Exception) {
            jobs.forEach { job: Job -> job.cancel() }
            throw e
        }
    }

    channel.close()
    resultMap
}
