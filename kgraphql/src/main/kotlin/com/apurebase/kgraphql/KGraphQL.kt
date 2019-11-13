package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.dsl.SchemaBuilder


class KGraphQL {
    companion object {
        fun schema(init : SchemaBuilder<Unit>.() -> Unit) = SchemaBuilder(init).build()
    }
}