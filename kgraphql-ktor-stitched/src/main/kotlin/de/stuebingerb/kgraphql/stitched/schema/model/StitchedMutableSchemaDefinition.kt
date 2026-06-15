package de.stuebingerb.kgraphql.stitched.schema.model

import de.stuebingerb.kgraphql.schema.SchemaException
import de.stuebingerb.kgraphql.schema.introspection.__Schema
import de.stuebingerb.kgraphql.schema.model.MutableSchemaDefinition
import de.stuebingerb.kgraphql.schema.model.SchemaDefinition
import de.stuebingerb.kgraphql.stitched.schema.structure.StitchedProperty
import de.stuebingerb.kgraphql.stitched.schema.structure.StitchedSchemaDefinition

/**
 * Intermediate, mutable data structure used to prepare [StitchedSchemaDefinition]
 * Performs basic validation (names duplication etc.) when methods for adding schema components are invoked
 */
class StitchedMutableSchemaDefinition : MutableSchemaDefinition() {
    private val remoteSchemas: MutableMap<String, __Schema> = mutableMapOf()
    private val stitchedProperties: MutableList<StitchedProperty> = mutableListOf()

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
            stitchedProperties
        )
    }

    fun addRemoteSchema(url: String, schema: __Schema) {
        if (url in remoteSchemas.keys) {
            throw SchemaException("Cannot add remote schema with duplicated url '$url'")
        }
        remoteSchemas[url] = schema
    }

    fun addStitchedProperty(stitchedProperty: StitchedProperty) {
        if (stitchedProperties.any { it.typeName == stitchedProperty.typeName && it.fieldName == stitchedProperty.fieldName }) {
            throw SchemaException("Cannot add stitched field with duplicated name '${stitchedProperty.fieldName}' for type '${stitchedProperty.typeName}'")
        }
        stitchedProperties.add(stitchedProperty)
    }
}
