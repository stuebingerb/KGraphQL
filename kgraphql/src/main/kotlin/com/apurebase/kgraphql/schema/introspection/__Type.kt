package com.apurebase.kgraphql.schema.introspection

/**
 * GraphQL introspection system defines __Type to represent all of TypeKinds
 * If some field does not apply to given type, it returns null
 */
interface __Type {
    val kind: TypeKind
    val name: String?
    val description: String?

    // OBJECT and INTERFACE only
    val fields: List<__Field>?

    // OBJECT and INTERFACE only
    val interfaces: List<__Type>?

    // INTERFACE and UNION only
    val possibleTypes: List<__Type>?

    // ENUM only
    val enumValues: List<__EnumValue>?

    // INPUT_OBJECT only
    val inputFields: List<__InputValue>?

    // NON_NULL and LIST only
    val ofType: __Type?

    fun typeReference(): String = when (kind) {
        TypeKind.NON_NULL -> "${ofType?.typeReference()}!"
        TypeKind.LIST -> "[${ofType?.typeReference()}]"
        else -> name ?: ""
    }

    fun unwrapped(): __Type = when (kind) {
        TypeKind.NON_NULL, TypeKind.LIST -> (ofType as __Type).unwrapped()
        else -> this
    }
}
