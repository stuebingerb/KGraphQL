package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.configuration.SchemaConfiguration
import com.apurebase.kgraphql.schema.execution.ArgumentTransformer
import com.apurebase.kgraphql.schema.execution.GenericTypeResolver
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

open class SchemaConfigurationDSL {
    var useDefaultPrettyPrinter: Boolean = false
    var useCachingDocumentParser: Boolean = true
    var objectMapper: ObjectMapper = jacksonObjectMapper()
    var documentParserCacheMaximumSize: Long = 1000L
    var acceptSingleValueAsArray: Boolean = true
    var coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default
    var wrapErrors: Boolean = true
    var introspection: Boolean = true
    var genericTypeResolver: GenericTypeResolver = GenericTypeResolver.DEFAULT

    fun update(block: SchemaConfigurationDSL.() -> Unit) = block()
    open fun build(): SchemaConfiguration {
        objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, acceptSingleValueAsArray)
        return SchemaConfiguration(
            useCachingDocumentParser = useCachingDocumentParser,
            documentParserCacheMaximumSize = documentParserCacheMaximumSize,
            objectMapper = objectMapper,
            useDefaultPrettyPrinter = useDefaultPrettyPrinter,
            coroutineDispatcher = coroutineDispatcher,
            wrapErrors = wrapErrors,
            introspection = introspection,
            genericTypeResolver = genericTypeResolver,
            argumentTransformer = ArgumentTransformer()
        )
    }
}
