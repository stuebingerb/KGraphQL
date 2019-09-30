package com.apurebase.kgraphql.schema.model

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.jol.DataLoader
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KProperty1

interface PropertyDef<T> : Depreciable, DescribedDef {

    val accessRule : ((T?, Context) -> Exception?)?

    val name : String

    open class Function<T, R> (
            name : String,
            resolver: FunctionWrapper<R>,
            override val description: String? = null,
            override val isDeprecated: Boolean = false,
            override val deprecationReason: String? = null,
            accessRule : ((T?, Context) -> Exception?)? = null,
            inputValues : List<InputValueDef<*>> = emptyList()
    ) : BaseOperationDef<T, R>(name, resolver, inputValues, accessRule), PropertyDef<T>

    /**
     * [T] -> The Parent Type
     * [K] -> The Key that'll be passed to the dataLoader
     * [R] -> The return type
     */
    open class DataLoadDef<T, K, R>(
        override val name: String,
        val loader: FunctionWrapper<Map<K, R>>,
        val prepare: FunctionWrapper<K>,
        val returnWrapper: FunctionWrapper<R>, // TODO: Should just be the KType directly
        val cache: ConcurrentMap<K, R>,
        override val description: String? = null,
        override val isDeprecated: Boolean = false,
        override val deprecationReason: String? = null,
        override val accessRule: ((T?, Context) -> Exception?)? = null,
        val inputValues: List<InputValueDef<*>> = emptyList()
    ): PropertyDef<T>

    /**
     * [T] -> The Parent Type
     * [K] -> The Key that'll be passed to the dataLoader
     * [R] -> The return type
     */
    open class DataLoaderDefV2<T, K, R>(
        override val accessRule: ((T?, Context) -> Exception?)?,
        override val name: String,
        override val isDeprecated: Boolean,
        override val deprecationReason: String?,
        override val description: String?,

        val inputValues: List<InputValueDef<*>> = emptyList(),
        val prepare: FunctionWrapper<K>,
        val returnWrapper: FunctionWrapper<R>, // Don't use this one
        val dataLoader: DataLoader<K, R>
    ): PropertyDef<T>

    open class Kotlin<T : Any, R> (
            val kProperty: KProperty1<T, R>,
            override val description: String? = null,
            override val isDeprecated: Boolean = false,
            override val deprecationReason: String? = null,
            override val accessRule : ((T?, Context) -> Exception?)? = null,
            val isIgnored : Boolean = false
    ) : Definition(kProperty.name), PropertyDef<T>

    class Union<T> (
            name : String,
            resolver : FunctionWrapper<Any?>,
            val union : TypeDef.Union,
            description: String?,
            val nullable: Boolean,
            isDeprecated: Boolean,
            deprecationReason: String?,
            accessRule : ((T?, Context) -> Exception?)? = null,
            inputValues : List<InputValueDef<*>>
    ) : Function<T, Any?>(name, resolver, description, isDeprecated, deprecationReason, accessRule, inputValues), PropertyDef<T>
}
