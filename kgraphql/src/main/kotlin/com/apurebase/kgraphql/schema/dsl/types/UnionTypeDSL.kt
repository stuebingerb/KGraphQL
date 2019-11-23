package com.apurebase.kgraphql.schema.dsl.types

import com.apurebase.kgraphql.schema.dsl.ItemDSL
import kotlin.reflect.KClass


class UnionTypeDSL() : ItemDSL() {

    internal val possibleTypes = mutableSetOf<KClass<*>>()

    fun <T : Any>type(kClass : KClass<T>){
        possibleTypes.add(kClass)
    }

    inline fun <reified T : Any>type(){
        type(T::class)
    }
}