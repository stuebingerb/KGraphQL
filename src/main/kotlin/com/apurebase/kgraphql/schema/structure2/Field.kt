package com.apurebase.kgraphql.schema.structure2

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.request.Arguments
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.execution.ParallelRequestExecutor
import com.apurebase.kgraphql.schema.execution.ParallelRequestExecutor.ExecutionContext
import com.apurebase.kgraphql.schema.introspection.NotIntrospected
import com.apurebase.kgraphql.schema.introspection.__Field
import com.apurebase.kgraphql.schema.introspection.__InputValue
import com.apurebase.kgraphql.schema.introspection.__Type
import com.apurebase.kgraphql.schema.model.BaseOperationDef
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.PropertyDef
import com.apurebase.kgraphql.schema.model.Transformation
import kotlin.reflect.full.findAnnotation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.*


sealed class Field : __Field {

    abstract val arguments : List<InputValue<*>>

    override val args: List<__InputValue>
        get() = arguments.filterNot { it.type.kClass?.findAnnotation<NotIntrospected>() != null }

    abstract val returnType : Type

    override val type: __Type
        get() = returnType

    abstract fun checkAccess(parent : Any?, ctx: Context)

    open class Function<T, R>(
            kql : BaseOperationDef<T, R>,
            override val returnType: Type,
            override val arguments: List<InputValue<*>>
    ) : Field(), FunctionWrapper<R> by kql {

        override val name: String = kql.name

        override val description: String? = kql.description

        override val isDeprecated: Boolean = kql.isDeprecated

        override val deprecationReason: String? = kql.deprecationReason

        val accessRule : ((T?, Context) -> Exception?)? = kql.accessRule

        override fun checkAccess(parent: Any?, ctx: Context) {
            accessRule?.invoke(parent as T?, ctx)?.let { throw it }
        }
    }

    open class DataLoader<T, K, R>(
        val kql: PropertyDef.DataLoaderDefV2<T, K, R>,
        override val returnType: Type,
        override val arguments: List<InputValue<*>>
    ): Field() {
        override val isDeprecated = kql.isDeprecated
        override val deprecationReason = kql.deprecationReason
        override val description = kql.description
        override val name = kql.name

        override fun checkAccess(parent: Any?, ctx: Context) {
            kql.accessRule?.invoke(parent as T?, ctx)?.let { throw it }
        }
    }

/*
//    open class DataLoader<T, K, R>(
//        val kql : PropertyDef.DataLoadDef<T, K, R>,
//        override val returnType: Type,
//        override val arguments: List<InputValue<*>>
//    ) : Field() {
//        override val isDeprecated = kql.isDeprecated
//        override val deprecationReason = kql.deprecationReason
//        override val description = kql.description
//        override val name = kql.name

        /*
        var total: Int = -1
        private var count = 0

        private val map = mutableMapOf<Any, K>()
        private val channels = mutableMapOf<K, Channel<R>>()

        */
        /*// We'll need cache n' stuff.
        suspend fun load(
            test: ParallelRequestExecutor,
            ctx: ExecutionContext,
            args: Arguments?,
            value: Any
        ): Any? {
            return with (test) {
                println("Field:load, ${args?.values} - $total == ${count + 1}")
                val key = kql.prepare.invoke(
                    funName = name,
                    receiver = value,
                    inputValues = arguments,
                    args = args,
                    ctx = ctx
                ) ?: throw TODO("Don't think I wan't this to be possible to be null?")

                channels[key] = Channel()
                map[value] = key
                if (total == ++count) {
                    val res = kql.loader.suspendInvoke(map.values.asIterable().toList())
                        ?: throw TODO("Should never be null!")
                    res.forEach { k, loaded ->
                        launch { // TODO: Why launch?
                            channels[k]!!.send(loaded)
                        }
                    }
                    count = 0
                    map.clear()
                }

                val result = channels[key]!!.receive()
                channels[key]!!.close()
                result
            }
        } */
//        override fun checkAccess(parent: Any?, ctx: Context) {
//            kql.accessRule?.invoke(parent as T?, ctx)?.let { throw it }
//        }
//    }
*/

    class Kotlin<T : Any, R>(
        kql : PropertyDef.Kotlin<T, R>,
        override val returnType: Type,
        override val arguments: List<InputValue<*>>,
        val transformation : Transformation<T, R>?
    ) : Field(){

        val kProperty = kql.kProperty

        override val name: String = kql.name

        override val description: String? = kql.description

        override val isDeprecated: Boolean = kql.isDeprecated

        override val deprecationReason: String? = kql.deprecationReason

        val accessRule : ((T?, Context) -> Exception?)? = kql.accessRule

        override fun checkAccess(parent: Any?, ctx: Context) {
            accessRule?.invoke(parent as T?, ctx)?.let { throw it }
        }
    }

    class Union<T> (
            kql : PropertyDef.Union<T>,
            val nullable: Boolean,
            override val returnType: Type.Union,
            override val arguments: List<InputValue<*>>
    ) : Field(), FunctionWrapper<Any?> by kql {

        override val name: String = kql.name

        override val description: String? = kql.description

        override val isDeprecated: Boolean = kql.isDeprecated

        override val deprecationReason: String? = kql.deprecationReason

        val accessRule : ((T?, Context) -> Exception?)? = kql.accessRule

        override fun checkAccess(parent: Any?, ctx: Context) {
            accessRule?.invoke(parent as T?, ctx)?.let { throw it }
        }
    }
}
