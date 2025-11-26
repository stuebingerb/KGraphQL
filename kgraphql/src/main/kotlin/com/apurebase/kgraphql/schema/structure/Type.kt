package com.apurebase.kgraphql.schema.structure

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.introspection.__EnumValue
import com.apurebase.kgraphql.schema.introspection.__InputValue
import com.apurebase.kgraphql.schema.introspection.__Type
import com.apurebase.kgraphql.schema.model.TypeDef
import kotlin.reflect.KClass

interface Type : __Type {

    override val ofType: Type?

    override val fields: List<Field>?

    override val interfaces: List<Type>?

    override val possibleTypes: List<Type>?

    operator fun get(name: String): Field? = null

    override fun unwrapped(): Type = when (kind) {
        TypeKind.NON_NULL, TypeKind.LIST -> (ofType as Type).unwrapped()
        else -> this
    }

    fun isNullable() = kind != TypeKind.NON_NULL

    fun isNotNullable() = kind == TypeKind.NON_NULL

    fun unwrapNonNull(): Type = when (kind) {
        TypeKind.NON_NULL -> ofType as Type
        else -> ofType?.unwrapNonNull() ?: this
    }

    fun unwrapList(): Type = when (kind) {
        TypeKind.LIST -> ofType as Type
        else -> ofType?.unwrapList() ?: throw NoSuchElementException("this type does not wrap list element")
    }

    fun listType(): AList = when (kind) {
        TypeKind.LIST -> this as AList
        else -> ofType?.listType() ?: throw NoSuchElementException("this type is not a list type")
    }

    fun isList(): Boolean = when {
        kind == TypeKind.LIST -> true
        ofType == null -> false
        else -> (ofType as Type).isList()
    }

    fun isNotList(): Boolean = !isList()

    fun isElementNullable() = isList() && unwrapList().kind != TypeKind.NON_NULL

    fun isInstance(value: Any?): Boolean = kClass?.isInstance(value) ?: false

    val kClass: KClass<*>?

    abstract class ComplexType(val allFields: List<Field>) : Type {
        private val fieldsByName = allFields.associateBy { it.name }

        override val fields: List<Field>? = allFields.filterNot { it.name.startsWith("__") }

        override fun get(name: String): Field? = fieldsByName[name]

        override val specifiedByURL: String? = null
    }

    class OperationObject(
        override val name: String,
        override val description: String?,
        fields: List<Field>
    ) : ComplexType(fields) {

        override val kClass: KClass<*>? = null

        override val kind: TypeKind = TypeKind.OBJECT

        override val enumValues: List<__EnumValue>? = null

        override val inputFields: List<__InputValue>? = null

        override val ofType: Type? = null

        override val possibleTypes: List<Type>? = null

        override val interfaces: List<Interface<*>> = emptyList()

        override fun isInstance(value: Any?): Boolean = false
    }

    class Object<T : Any>(
        private val definition: TypeDef.Object<T>,
        fields: List<Field> = emptyList(),
        override val interfaces: List<Type>? = emptyList()
    ) : ComplexType(fields) {

        override val kClass = definition.kClass

        override val kind: TypeKind = TypeKind.OBJECT

        override val name: String = definition.name

        override val description: String? = definition.description

        override val enumValues: List<__EnumValue>? = null

        override val inputFields: List<__InputValue>? = null

        override val ofType: Type? = null

        override val possibleTypes: List<Type>? = null

        fun withInterfaces(interfaces: List<Type>) = Object(definition, allFields, interfaces)

        fun withStitchedFields(stitchedFields: List<Field>): RemoteObject =
            RemoteObject(name, description, allFields + stitchedFields, interfaces)
    }

