package com.apurebase.kgraphql.schema.structure

import com.apurebase.kgraphql.schema.Schema
import kotlin.reflect.KClass

interface LookupSchema : Schema {

    fun typeByKClass(kClass: KClass<*>): Type?

    fun inputTypeByKClass(kClass: KClass<*>): Type?

    fun findTypeByName(name: String): Type?
}
