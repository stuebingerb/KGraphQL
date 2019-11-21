package com.apurebase.kgraphql.schema.dsl.types

import kotlin.reflect.KClass


class InputValuesDSL {

    val inputValues = mutableListOf<InputValueDSL<*>>()


    fun <T : Any> arg(kClass: KClass<T>, block : InputValueDSL<T>.() -> Unit){
        inputValues.add(InputValueDSL(kClass).apply(block))
    }

    inline fun <reified T : Any> arg(noinline block : InputValueDSL<T>.() -> Unit){
        arg(T::class, block)
    }

}