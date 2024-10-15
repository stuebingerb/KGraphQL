package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder

class KGraphQL {
    companion object {
        fun schema(init: SchemaBuilder.() -> Unit): Schema {
            return SchemaBuilder().apply(init).build()
        }
    }
}