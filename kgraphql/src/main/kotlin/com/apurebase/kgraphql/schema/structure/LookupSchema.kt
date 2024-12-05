package com.apurebase.kgraphql.schema.structure

import com.apurebase.kgraphql.isIterable
import com.apurebase.kgraphql.request.TypeReference
import com.apurebase.kgraphql.schema.Schema
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

interface LookupSchema : Schema {

    fun typeByKClass(kClass: KClass<*>): Type?

    fun typeByKType(kType: KType): Type?

    fun typeByName(name: String): Type?

    fun inputTypeByKClass(kClass: KClass<*>): Type?

    fun inputTypeByKType(kType: KType): Type?

    fun inputTypeByName(name: String): Type?

    fun typeReference(kType: KType): TypeReference {
        if (kType.jvmErasure.isIterable()) {
            val elementKType = requireNotNull(kType.arguments.first().type) {
                "Cannot transform kotlin collection type $kType to KGraphQL TypeReference"
            }
            val elementKTypeErasure = elementKType.jvmErasure

            val kqlType = typeByKClass(elementKTypeErasure) ?: inputTypeByKClass(elementKTypeErasure)
            ?: throw IllegalArgumentException("$kType has not been registered in this schema")
            val name = requireNotNull(kqlType.name) { "Cannot create type reference to unnamed type" }

            return TypeReference(name, kType.isMarkedNullable, true, elementKType.isMarkedNullable)
        } else {
            val erasure = kType.jvmErasure
            val kqlType = typeByKClass(erasure) ?: inputTypeByKClass(erasure)
            ?: throw IllegalArgumentException("$kType has not been registered in this schema")
            val name = requireNotNull(kqlType.name) { "Cannot create type reference to unnamed type" }

            return TypeReference(name, kType.isMarkedNullable)
        }
    }
}
