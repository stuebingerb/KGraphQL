package com.apurebase.kgraphql.schema.dsl.types

import com.apurebase.kgraphql.defaultKQLTypeName
import com.apurebase.kgraphql.schema.dsl.ItemDSL
import com.apurebase.kgraphql.schema.dsl.KotlinPropertyDSL
import com.apurebase.kgraphql.schema.model.PropertyDef
import com.apurebase.kgraphql.schema.model.TypeDef
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class InputTypeDSL<T : Any>(val kClass: KClass<T>) : ItemDSL() {

    var name = kClass.defaultKQLTypeName()

    private val kotlinProperties = mutableMapOf<KProperty1<T, *>, PropertyDef.Kotlin<T, *>>()

    fun <R> property(kProperty: KProperty1<T, R>, block: KotlinPropertyDSL<T, R>.() -> Unit) {
        val dsl = KotlinPropertyDSL(kProperty, block)
        kotlinProperties[kProperty] = dsl.toKQLProperty()
    }

    fun <R> KProperty1<T, R>.configure(block: KotlinPropertyDSL<T, R>.() -> Unit) {
        property(this, block)
    }

    internal fun toKQLObject(): TypeDef.Input<T> {
        return TypeDef.Input(
            name = name,
            kClass = kClass,
            kotlinProperties = kotlinProperties.toMap(),
            description = description
        )
    }
}
