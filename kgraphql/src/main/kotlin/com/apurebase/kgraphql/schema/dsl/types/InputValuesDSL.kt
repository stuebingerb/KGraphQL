package com.apurebase.kgraphql.schema.dsl.types

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf


class InputValuesDSL {

    val inputValues = mutableListOf<InputValueDSL<*>>()

    fun <T : Any> arg(kClass: KClass<T>, kType: KType? = null, block : InputValueDSL<T>.() -> Unit){
        inputValues.add(InputValueDSL(kClass, kType).apply(block))
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> arg(noinline block : InputValueDSL<T>.() -> Unit){
        arg(T::class, typeOf<T>(), block)
    }

}
