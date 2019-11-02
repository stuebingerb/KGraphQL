package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.request.Arguments
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.TypeDef
import com.apurebase.kgraphql.schema.scalar.serializeScalar
import com.apurebase.kgraphql.schema.structure2.Field
import com.apurebase.kgraphql.schema.structure2.InputValue
import com.apurebase.kgraphql.schema.structure2.Type
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KProperty1


@Suppress("UNCHECKED_CAST") // For valid structure there is no risk of ClassCastException
class ParallelRequestExecutor(val schema: DefaultSchema) : RequestExecutor, CoroutineScope {

    data class ExecutionContext(val variables: Variables, val requestContext: Context)

    override val coroutineContext: CoroutineContext = Job()

    private val argumentsHandler = ArgumentsHandler(schema)

    private val jsonNodeFactory = JsonNodeFactory.instance

    private val dispatcher = schema.configuration.coroutineDispatcher

    private val objectWriter = schema.configuration.objectMapper.writer().let {
        if (schema.configuration.useDefaultPrettyPrinter) {
            it.withDefaultPrettyPrinter()
        } else {
            it
        }
    }

    override suspend fun suspendExecute(plan: ExecutionPlan, variables: VariablesJson, context: Context): String {
        val root = jsonNodeFactory.objectNode()
        val data = root.putObject("data")

        val resultMap = plan.toMapAsync {
            writeOperation(
                ctx = ExecutionContext(Variables(schema, variables, it.variables), context),
                node = it,
                operation = it.field as Field.Function<*, *>
            )
        }

        for (operation in plan) {
            data.set(operation.aliasOrKey, resultMap[operation])
        }

        return objectWriter.writeValueAsString(root)
    }

    override fun execute(plan: ExecutionPlan, variables: VariablesJson, context: Context): String = runBlocking {
        suspendExecute(plan, variables, context)
    }

    private suspend fun <T, R> Collection<T>.toMapAsync(block: suspend (T) -> R): Map<T, R> = coroutineScope {
        val channel = Channel<Pair<T, R>>()
        val jobs = map { item ->
            launch(dispatcher) {
                try {
                    val res = block(item)
                    channel.send(item to res)
                } catch (e: Exception) {
                    channel.close(e)
                }
            }
        }
        val resultMap = mutableMapOf<T, R>()
        repeat(size) {
            try {
                val (item, result) = channel.receive()
                resultMap[item] = result
            } catch (e: Exception) {
                jobs.forEach { job: Job -> job.cancel() }
                throw e
            }
        }

        channel.close()
        resultMap
    }

    private suspend fun <T> writeOperation(ctx: ExecutionContext, node: Execution.Node, operation: FunctionWrapper<T>): JsonNode {
        node.field.checkAccess(null, ctx.requestContext)
        val operationResult: T? = operation.invoke(
                funName = node.field.name,
                receiver = null,
                inputValues = node.field.arguments,
                args = node.arguments,
                ctx = ctx
        )

        return createNode(ctx, operationResult, node, node.field.returnType)
    }

    private suspend fun <T> createUnionOperationNode(ctx: ExecutionContext, parent: T, node: Execution.Union, unionProperty: Field.Union<T>): JsonNode {
        node.field.checkAccess(parent, ctx.requestContext)

        val operationResult: Any? = unionProperty.invoke(
                funName = unionProperty.name,
                receiver = parent,
                inputValues = node.field.arguments,
                args = node.arguments,
                ctx = ctx
        )

        val returnType = unionProperty.returnType.possibleTypes.find { it.isInstance(operationResult) }

        if (returnType == null && !unionProperty.nullable) throw ExecutionException(
                "Unexpected type of union property value, expected one of : ${unionProperty.type.possibleTypes}." +
                        " value was $operationResult"
        )

        return createNode(ctx, operationResult, node, returnType ?: unionProperty.returnType)
    }

    private suspend fun <T> createNode(ctx: ExecutionContext, value: T?, node: Execution.Node, returnType: Type): JsonNode {
        return when {
            value == null -> createNullNode(node, returnType)

            //check value, not returnType, because this method can be invoked with element value
            value is Collection<*> -> {
                if (returnType.isList()) {
                    val valuesMap = value.toMapAsync {
                        createNode(ctx, it, node, returnType.unwrapList())
                    }
                    value.fold(jsonNodeFactory.arrayNode(value.size)) { array, v ->
                        array.add(valuesMap[v])
                    }
                } else {
                    throw ExecutionException("Invalid collection value for non collection property")
                }
            }
            value is String -> jsonNodeFactory.textNode(value)
            value is Int -> jsonNodeFactory.numberNode(value)
            value is Float -> jsonNodeFactory.numberNode(value)
            value is Double -> jsonNodeFactory.numberNode(value)
            value is Boolean -> jsonNodeFactory.booleanNode(value)
            value is Long -> jsonNodeFactory.numberNode(value)
            //big decimal etc?

            node.children.isNotEmpty() -> createObjectNode(ctx, value, node, returnType)
            node is Execution.Union -> {
                createObjectNode(ctx, value, node.memberExecution(returnType), returnType)
            }
            else -> createSimpleValueNode(returnType, value)
        }
    }

    private fun <T> createSimpleValueNode(returnType: Type, value: T): JsonNode {
        val unwrapped = returnType.unwrapped()
        return when (unwrapped) {
            is Type.Scalar<*> -> {
                serializeScalar(jsonNodeFactory, unwrapped, value)
            }
            is Type.Enum<*> -> {
                jsonNodeFactory.textNode(value.toString())
            }
            is TypeDef.Object<*> -> throw ExecutionException("Cannot handle object return type, schema structure exception")
            else -> throw ExecutionException("Invalid Type:  ${returnType.name}")
        }
    }

