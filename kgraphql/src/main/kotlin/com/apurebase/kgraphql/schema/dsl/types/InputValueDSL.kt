package com.apurebase.kgraphql.schema.dsl.types

import com.apurebase.kgraphql.schema.dsl.DepreciableItemDSL
import com.apurebase.kgraphql.schema.model.InputValueDef
import kotlin.reflect.KClass


class InputValueDSL<T : Any>(val kClass: KClass<T>) : DepreciableItemDSL() {

    lateinit var name : String

    var defaultValue : T? = null

    fun toKQLInputValue() : InputValueDef<T> = InputValueDef(
            kClass = kClass,
            name = name,
            defaultValue = defaultValue,
            isDeprecated = isDeprecated,
            description = description,
            deprecationReason = deprecationReason
    )
}