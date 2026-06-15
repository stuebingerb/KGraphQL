package de.stuebingerb.kgraphql.stitched

import de.stuebingerb.kgraphql.KGraphQL
import de.stuebingerb.kgraphql.request.Introspection
import de.stuebingerb.kgraphql.schema.dsl.SchemaBuilder
import de.stuebingerb.kgraphql.schema.introspection.__Schema
import de.stuebingerb.kgraphql.stitched.schema.structure.IntrospectedSchema

fun getRemoteSchema(builder: SchemaBuilder.() -> Unit): __Schema {
    val schema = KGraphQL.schema { builder() }
    val introspectionResponse = schema.executeBlocking(Introspection.query())
    return IntrospectedSchema.fromIntrospectionResponse(introspectionResponse)
}