    class Interface<T : Any>(
        private val definition: TypeDef.Object<T>,
        fields: List<Field> = emptyList(),
        override val possibleTypes: List<Type>? = emptyList(),
        override val interfaces: List<Type>? = emptyList()
    ) : ComplexType(fields) {

        override val kClass = definition.kClass

        override val kind: TypeKind = TypeKind.INTERFACE

        override val name: String = definition.name

        override val description: String? = definition.description

        override val enumValues: List<__EnumValue>? = null

        override val inputFields: List<__InputValue>? = null

        override val ofType: Type? = null

        fun withPossibleTypes(possibleTypes: List<Type>) = Interface(definition, allFields, possibleTypes)

        fun withInterfaces(interfaces: List<Type>) = Interface(definition, allFields, possibleTypes, interfaces)
    }

    class Scalar<T : Any>(
        kqlType: TypeDef.Scalar<T>
    ) : Type {

        override val kClass = kqlType.kClass

        override val kind: TypeKind = TypeKind.SCALAR

        override val name: String = kqlType.name

        override val description: String? = kqlType.description

        override val enumValues: List<__EnumValue>? = null

        override val inputFields: List<__InputValue>? = null

        override val ofType: Type? = null

        override val interfaces: List<Type>? = null

        override val fields: List<Field>? = null

        override val possibleTypes: List<Type>? = null

        override val specifiedByURL: String? = kqlType.specifiedByURL

        val coercion = kqlType.coercion
    }

    class Enum<T : kotlin.Enum<T>>(
        kqlType: TypeDef.Enumeration<T>
    ) : Type {

        val values = kqlType.values.map { it.toEnumValue() }

        override val kClass = kqlType.kClass

        override val kind: TypeKind = TypeKind.ENUM

        override val name: String = kqlType.name

        override val description: String? = kqlType.description

        override val enumValues: List<__EnumValue> = values

        override val inputFields: List<__InputValue>? = null

        override val ofType: Type? = null

        override val interfaces: List<Type>? = null

        override val fields: List<Field>? = null

        override val possibleTypes: List<Type>? = null

        override val specifiedByURL: String? = null
    }

    class Input<T : Any>(
        kqlType: TypeDef.Input<T>,
        override val inputFields: List<InputValue<*>> = emptyList()
    ) : Type {

        override val kClass = kqlType.kClass

        override val kind: TypeKind = TypeKind.INPUT_OBJECT

        override val name: String = kqlType.name

        override val description: String? = kqlType.description

        override val enumValues: List<__EnumValue>? = null

        override val ofType: Type? = null

        override val interfaces: List<Type>? = null

        override val fields: List<Field>? = null

        override val possibleTypes: List<Type>? = null

        override val specifiedByURL: String? = null
    }

    class Union(
        kqlType: TypeDef.Union,
        typenameResolver: Field,
        override val possibleTypes: List<Type>
    ) : ComplexType(listOf(typenameResolver)) {
        override val kClass: KClass<*>? = null

        override val kind: TypeKind = TypeKind.UNION

        override val name: String = kqlType.name

        override val description: String? = kqlType.description

        override val enumValues: List<__EnumValue>? = null

        override val inputFields: List<__InputValue>? = null

        override val ofType: Type? = null

        override val interfaces: List<Type>? = null

        override val fields: List<Field>? = null

        override fun isInstance(value: Any?): Boolean = false
    }

    class _Context : Type {
        override val kClass: KClass<*> = Context::class

        override val kind: TypeKind = TypeKind.OBJECT

        override val name: String? = null

        override val description: String? = null

        override val enumValues: List<__EnumValue>? = null

        override val inputFields: List<__InputValue>? = null

        override val ofType: Type? = null

        override val interfaces: List<Type>? = null

        override val fields: List<Field>? = null

        override val possibleTypes: List<Type>? = null

        override val specifiedByURL: String? = null
    }

    class _ExecutionNode : Type {
        override val kClass: KClass<*> = Execution.Node::class

        override val kind: TypeKind = TypeKind.OBJECT

        override val name: String? = null

        override val description: String? = null

        override val enumValues: List<__EnumValue>? = null

        override val inputFields: List<__InputValue>? = null

