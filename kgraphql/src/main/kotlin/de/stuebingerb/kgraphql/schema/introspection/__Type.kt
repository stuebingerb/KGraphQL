package de.stuebingerb.kgraphql.schema.introspection

/**
 * GraphQL introspection system defines __Type to represent all of TypeKinds
 * If some field does not apply to given type, it returns null
 */
interface __Type : Describable {
    val kind: TypeKind
    val name: String?

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

    // SCALAR only
    val specifiedByURL: String?

    fun typeReference(): String = when (kind) {
        TypeKind.NON_NULL -> "${ofType?.typeReference()}!"
        TypeKind.LIST -> "[${ofType?.typeReference()}]"
        else -> name ?: ""
    }

    fun unwrapped(): __Type = when (kind) {
        TypeKind.NON_NULL, TypeKind.LIST -> (ofType as __Type).unwrapped()
        else -> this
    }

    // https://spec.graphql.org/September2025/#IsInputType()
    fun isInputType(): Boolean = when (kind) {
        TypeKind.NON_NULL, TypeKind.LIST -> unwrapped().isInputType()
        TypeKind.SCALAR, TypeKind.ENUM, TypeKind.INPUT_OBJECT -> true
        else -> false
    }

    // https://spec.graphql.org/September2025/#IsOutputType()
    fun isOutputType(): Boolean = when (kind) {
        TypeKind.NON_NULL, TypeKind.LIST -> unwrapped().isOutputType()
        TypeKind.SCALAR, TypeKind.OBJECT, TypeKind.INTERFACE, TypeKind.UNION, TypeKind.ENUM -> true
        else -> false
    }
}
