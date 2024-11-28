package com.apurebase.kgraphql.schema.introspection

enum class TypeKind {
    SCALAR,
    OBJECT,
    INTERFACE,
    UNION,
    ENUM,
    INPUT_OBJECT,

    // Wrapper types
    LIST,
    NON_NULL
}
