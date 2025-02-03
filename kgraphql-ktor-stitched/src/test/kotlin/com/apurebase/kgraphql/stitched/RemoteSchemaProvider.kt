package com.apurebase.kgraphql.stitched

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.request.Introspection
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.stitched.IntrospectedSchema

fun getRemoteSchema(builder: SchemaBuilder.() -> Unit): __Schema {
    val schema = KGraphQL.schema { builder() }
    val introspectionResponse = schema.executeBlocking(Introspection.query(Introspection.SpecLevel.October2021))
    return IntrospectedSchema.fromIntrospectionResponse(introspectionResponse)
}
