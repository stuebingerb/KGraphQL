package com.apurebase.kgraphql.stitched.schema.model

import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.model.MutableSchemaDefinition
import com.apurebase.kgraphql.schema.model.SchemaDefinition
import com.apurebase.kgraphql.stitched.schema.structure.Link
import com.apurebase.kgraphql.stitched.schema.structure.StitchedSchemaDefinition

/**
 * Intermediate, mutable data structure used to prepare [StitchedSchemaDefinition]
 * Performs basic validation (names duplication etc.) when methods for adding schema components are invoked
 */
class StitchedMutableSchemaDefinition : MutableSchemaDefinition() {
    private val remoteSchemas: MutableMap<String, __Schema> = mutableMapOf()
    private val links: MutableList<Link> = mutableListOf()

    override fun toSchemaDefinition(): SchemaDefinition {
        validateUnions()

        return StitchedSchemaDefinition(
            objects,
            queries,
            scalars,
            mutations,
            subscriptions,
            enums,
            unions,
            directives,
            inputObjects,
            remoteSchemas,
            links
        )
    }

    fun addRemoteSchema(url: String, schema: __Schema) {
        if (url in remoteSchemas.keys) {
            throw SchemaException("Cannot add remote schema with duplicated url $url")
        }
        remoteSchemas[url] = schema
    }

    fun addLink(link: Link) {
        if (links.any { it.typeName == link.typeName && it.fieldName == link.fieldName }) {
            throw SchemaException("Cannot add link with duplicated field ${link.fieldName} for type ${link.typeName}")
        }
        links.add(link)
    }
}