    private fun createNullNode(node: Execution.Node, returnType: Type): NullNode {
        if (returnType !is Type.NonNull) {
            return jsonNodeFactory.nullNode()
        } else {
            throw ExecutionException("null result for non-nullable operation ${node.field}")
        }
    }

    private suspend fun <T> createObjectNode(ctx: ExecutionContext, value: T, node: Execution.Node, type: Type): ObjectNode {
        val objectNode = jsonNodeFactory.objectNode()
        for (child in node.children) {
            if (child is Execution.Fragment) {
                objectNode.setAll(handleFragment(ctx, value, child))
            } else {
                val (key, jsonNode) = handleProperty(ctx, value, child, type)
                objectNode.set(key, jsonNode)
            }
        }
        return objectNode
    }

    private suspend fun <T> handleProperty(ctx: ExecutionContext, value: T, child: Execution, type: Type): Pair<String, JsonNode?> {
        when (child) {
            //Union is subclass of Node so check it first
            is Execution.Union -> {
                val field = type.unwrapped()[child.key]
                        ?: throw IllegalStateException("Execution unit ${child.key} is not contained by operation return type")
                if (field is Field.Union<*>) {
                    return child.aliasOrKey to createUnionOperationNode(ctx, value, child, field as Field.Union<T>)
                } else {
                    throw ExecutionException("Unexpected non-union field for union execution node")
                }
            }
            is Execution.Node -> {
                val field = type.unwrapped()[child.key]
                        ?: throw IllegalStateException("Execution unit ${child.key} is not contained by operation return type")
                return child.aliasOrKey to createPropertyNode(ctx, value, child, field)
            }
            else -> {
                throw UnsupportedOperationException("Handling containers is not implemented yet")
            }
        }
    }

    private suspend fun <T> handleFragment(ctx: ExecutionContext, value: T, container: Execution.Fragment): Map<String, JsonNode?> {
        val expectedType = container.condition.type
        val include = determineInclude(ctx, container.directives)

        if (include) {
            if (expectedType.kind == TypeKind.OBJECT || expectedType.kind == TypeKind.INTERFACE) {
                if (expectedType.isInstance(value)) {
                    return container.elements.flatMap { child ->
                        when (child) {
                            is Execution.Fragment -> handleFragment(ctx, value, child).toList()
                            else -> listOf(handleProperty(ctx, value, child, expectedType))
                        }
                    }.fold(mutableMapOf()) { map, entry -> map.merge(entry.first, entry.second) }
                }
            } else {
                throw IllegalStateException("fragments can be specified on object types, interfaces, and unions")
            }
        }
        //not included, or type condition is not matched
        return emptyMap()
    }

    private suspend fun <T> createPropertyNode(ctx: ExecutionContext, parentValue: T, node: Execution.Node, field: Field): JsonNode? {
        val include = determineInclude(ctx, node.directives)
        node.field.checkAccess(parentValue, ctx.requestContext)

        if (include) {
            when (field) {
                is Field.Kotlin<*, *> -> {
                    field.kProperty as KProperty1<T, *>
                    val rawValue = field.kProperty.get(parentValue)
                    val value: Any?
                    value = if (field.transformation != null) {
                        field.transformation.invoke(
                                funName = field.name,
                                receiver = rawValue,
                                inputValues = field.arguments,
                                args = node.arguments,
                                ctx = ctx
                        )
                    } else {
                        rawValue
                    }
                    return createNode(ctx, value, node, field.returnType)
                }
                is Field.Function<*, *> -> {
                    return handleFunctionProperty(ctx, parentValue, node, field)
                }
                else -> {
                    throw Exception("Unexpected field type: $field, should be Field.Kotlin or Field.Function")
                }
            }
        } else {
            return null
        }
    }

    suspend fun <T> handleFunctionProperty(ctx: ExecutionContext, parentValue: T, node: Execution.Node, field: Field.Function<*, *>): JsonNode {
        val result = field.invoke(
                funName = field.name,
                receiver = parentValue,
                inputValues = field.arguments,
                args = node.arguments,
                ctx = ctx
        )
        return createNode(ctx, result, node, field.returnType)
    }

    private suspend fun determineInclude(ctx: ExecutionContext, directives: Map<Directive, Arguments?>?): Boolean {
        return directives?.map { (directive, arguments) ->
            directive.execution.invoke(
                    funName = directive.name,
                    inputValues = directive.arguments,
                    receiver = null,
                    args = arguments,
                    ctx = ctx
            )?.include
                    ?: throw ExecutionException("Illegal directive implementation returning null result")
        }?.reduce { acc, b -> acc && b } ?: true
    }

    private suspend fun <T> FunctionWrapper<T>.invoke(
            funName: String,
            receiver: Any?,
            inputValues: List<InputValue<*>>,
            args: Arguments?,
            ctx: ExecutionContext
    ): T? {
        val transformedArgs = argumentsHandler.transformArguments(funName, inputValues, args, ctx.variables, ctx.requestContext)

        //exceptions are not caught on purpose to pass up business logic errors
        return if (hasReceiver) {
            invoke(receiver, *transformedArgs.toTypedArray())
        } else {
            invoke(*transformedArgs.toTypedArray())
        }
    }

}
