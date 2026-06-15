package de.stuebingerb.kgraphql

import de.stuebingerb.kgraphql.schema.Schema
import de.stuebingerb.kgraphql.schema.dsl.SchemaBuilder

class KGraphQL {
    companion object {
        fun schema(init: SchemaBuilder.() -> Unit): Schema {
            return SchemaBuilder().apply(init).build()
        }
    }
}
