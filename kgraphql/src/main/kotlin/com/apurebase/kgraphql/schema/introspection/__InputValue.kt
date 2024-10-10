package com.apurebase.kgraphql.schema.introspection

interface __InputValue : __Described {

    val type: __Type

    val defaultValue: String?
}