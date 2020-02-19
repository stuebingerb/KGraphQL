package com.apurebase.kgraphql.schema.dsl.types

import com.apurebase.kgraphql.defaultKQLTypeName
import com.apurebase.kgraphql.schema.dsl.ItemDSL
import kotlin.reflect.KClass


class InputTypeDSL<T : Any>(val kClass: KClass<T>) : ItemDSL() {

    var name = kClass.defaultKQLTypeName()
}
