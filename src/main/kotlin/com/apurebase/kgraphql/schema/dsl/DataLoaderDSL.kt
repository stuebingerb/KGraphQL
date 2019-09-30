package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.jol.DataLoader
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.InputValueDef
import com.apurebase.kgraphql.schema.model.PropertyDef
import java.lang.IllegalArgumentException
import java.util.concurrent.ConcurrentHashMap


open class DataLoaderDSL<T, K: Any, R>(
    val name: String,
    private val block : DataLoaderDSL<T, K, R>.() -> Unit
): LimitedAccessItemDSL<T>(), ResolverDSL.Target {

    internal lateinit var prepareWrapper : FunctionWrapper<K>
    internal lateinit var dataLoader: DataLoader<K, R>

    private val inputValues = mutableListOf<InputValueDef<*>>()

    // TODO: Does this work?
    internal lateinit var returnType: FunctionWrapper<R>

    fun setReturnType(block: suspend () -> R) {
        returnType = FunctionWrapper.on(block)
    }

    fun loader(block: suspend (List<K>) -> Map<K, R>) {
        dataLoader = DataLoader(block)
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

    fun toKQLProperty(): PropertyDef.DataLoaderDefV2<T, K, R> {
        block()
        return PropertyDef.DataLoaderDefV2(
            name = name,
            description = description,
            accessRule = accessRuleBlock,
            deprecationReason = deprecationReason,
            isDeprecated = isDeprecated,
            inputValues = inputValues,
            prepare = prepareWrapper,
            returnWrapper = returnType,
            dataLoader = dataLoader
        )
    }


    override fun addInputValues(inputValues: Collection<InputValueDef<*>>) {
        this.inputValues.addAll(inputValues)
    }

}
