package com.apurebase.kgraphql.stitched.schema.dsl

import com.apurebase.kgraphql.schema.dsl.SchemaConfigurationDSL
import com.apurebase.kgraphql.stitched.schema.configuration.StitchedSchemaConfiguration
import com.apurebase.kgraphql.stitched.schema.execution.RemoteArgumentTransformer
import com.apurebase.kgraphql.stitched.schema.execution.RemoteRequestExecutor
import com.fasterxml.jackson.databind.DeserializationFeature

open class StitchedSchemaConfigurationDSL : SchemaConfigurationDSL() {
    // Remote executor has to be set for remote schemas
    var remoteExecutor: RemoteRequestExecutor? = null

    // Local url has to be set when stitching local queries (only)
    var localUrl: String? = null

    override fun build(): StitchedSchemaConfiguration {
        objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, acceptSingleValueAsArray)
        return StitchedSchemaConfiguration(
            useCachingDocumentParser = useCachingDocumentParser,
            documentParserCacheMaximumSize = documentParserCacheMaximumSize,
            objectMapper = objectMapper,
            useDefaultPrettyPrinter = useDefaultPrettyPrinter,
            coroutineDispatcher = coroutineDispatcher,
            wrapErrors = wrapErrors,
            executor = executor,
            timeout = timeout,
            introspection = introspection,
            plugins = plugins,
            genericTypeResolver = genericTypeResolver,
            argumentTransformer = RemoteArgumentTransformer(),
            remoteExecutor = requireNotNull(remoteExecutor) { "Remote executor not defined" },
            localUrl = localUrl
        )
    }
}
