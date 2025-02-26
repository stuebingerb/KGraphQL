package com.apurebase.kgraphql.stitched

import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.stitched.schema.dsl.StitchedSchemaBuilder

object StitchedKGraphQL {
    fun stitchedSchema(init: StitchedSchemaBuilder.() -> Unit): Schema {
        return StitchedSchemaBuilder().apply(init).build()
    }
}
