package com.apurebase.kgraphql.stitched.schema.configuration

import com.apurebase.kgraphql.ExperimentalAPI
import com.apurebase.kgraphql.configuration.SchemaConfiguration
import com.apurebase.kgraphql.schema.execution.ArgumentTransformer
import com.apurebase.kgraphql.schema.execution.Executor
import com.apurebase.kgraphql.schema.execution.GenericTypeResolver
import com.apurebase.kgraphql.stitched.schema.execution.RemoteRequestExecutor
import com.fasterxml.jackson.databind.ObjectMapper
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
    executor: Executor,
    timeout: Long?,
    // allow schema introspection
    introspection: Boolean = true,
    genericTypeResolver: GenericTypeResolver,
    argumentTransformer: ArgumentTransformer,
    val remoteExecutor: RemoteRequestExecutor,
    val localUrl: String?
) : SchemaConfiguration(
    useCachingDocumentParser,
    documentParserCacheMaximumSize,
    objectMapper,
    useDefaultPrettyPrinter,
    coroutineDispatcher,
    wrapErrors,
    executor,
    timeout,
    introspection,
    genericTypeResolver,
    argumentTransformer
)
