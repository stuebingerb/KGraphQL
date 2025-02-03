package com.apurebase.kgraphql.schema.structure

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.introspection.NotIntrospected
import com.apurebase.kgraphql.schema.introspection.__Field
import com.apurebase.kgraphql.schema.introspection.__InputValue
import com.apurebase.kgraphql.schema.introspection.__Type
import com.apurebase.kgraphql.schema.model.BaseOperationDef
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.PropertyDef
import com.apurebase.kgraphql.schema.model.Transformation
import com.apurebase.kgraphql.schema.stitched.LinkArgument
import nidomiro.kdataloader.factories.DataLoaderFactory
import kotlin.reflect.full.findAnnotation

@Suppress("UNCHECKED_CAST")
sealed class Field : __Field {

    abstract val arguments: List<InputValue<*>>

    override val args: List<__InputValue>
        get() = arguments.filterNot { it.type.kClass?.findAnnotation<NotIntrospected>() != null }

    abstract val returnType: Type

    override val type: __Type
        get() = returnType

    abstract fun checkAccess(parent: Any?, ctx: Context)

    open class Function<T, R>(
        private val kql: BaseOperationDef<T, R>,
        override val returnType: Type,
        override val arguments: List<InputValue<*>>
    ) : Field(), FunctionWrapper<R> by kql {

        override val name: String = kql.name

        override val description: String? = kql.description

        override val isDeprecated: Boolean = kql.isDeprecated

        override val deprecationReason: String? = kql.deprecationReason

        override fun checkAccess(parent: Any?, ctx: Context) {
            kql.accessRule?.invoke(parent as T?, ctx)?.let { throw it }
        }
    }

    class DataLoader<T, K, R>(
        val kql: PropertyDef.DataLoadedFunction<T, K, R>,
        val loader: DataLoaderFactory<K, R>,
        override val returnType: Type,
        override val arguments: List<InputValue<*>>
    ) : Field() {
        override val isDeprecated = kql.isDeprecated
        override val deprecationReason = kql.deprecationReason
        override val description = kql.description
        override val name = kql.name

        override fun checkAccess(parent: Any?, ctx: Context) {
            kql.accessRule?.invoke(parent as T?, ctx)?.let { throw it }
        }
    }

    class Kotlin<T : Any, R>(
        private val kql: PropertyDef.Kotlin<T, R>,
        override val returnType: Type,
        override val arguments: List<InputValue<*>>,
        val transformation: Transformation<T, R>?
    ) : Field() {

        val kProperty = kql.kProperty

        override val name: String = kql.name

        override val description: String? = kql.description

        override val isDeprecated: Boolean = kql.isDeprecated

        override val deprecationReason: String? = kql.deprecationReason

        override fun checkAccess(parent: Any?, ctx: Context) {
            kql.accessRule?.invoke(parent as T?, ctx)?.let { throw it }
        }
    }

    class Union<T>(
        private val kql: PropertyDef.Union<T>,
        override val returnType: Type,
        override val arguments: List<InputValue<*>>
    ) : Field(), FunctionWrapper<Any?> by kql {

        override val name: String = kql.name

        override val description: String? = kql.description

        override val isDeprecated: Boolean = kql.isDeprecated

        override val deprecationReason: String? = kql.deprecationReason

        override fun checkAccess(parent: Any?, ctx: Context) {
            kql.accessRule?.invoke(parent as T?, ctx)?.let { throw it }
        }
    }

    // TODO: can we combine remoteoperation and delegated?
    class RemoteOperation<T, R>(
        private val kql: BaseOperationDef<T, R>,
        val field: Delegated,
        val remoteUrl: String,
        val remoteQuery: String,
        override val arguments: List<InputValue<*>> = emptyList(),
        override val args: List<__InputValue>
    ) : Function<T, R>(kql, field.returnType, emptyList()) {
        override fun checkAccess(parent: Any?, ctx: Context) {
            kql.accessRule?.invoke(parent as T?, ctx)?.let { throw it }
        }

        override val isDeprecated: Boolean = field.isDeprecated
        override val deprecationReason: String? = field.deprecationReason
        override val name: String = field.name
        override val description: String? = field.description
    }

    class Delegated(
        override val name: String,
        override val description: String?,
        override val isDeprecated: Boolean,
        override val deprecationReason: String?,
        override val args: List<__InputValue>,
        override val returnType: Type,
        val argsFromParent: Map<__InputValue, String>
    ) : Field() {
        override val arguments: List<InputValue<*>> = emptyList()

        override fun checkAccess(parent: Any?, ctx: Context) {
            // Noop
        }
    }
}
