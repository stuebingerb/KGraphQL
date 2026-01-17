package com.apurebase.kgraphql.schema

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.configuration.SchemaConfiguration
import com.apurebase.kgraphql.request.CachingDocumentParser
import com.apurebase.kgraphql.request.DocumentParser
import com.apurebase.kgraphql.request.Introspection
import com.apurebase.kgraphql.request.RequestParser
import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.schema.execution.ParallelRequestExecutor
import com.apurebase.kgraphql.schema.execution.RequestExecutor
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.model.ast.NameNode
import com.apurebase.kgraphql.schema.structure.LookupSchema
import com.apurebase.kgraphql.schema.structure.RequestInterpreter
import com.apurebase.kgraphql.schema.structure.SchemaModel
import com.apurebase.kgraphql.schema.structure.Type
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

class DefaultSchema(
    override val configuration: SchemaConfiguration,
    internal val model: SchemaModel
) : Schema, __Schema by model, LookupSchema {

    companion object {
        val OPERATION_NAME_PARAM = NameNode("operationName", null)
    }

    private val requestExecutor: RequestExecutor = ParallelRequestExecutor(this)

    private val requestInterpreter: RequestInterpreter = RequestInterpreter(model)
    private val requestParser: RequestParser = if (configuration.useCachingDocumentParser) {
        CachingDocumentParser(configuration.documentParserCacheMaximumSize)
    } else {
        DocumentParser()
    }

    override suspend fun execute(
        request: String,
        variables: String?,
        context: Context,
        operationName: String?,
    ): String = withContext(configuration.coroutineDispatcher) {
        if (!configuration.introspection && Introspection.isIntrospection(request)) {
            throw ValidationException("GraphQL introspection is not allowed")
        }

        val parsedVariables = variables
            ?.let { VariablesJson.Defined(configuration.objectMapper.readTree(variables)) }
            ?: VariablesJson.Empty()

        val document = requestParser.parseDocument(request)

        requestExecutor.suspendExecute(
            plan = requestInterpreter.createExecutionPlan(document, operationName, parsedVariables),
            variables = parsedVariables,
            context = context
        )
    }

    override fun printSchema() = SchemaPrinter().print(model)

    override fun typeByKClass(kClass: KClass<*>): Type? = model.queryTypes[kClass]

    override fun inputTypeByKClass(kClass: KClass<*>): Type? = model.inputTypes[kClass]

    override fun findTypeByName(name: String): Type? = model.allTypesByName[name]
}
