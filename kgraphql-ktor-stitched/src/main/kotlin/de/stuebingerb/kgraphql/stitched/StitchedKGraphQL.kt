package de.stuebingerb.kgraphql.stitched

import de.stuebingerb.kgraphql.ExperimentalAPI
import de.stuebingerb.kgraphql.schema.Schema
import de.stuebingerb.kgraphql.stitched.schema.dsl.StitchedSchemaBuilder

@ExperimentalAPI
object StitchedKGraphQL {
    fun stitchedSchema(init: StitchedSchemaBuilder.() -> Unit): Schema {
        return StitchedSchemaBuilder().apply(init).build()
    }
}
