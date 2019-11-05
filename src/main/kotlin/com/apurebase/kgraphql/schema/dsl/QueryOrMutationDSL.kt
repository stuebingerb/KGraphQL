package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.InputValueDef
import com.apurebase.kgraphql.schema.model.MutationDef
import com.apurebase.kgraphql.schema.model.QueryDef
import kotlin.reflect.KFunction


class QueryOrMutationDSL(
    val name : String,
    private val block : QueryOrMutationDSL.() -> Unit
) : LimitedAccessItemDSL<Nothing>(), ResolverDSL.Target {

    private val inputValues = mutableListOf<InputValueDef<*>>()

    private var functionWrapper : FunctionWrapper<*>? = null

    private fun resolver(function: FunctionWrapper<*>): ResolverDSL {
        functionWrapper = function
        return ResolverDSL(this)
    }

    fun <T> KFunction<T>.toResolver() = resolver(FunctionWrapper.on(this))

    fun <T> resolver(function: suspend () -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R> resolver(function: suspend (R) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E> resolver(function: suspend (R, E) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W> resolver(function: suspend (R, E ,W ) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q> resolver(function: suspend (R, E, W, Q) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A> resolver(function: suspend (R, E, W, Q, A) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A, S> resolver(function: suspend (R, E, W, Q, A, S) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A, S, B> resolver(function: suspend (R, E, W, Q, A, S, B) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A, S, B, U> resolver(function: suspend (R, E, W, Q, A, S, B, U) -> T) = resolver(FunctionWrapper.on(function))

    fun <T, R, E, W, Q, A, S, B, U, C> resolver(function: suspend (R, E, W, Q, A, S, B, U, C) -> T) = resolver(FunctionWrapper.on(function))

    fun accessRule(rule: (Context) -> Exception?){
        val accessRuleAdapter: (Nothing?, Context) -> Exception? = { _, ctx -> rule(ctx) }
        this.accessRuleBlock = accessRuleAdapter
    }

    override fun addInputValues(inputValues: Collection<InputValueDef<*>>) {
        this.inputValues.addAll(inputValues)
    }

    internal fun toKQLQuery(): QueryDef<out Any?> {
        block()
        val function = functionWrapper ?: throw IllegalArgumentException("resolver has to be specified for query [$name]")

        return QueryDef (
            name = name,
            resolver = function,
            description = description,
            isDeprecated = isDeprecated,
            deprecationReason = deprecationReason,
            inputValues = inputValues,
            accessRule = accessRuleBlock
        )
    }

    internal fun toKQLMutation(): MutationDef<out Any?> {
        block()
        val function = functionWrapper ?: throw IllegalArgumentException("resolver has to be specified for mutation [$name]")

        return MutationDef(
            name = name,
            resolver = function,
            description = description,
            isDeprecated = isDeprecated,
            deprecationReason = deprecationReason,
            inputValues = inputValues,
            accessRule = accessRuleBlock
        )
    }
}
