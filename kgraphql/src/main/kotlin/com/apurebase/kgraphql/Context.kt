package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.introspection.NotIntrospected
import kotlin.reflect.KClass

@NotIntrospected
class Context(private val map: Map<KClass<*>, Any>) {

    operator fun <T : Any> get(kClass: KClass<T>): T? {
        val value = map[kClass]
        @Suppress("UNCHECKED_CAST")
        return if (kClass.isInstance(value)) {
            value as T
        } else {
            null
        }
    }

    inline fun <reified T : Any> get(): T? = get(T::class)

    operator fun <T : Any> plus(value: T): Context = Context(map + (value::class to value))
}
