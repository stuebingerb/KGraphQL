package com.apurebase.kgraphql.schema.directive

enum class DirectiveLocation {
    // ExecutableDirectiveLocation
    QUERY,
    MUTATION,
    SUBSCRIPTION,
    FIELD,
    FRAGMENT_DEFINITION,
    FRAGMENT_SPREAD,
    INLINE_FRAGMENT,
    VARIABLE_DEFINITION,

    // TypeSystemDirectiveLocation
    SCHEMA,
    SCALAR,
    OBJECT,
    FIELD_DEFINITION,
    ARGUMENT_DEFINITION,
    INTERFACE,
    UNION,
    ENUM,
    ENUM_VALUE,
    INPUT_OBJECT,
    INPUT_FIELD_DEFINITION;

    companion object {
        fun from(str: String) = str.lowercase().let { lowered ->
            entries.firstOrNull { it.name.lowercase() == lowered }
        }
    }
}
