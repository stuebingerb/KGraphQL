package com.apurebase.kgraphql.schema

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.configuration.SchemaConfiguration
import com.apurebase.kgraphql.request.CachingDocumentParser
import com.apurebase.kgraphql.request.DocumentParser
import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.schema.execution.ParallelRequestExecutor
import com.apurebase.kgraphql.schema.execution.RequestExecutor
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.jol.Parser
import com.apurebase.kgraphql.schema.structure2.LookupSchema
import com.apurebase.kgraphql.schema.structure2.RequestInterpreter
import com.apurebase.kgraphql.schema.structure2.SchemaModel
import com.apurebase.kgraphql.schema.structure2.Type
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

class DefaultSchema (
        internal val configuration: SchemaConfiguration,
        internal val model : SchemaModel
) : Schema , __Schema by model, LookupSchema {

    companion object {
        const val OPERATION_NAME_PARAM = "operationName"
    }

    private val requestExecutor : RequestExecutor = ParallelRequestExecutor(this)

     private val requestInterpreter : RequestInterpreter = RequestInterpreter(model)

    override suspend fun execute(request: String, variables: String?, context: Context): String {
        val parsedVariables = variables
            ?.let { VariablesJson.Defined(configuration.objectMapper, variables) }
            ?: VariablesJson.Empty()

        val document = Parser(request).parseDocument()


        return requestExecutor.suspendExecute(
            plan = requestInterpreter.createExecutionPlan(document, parsedVariables),
            variables = parsedVariables,
            context = context
        )
    }

    override fun typeByKClass(kClass: KClass<*>): Type? = model.queryTypes[kClass]

    override fun typeByKType(kType: KType): Type? = typeByKClass(kType.jvmErasure)

    override fun inputTypeByKClass(kClass: KClass<*>): Type? = model.inputTypes[kClass]

    override fun inputTypeByKType(kType: KType): Type? = typeByKClass(kType.jvmErasure)

    override fun typeByName(name: String): Type? = model.queryTypesByName[name]

    override fun inputTypeByName(name: String): Type? = model.inputTypesByName[name]
}
