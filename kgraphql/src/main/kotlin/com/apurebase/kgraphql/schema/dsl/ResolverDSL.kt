package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.schema.dsl.types.InputValuesDSL
import com.apurebase.kgraphql.schema.model.InputValueDef
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class ResolverDSL(val target: Target) {
    fun withArgs(block: InputValuesDSL.() -> Unit) {
        val inputValuesDSL = InputValuesDSL().apply(block)

        target.addInputValues(
            inputValuesDSL.inputValues.map { inputValue ->
                inputValue.toKQLInputValue()
            }
        )
    }

    inline fun <reified T : Any> returns(): ResolverDSL {
        target.setReturnType(typeOf<T>())
        return this
    }

    interface Target {
        fun addInputValues(inputValues: Collection<InputValueDef<*>>)
        fun setReturnType(type: KType)
    }
}
