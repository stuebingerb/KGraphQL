package com.apurebase.kgraphql.schema.introspection

import com.apurebase.kgraphql.schema.model.Depreciable

interface __Field : Depreciable, __Described {

    val type: __Type

    val args: List<__InputValue>
}
