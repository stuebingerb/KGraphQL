package de.stuebingerb.kgraphql.stitched.schema.dsl

import de.stuebingerb.kgraphql.ExperimentalAPI
import de.stuebingerb.kgraphql.schema.Schema
import de.stuebingerb.kgraphql.schema.SchemaException
import de.stuebingerb.kgraphql.schema.dsl.SchemaBuilder
import de.stuebingerb.kgraphql.schema.dsl.SchemaConfigurationDSL
import de.stuebingerb.kgraphql.schema.introspection.__Schema
import de.stuebingerb.kgraphql.schema.model.SchemaDefinition
import de.stuebingerb.kgraphql.stitched.schema.configuration.StitchedSchemaConfiguration
import de.stuebingerb.kgraphql.stitched.schema.model.StitchedMutableSchemaDefinition
import de.stuebingerb.kgraphql.stitched.schema.structure.StitchedSchemaCompilation
import de.stuebingerb.kgraphql.stitched.schema.structure.StitchedSchemaDefinition
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
                ),
                localSchemaBuilder.description
            ).perform()
        }
    }

    private fun mergeSchemaDefinitions(local: SchemaDefinition, stitched: StitchedSchemaDefinition) =
        StitchedSchemaDefinition(
            objects = (local.objects + stitched.objects).distinctBy { it.kClass },
            queries = local.queries + stitched.queries,
            scalars = (local.scalars + stitched.scalars).distinctBy { it.kClass },
            mutations = local.mutations + stitched.mutations,
            subscriptions = local.subscriptions + stitched.subscriptions,
            enums = (local.enums + stitched.enums).distinctBy { it.kClass },
            unions = local.unions + stitched.unions,
            directives = (local.directives + stitched.directives).distinct(),
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
