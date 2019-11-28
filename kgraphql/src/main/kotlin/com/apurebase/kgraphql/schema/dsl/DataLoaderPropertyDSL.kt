package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.InputValueDef
import com.apurebase.kgraphql.schema.model.PropertyDef

class DataLoaderPropertyDSL<T, K, R>(
    val name: String,
    private val block : DataLoaderPropertyDSL<T, K, R>.() -> Unit
): LimitedAccessItemDSL<T>(), ResolverDSL.Target {

    internal lateinit var prepareWrapper: FunctionWrapper<K>
    internal lateinit var dataLoader: FunctionWrapper<Map<K, R>>

    private val inputValues = mutableListOf<InputValueDef<*>>()

    internal lateinit var returnType: FunctionWrapper<R>

    fun setReturnType(block: suspend () -> R) {
        returnType = FunctionWrapper.on(block)
    }

    fun loader(block: suspend (List<K>) -> Map<K, R>) {
        dataLoader = FunctionWrapper.on(block)
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
            prepare = prepareWrapper,
            returnWrapper = returnType,
            loader = dataLoader
        )
    }


    override fun addInputValues(inputValues: Collection<InputValueDef<*>>) {
        this.inputValues.addAll(inputValues)
    }

}
