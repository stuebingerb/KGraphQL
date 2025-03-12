package com.apurebase.kgraphql.stitched.schema.dsl

import com.apurebase.kgraphql.ExperimentalAPI
import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.apurebase.kgraphql.schema.dsl.SchemaConfigurationDSL
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.model.SchemaDefinition
import com.apurebase.kgraphql.stitched.schema.configuration.StitchedSchemaConfiguration
import com.apurebase.kgraphql.stitched.schema.model.StitchedMutableSchemaDefinition
import com.apurebase.kgraphql.stitched.schema.structure.StitchedSchemaCompilation
import com.apurebase.kgraphql.stitched.schema.structure.StitchedSchemaDefinition
import kotlinx.coroutines.runBlocking

/**
 * StitchedSchemaBuilder exposes rich DSL to setup stitched GraphQL schema
 */
@ExperimentalAPI
class StitchedSchemaBuilder {
    private val model = StitchedMutableSchemaDefinition()
    private val localSchemaBuilder = SchemaBuilder()
    private var localSchemaBlock: (SchemaBuilder.() -> Unit)? = null

    var configuration: SchemaConfigurationDSL = StitchedSchemaConfigurationDSL()

    fun configure(block: StitchedSchemaConfigurationDSL.() -> Unit) {
        @Suppress("UNCHECKED_CAST")
        configuration.update(block as SchemaConfigurationDSL.() -> Unit)
    }

    fun localSchema(block: SchemaBuilder.() -> Unit) {
        if (localSchemaBlock == null) {
            localSchemaBlock = block
        } else {
            throw SchemaException("Local schema already defined")
        }
    }

    fun build(): Schema {
        return runBlocking {
            localSchemaBlock?.let { localSchemaBuilder.apply(it) }
            StitchedSchemaCompilation(
                configuration.build() as StitchedSchemaConfiguration,
                mergeSchemaDefinitions(
                    localSchemaBuilder.schemaDefinition,
                    model.toSchemaDefinition() as StitchedSchemaDefinition
                )
            ).perform()
        }
    }

    private fun mergeSchemaDefinitions(local: SchemaDefinition, stitched: StitchedSchemaDefinition) =
        StitchedSchemaDefinition(
            objects = local.objects + stitched.objects,
            queries = local.queries + stitched.queries,
            scalars = local.scalars + stitched.scalars,
            mutations = local.mutations + stitched.mutations,
            subscriptions = local.subscriptions + stitched.subscriptions,
            enums = local.enums + stitched.enums,
            unions = local.unions + stitched.unions,
            directives = local.directives + stitched.directives,
            inputObjects = local.inputObjects + stitched.inputObjects,
            remoteSchemas = stitched.remoteSchemas,
            stitchedProperties = stitched.stitchedProperties
        )

    //================================================================================
    // STITCHING
    //================================================================================

    fun remoteSchema(url: String, block: () -> __Schema) {
        model.addRemoteSchema(url, block.invoke())
    }

    fun type(name: String, block: StitchedTypeDSL.() -> Unit) {
        val stitchedProperties = StitchedTypeDSL(name).apply(block).stitchedProperties
        stitchedProperties.forEach { model.addStitchedProperty(it) }
    }
}
