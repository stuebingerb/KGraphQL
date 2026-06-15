package de.stuebingerb.kgraphql.stitched.schema.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import de.stuebingerb.kgraphql.ExperimentalAPI
import de.stuebingerb.kgraphql.configuration.SchemaConfiguration
import de.stuebingerb.kgraphql.schema.execution.ArgumentTransformer
import de.stuebingerb.kgraphql.schema.execution.ErrorHandler
import de.stuebingerb.kgraphql.schema.execution.GenericTypeResolver
import de.stuebingerb.kgraphql.stitched.schema.execution.RemoteRequestExecutor
import kotlinx.coroutines.CoroutineDispatcher

@ExperimentalAPI
class StitchedSchemaConfiguration(
    // document parser caching mechanisms
    useCachingDocumentParser: Boolean,
    documentParserCacheMaximumSize: Long,
    // jackson features
    objectMapper: ObjectMapper,
    useDefaultPrettyPrinter: Boolean,
    // execution
    coroutineDispatcher: CoroutineDispatcher,
    wrapErrors: Boolean,
    // allow schema introspection
    introspection: Boolean = true,
    genericTypeResolver: GenericTypeResolver,
    argumentTransformer: ArgumentTransformer,
    errorHandler: ErrorHandler,
    val remoteExecutor: RemoteRequestExecutor,
    val localUrl: String?
) : SchemaConfiguration(
    useCachingDocumentParser,
    documentParserCacheMaximumSize,
    objectMapper,
    useDefaultPrettyPrinter,
    coroutineDispatcher,
    wrapErrors,
    introspection,
    genericTypeResolver,
    argumentTransformer,
    errorHandler
)