        override val ofType: Type? = null

        override val interfaces: List<Type>? = null

        override val fields: List<Field>? = null

        override val possibleTypes: List<Type>? = null

        override val specifiedByURL: String? = null
    }

    class NonNull(override val ofType: Type) : Type {

        override val kClass: KClass<*>? = null

        override val kind: TypeKind = TypeKind.NON_NULL

        override val name: String? = null

        override val description: String = "NonNull wrapper type"

        override val fields: List<Field>? = null

        override val interfaces: List<Type>? = null

        override val possibleTypes: List<Type>? = null

        override val enumValues: List<__EnumValue>? = null

        override val inputFields: List<__InputValue>? = null

        override val specifiedByURL: String? = null

        override fun isInstance(value: Any?): Boolean = false
    }

    class AList(elementType: Type, override val kClass: KClass<*>) : Type {

        override val ofType: Type = elementType

        override val kind: TypeKind = TypeKind.LIST

        override val name: String? = null

        override val description: String = "List wrapper type"

        override val fields: List<Field>? = null

        override val interfaces: List<Type>? = null

        override val possibleTypes: List<Type>? = null

        override val enumValues: List<__EnumValue>? = null

        override val inputFields: List<__InputValue>? = null

        override val specifiedByURL: String? = null

        override fun isInstance(value: Any?): Boolean = false
    }

    class RemoteEnum(
        override val name: String,
        override val description: String?,
        override val enumValues: List<__EnumValue>
    ) : Type {

        override val kClass: KClass<*>? = null

        override val kind: TypeKind = TypeKind.ENUM

        override val fields: List<Field>? = null

        override val interfaces: List<Type>? = null

        override val possibleTypes: List<Type>? = null

        override val inputFields: List<__InputValue>? = null

        override val ofType: Type? = null

        override val specifiedByURL: String? = null
    }

    class RemoteObject(
        override val name: String,
        override val description: String?,
        fields: List<Field>,
        override val interfaces: List<Type>? = emptyList()
    ) : ComplexType(fields) {

        override val kClass = null

        override val kind: TypeKind = TypeKind.OBJECT

        override val enumValues: List<__EnumValue>? = null

        override val inputFields: List<__InputValue>? = null

        override val ofType: Type? = null

        override val possibleTypes: List<Type>? = null

        fun withStitchedFields(stitchedFields: List<Field>): RemoteObject =
            RemoteObject(name, description, allFields + stitchedFields, interfaces)
    }

    class RemoteInterface(
        override val name: String,
        override val description: String?,
        fields: List<Field>,
        override val interfaces: List<Type>? = emptyList()
    ) : ComplexType(fields) {

        override val kClass = null

        override val kind: TypeKind = TypeKind.INTERFACE

        override val enumValues: List<__EnumValue>? = null

        override val inputFields: List<__InputValue>? = null

        override val ofType: Type? = null

        override val possibleTypes: List<Type>? = null
    }

    class RemoteInputObject(
        override val name: String,
        override val description: String?,
        override val inputFields: List<__InputValue> = emptyList()
    ) : Type {

        override val kClass = null

        override val kind: TypeKind = TypeKind.INPUT_OBJECT

        override val enumValues: List<__EnumValue>? = null

        override val ofType: Type? = null

        override val interfaces: List<Type>? = null

        override val fields: List<Field>? = null

        override val possibleTypes: List<Type>? = null

        override val specifiedByURL: String? = null
    }

    class RemoteScalar(override val name: String, override val description: String?, override val specifiedByURL: String?) : Type {

        override val kClass = null

        override val kind: TypeKind = TypeKind.SCALAR

        override val enumValues: List<__EnumValue>? = null

        override val inputFields: List<__InputValue>? = null

        override val ofType: Type? = null

        override val interfaces: List<Type>? = null

        override val fields: List<Field>? = null

        override val possibleTypes: List<Type>? = null
    }
}
