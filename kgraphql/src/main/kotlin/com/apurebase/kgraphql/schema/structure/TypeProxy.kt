package com.apurebase.kgraphql.schema.structure

import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.introspection.__EnumValue
import com.apurebase.kgraphql.schema.introspection.__InputValue
import kotlin.reflect.KClass

open class TypeProxy(var proxied: Type) : Type {

    override fun isInstance(value: Any?): Boolean = proxied.isInstance(value)

    override val kClass: KClass<*>?
        get() = proxied.kClass

    override val kind: TypeKind
        get() = proxied.kind

    override val name: String?
        get() = proxied.name

    override val description: String?
        get() = proxied.description

    override val fields: List<Field>?
        get() = proxied.fields

    override val interfaces: List<Type>?
        get() = proxied.interfaces

    override val possibleTypes: List<Type>?
        get() = proxied.possibleTypes

    override val enumValues: List<__EnumValue>?
        get() = proxied.enumValues

    override val inputFields: List<__InputValue>?
        get() = proxied.inputFields

    override val ofType: Type?
        get() = proxied.ofType

    override val specifiedByURL: String?
        get() = proxied.specifiedByURL

    override fun get(name: String) = proxied[name]
}
