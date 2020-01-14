package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.InputValueDef
import com.apurebase.kgraphql.schema.model.PropertyDef
import nidomiro.kdataloader.BatchLoader
import nidomiro.kdataloader.DataLoaderOptions
import nidomiro.kdataloader.SimpleDataLoaderImpl

class DataLoaderPropertyDSL<T, K, R>(
    val name: String,
    private val block : DataLoaderPropertyDSL<T, K, R>.() -> Unit
): LimitedAccessItemDSL<T>(), ResolverDSL.Target {

    internal lateinit var dataLoader: BatchLoader<K, R>
    internal lateinit var prepareWrapper: FunctionWrapper<K>

    private val inputValues = mutableListOf<InputValueDef<*>>()

    internal lateinit var returnType: FunctionWrapper<R>

    fun setReturnType(block: suspend () -> R) {
        returnType = FunctionWrapper.on(block)
    }

    fun loader(block: BatchLoader<K, R>) {
        dataLoader = block
    }

    fun prepare(block: suspend (T) -> K) {
        prepareWrapper = FunctionWrapper.on(block, true)
    }

    fun <E> prepare(block: suspend (T, E) -> K) {
        prepareWrapper = FunctionWrapper.on(block, true)
    }

    fun accessRule(rule: (T, Context) -> Exception?){
        val accessRuleAdapter: (T?, Context) -> Exception? = { parent, ctx ->
            if (parent != null) rule(parent, ctx) else IllegalArgumentException("Unexpected null parent of kotlin property")
        }
        this.accessRuleBlock = accessRuleAdapter
    }

    fun toKQLProperty(): PropertyDef.DataLoadedFunction<T, K, R> {
        block()
        return PropertyDef.DataLoadedFunction(
            name = name,
            description = description,
            accessRule = accessRuleBlock,
            deprecationReason = deprecationReason,
            isDeprecated = isDeprecated,
            inputValues = inputValues,
            returnWrapper = returnType,
            prepare = prepareWrapper,
            loader = nidomiro.kdataloader.factories.SimpleDataLoaderFactory(nidomiro.kdataloader.DataLoaderOptions(), mapOf(), dataLoader)
        )
    }


    override fun addInputValues(inputValues: Collection<InputValueDef<*>>) {
        this.inputValues.addAll(inputValues)
    }

}
