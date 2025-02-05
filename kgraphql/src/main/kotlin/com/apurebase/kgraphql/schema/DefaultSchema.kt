package com.apurebase.kgraphql.schema

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.configuration.SchemaConfiguration
import com.apurebase.kgraphql.function.memoized
import com.apurebase.kgraphql.request.Introspection
import com.apurebase.kgraphql.request.Parser
import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.schema.execution.DataLoaderPreparedRequestExecutor
import com.apurebase.kgraphql.schema.execution.ExecutionOptions
import com.apurebase.kgraphql.schema.execution.Executor
import com.apurebase.kgraphql.schema.execution.Executor.DataLoaderPrepared
import com.apurebase.kgraphql.schema.execution.Executor.Parallel
import com.apurebase.kgraphql.schema.execution.ParallelRequestExecutor
import com.apurebase.kgraphql.schema.execution.RequestExecutor
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.model.ast.DocumentNode
import com.apurebase.kgraphql.schema.model.ast.NameNode
import com.apurebase.kgraphql.schema.structure.LookupSchema
import com.apurebase.kgraphql.schema.structure.RequestInterpreter
import com.apurebase.kgraphql.schema.structure.SchemaModel
import com.apurebase.kgraphql.schema.structure.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

class DefaultSchema(
    override val configuration: SchemaConfiguration,
    internal val model: SchemaModel
) : Schema, __Schema by model, LookupSchema, CoroutineScope {

    companion object {
        val OPERATION_NAME_PARAM = NameNode("operationName", null)
    }

    private val defaultRequestExecutor: RequestExecutor = getExecutor(configuration.executor)

    private fun getExecutor(executor: Executor) = when (executor) {
        Parallel -> ParallelRequestExecutor(this)
        DataLoaderPrepared -> DataLoaderPreparedRequestExecutor(this)
    }

    private val requestInterpreter: RequestInterpreter = RequestInterpreter(model)

    private val parseRequest: (suspend (String) -> DocumentNode) =
        if (configuration.useCachingDocumentParser) {
            ::parseDocument.memoized(
                this,
                configuration.documentParserCacheMaximumSize
            )
        } else {
            ::parseDocument
        }

    override suspend fun execute(
        request: String,
        variables: String?,
        context: Context,
        options: ExecutionOptions,
        operationName: String?,
    ): String = coroutineScope {
        if (!configuration.introspection && Introspection.isIntrospection(request)) {
            throw ValidationException("GraphQL introspection is not allowed")
        }

        val parsedVariables = variables
            ?.let { VariablesJson.Defined(configuration.objectMapper, variables) }
            ?: VariablesJson.Empty()

        val document = parseRequest(request)

        val executor = options.executor?.let(this@DefaultSchema::getExecutor) ?: defaultRequestExecutor

        executor.suspendExecute(
            plan = requestInterpreter.createExecutionPlan(document, operationName, parsedVariables, options),
            variables = parsedVariables,
            context = context
        )
    }

    override fun printSchema() = SchemaPrinter().print(model)

    override fun typeByKClass(kClass: KClass<*>): Type? = model.queryTypes[kClass]

    override fun inputTypeByKClass(kClass: KClass<*>): Type? = model.inputTypes[kClass]

    override fun findTypeByName(name: String): Type? = model.allTypesByName[name]

    @Suppress("RedundantSuspendModifier")
    private suspend fun parseDocument(input: String): DocumentNode = Parser(input).parseDocument()

    override val coroutineContext: CoroutineContext = configuration.coroutineDispatcher
}
