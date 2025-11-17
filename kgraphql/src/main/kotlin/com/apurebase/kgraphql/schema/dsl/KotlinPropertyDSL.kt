package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.model.PropertyDef
import kotlin.reflect.KProperty1

class KotlinPropertyDSL<T : Any, R>(
    private val kProperty: KProperty1<T, R>,
    block: KotlinPropertyDSL<T, R>.() -> Unit
) : LimitedAccessItemDSL<T>() {

    // Whether this property should be ignored, i.e. hidden from GraphQL
    var ignore = false

    // Custom name for this property, otherwise taken from the [kProperty]
    var name: String? = null

    init {
        block()
    }

    fun accessRule(rule: (T, Context) -> Exception?) {
        val accessRuleAdapter: (T?, Context) -> Exception? = { parent, ctx ->
            if (parent != null) {
                rule(parent, ctx)
            } else {
                IllegalArgumentException("Unexpected null parent of kotlin property")
            }
        }
        accessRuleBlock = accessRuleAdapter
    }

    fun toKQLProperty() = PropertyDef.Kotlin(
        kProperty = kProperty,
        description = description,
        isDeprecated = isDeprecated,
        deprecationReason = deprecationReason,
        isIgnored = ignore,
        accessRule = accessRuleBlock,
        customName = name
    )
}
