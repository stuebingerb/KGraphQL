package de.stuebingerb.kgraphql.schema

import com.fasterxml.jackson.core.JsonProcessingException
import de.stuebingerb.kgraphql.Context
import de.stuebingerb.kgraphql.RequestError
import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.configuration.SchemaConfiguration
import de.stuebingerb.kgraphql.request.CachingDocumentParser
import de.stuebingerb.kgraphql.request.DocumentParser
import de.stuebingerb.kgraphql.request.Introspection
import de.stuebingerb.kgraphql.request.RequestParser
import de.stuebingerb.kgraphql.request.VariablesJson
import de.stuebingerb.kgraphql.schema.execution.ParallelRequestExecutor
import de.stuebingerb.kgraphql.schema.execution.RequestExecutor
import de.stuebingerb.kgraphql.schema.introspection.__Schema
import de.stuebingerb.kgraphql.schema.model.ast.NameNode
import de.stuebingerb.kgraphql.schema.structure.RequestInterpreter
import de.stuebingerb.kgraphql.schema.structure.SchemaModel
import kotlinx.coroutines.coroutineScope

class DefaultSchema(
    override val configuration: SchemaConfiguration,
    internal val model: SchemaModel
) : Schema, __Schema by model {

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
    ): String = coroutineScope {
        try {
            if (!configuration.introspection && Introspection.isIntrospection(request)) {
                throw ValidationException("GraphQL introspection is not allowed")
            }

            val parsedVariables = try {
                variables
                    ?.let { VariablesJson.Defined(configuration.objectMapper.readTree(variables)) }
                    ?: VariablesJson.Empty()
            } catch (e: JsonProcessingException) {
                throw ValidationException("Malformed JSON in variables: ${e.originalMessage}", originalError = e)
            }

            val document = requestParser.parseDocument(request)

            requestExecutor.suspendExecute(
                plan = requestInterpreter.createExecutionPlan(document, operationName, parsedVariables),
                variables = parsedVariables,
                context = context
            )
        } catch (e: RequestError) {
            e.serialize()
        }
    }

    override fun printSchema() = SchemaPrinter().print(model)
}
