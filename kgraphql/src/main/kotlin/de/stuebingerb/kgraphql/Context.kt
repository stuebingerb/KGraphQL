package de.stuebingerb.kgraphql

import de.stuebingerb.kgraphql.schema.introspection.NotIntrospected
import java.util.Collections
import kotlin.reflect.KClass

@NotIntrospected
class Context(
    private val map: Map<KClass<*>, Any>,
    private val _errors: MutableList<ExecutionError> = Collections.synchronizedList(mutableListOf())
) {
    // Outside view of errors should be readonly
    internal val errors: List<ExecutionError> = _errors

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

    operator fun <T : Any> plus(value: T): Context = Context(map + (value::class to value), _errors)

    fun raiseError(error: ExecutionError) = _errors.add(error)
}
