package com.apurebase.kgraphql.schema.introspection

import com.apurebase.kgraphql.schema.model.Depreciable

interface __InputValue : Depreciable, __Described {

    val type: __Type

    val defaultValue: String?
}
