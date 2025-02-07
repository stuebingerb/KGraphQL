package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.configuration.PluginConfiguration
import com.apurebase.kgraphql.configuration.SchemaConfiguration
import com.apurebase.kgraphql.schema.execution.Executor
import com.apurebase.kgraphql.schema.execution.GenericTypeResolver
import com.apurebase.kgraphql.schema.execution.RemoteRequestExecutor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.reflect.KClass

open class SchemaConfigurationDSL {
    var useDefaultPrettyPrinter: Boolean = false
    var useCachingDocumentParser: Boolean = true
    var objectMapper: ObjectMapper = jacksonObjectMapper()
    var documentParserCacheMaximumSize: Long = 1000L
    var acceptSingleValueAsArray: Boolean = true
    var coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default
    var wrapErrors: Boolean = true
    var executor: Executor = Executor.Parallel
    var timeout: Long? = null
    var introspection: Boolean = true
    var genericTypeResolver: GenericTypeResolver = GenericTypeResolver.DEFAULT
    var remoteExecutor: RemoteRequestExecutor? = null

    private val plugins: MutableMap<KClass<*>, Any> = mutableMapOf()

    fun install(plugin: PluginConfiguration) {
        val kClass = plugin::class
        require(plugins[kClass] == null)
        plugins[kClass] = plugin
    }

    internal fun update(block: SchemaConfigurationDSL.() -> Unit) = block()
    internal fun build(): SchemaConfiguration {
        objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, acceptSingleValueAsArray)
        return SchemaConfiguration(
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
            remoteExecutor = remoteExecutor
        )
    }
}
