package de.stuebingerb.kgraphql.schema.dsl.types

import de.stuebingerb.kgraphql.defaultKQLTypeName
import de.stuebingerb.kgraphql.schema.SchemaException
import de.stuebingerb.kgraphql.schema.dsl.ItemDSL
import de.stuebingerb.kgraphql.schema.dsl.KotlinPropertyDSL
import de.stuebingerb.kgraphql.schema.dsl.OneOfPropertyDSL
import de.stuebingerb.kgraphql.schema.model.PropertyDef
import de.stuebingerb.kgraphql.schema.model.TypeDef
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class InputTypeDSL<T : Any>(val kClass: KClass<T>) : ItemDSL() {

    var name = kClass.defaultKQLTypeName()

    var isOneOf = false

    private val kotlinProperties = mutableMapOf<KProperty1<T, *>, PropertyDef.Kotlin<T, *>>()
    private val oneOfProperties = mutableMapOf<KClass<out T>, PropertyDef.OneOfProperty<T, *>>()

    fun <R> property(kProperty: KProperty1<T, R>, block: KotlinPropertyDSL<T, R>.() -> Unit) {
        val dsl = KotlinPropertyDSL(kProperty, block)
        kotlinProperties[kProperty] = dsl.toKQLProperty()
    }

    fun <R> KProperty1<T, R>.configure(block: KotlinPropertyDSL<T, R>.() -> Unit) {
        property(this, block)
    }

    fun <R : T> KClass<R>.configure(block: OneOfPropertyDSL<T, R>.() -> Unit) {
        oneOf(this, block)
    }

    private fun <R : T> oneOf(subClass: KClass<R>, block: OneOfPropertyDSL<T, R>.() -> Unit = {}) {
        if (subClass !in kClass.sealedSubclasses) {
            val directParent = findDirectParent(subClass, kClass)
            val parentHint = directParent?.let { "; configure it via `inputType<${it.simpleName}>()`" } ?: ""
            throw SchemaException("'${subClass.simpleName}' is not a direct subclass of '${kClass.simpleName}'$parentHint")
        }
        val dsl = OneOfPropertyDSL(subClass, block)
        oneOfProperties[subClass] = dsl.toKQLProperty()
    }

    private fun findDirectParent(subClass: KClass<*>, parent: KClass<*>): KClass<*>? {
        return if (subClass in parent.sealedSubclasses) {
            parent
        } else {
            parent.sealedSubclasses.firstNotNullOfOrNull { findDirectParent(subClass, it) }
        }
    }

    internal fun toKQLObject(): TypeDef.Input<T> {
        return TypeDef.Input(
            name = name,
            kClass = kClass,
            kotlinProperties = kotlinProperties.toMap(),
            description = description,
            isOneOf = isOneOf || kClass.isSealed,
            oneOfProperties = oneOfProperties
        )
    }
}
