package de.stuebingerb.kgraphql.schema.dsl

import de.stuebingerb.kgraphql.defaultOneOfPropertyName
import de.stuebingerb.kgraphql.schema.model.PropertyDef
import kotlin.reflect.KClass

class OneOfPropertyDSL<T : Any, R : T>(private val kClass: KClass<R>, block: OneOfPropertyDSL<T, R>.() -> Unit) :
    DepreciableItemDSL() {

    // Whether this sealed sub class should be ignored, i.e. hidden from GraphQL
    var ignore = false

    // Custom name for this property, otherwise taken from the [kProperty]
    var name: String? = null

    init {
        block()
    }

    fun toKQLProperty() = PropertyDef.OneOfProperty<T, R>(
        name = name ?: kClass.defaultOneOfPropertyName(),
        isIgnored = ignore,
        description = description,
        isDeprecated = isDeprecated,
        deprecationReason = deprecationReason
    )
}
