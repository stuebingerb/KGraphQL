package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.execution.stitched.RemoteArgumentTransformer
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.ast.ArgumentNodes
import com.apurebase.kgraphql.schema.scalar.serializeScalar
import com.apurebase.kgraphql.schema.structure.Field
import com.apurebase.kgraphql.schema.structure.InputValue
import com.apurebase.kgraphql.schema.structure.Type
import com.apurebase.kgraphql.toMapAsync
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.coroutineScope
import nidomiro.kdataloader.DataLoader
import kotlin.reflect.KProperty1

@Suppress("UNCHECKED_CAST") // For valid structure there is no risk of ClassCastException
class ParallelRequestExecutor(val schema: DefaultSchema) : RequestExecutor {

    inner class ExecutionContext(
        val variables: Variables,
        val requestContext: Context
    )

    private val argumentsHandler = ArgumentTransformer(schema)

    private val jsonNodeFactory = JsonNodeFactory.instance

    private val dispatcher = schema.configuration.coroutineDispatcher

    private val objectWriter = schema.configuration.objectMapper.writer().let {
        if (schema.configuration.useDefaultPrettyPrinter) {
            it.withDefaultPrettyPrinter()
        } else {
            it
        }
    }

    private val remoteArgumentsHandler = RemoteArgumentTransformer(schema.model)

    override suspend fun suspendExecute(plan: ExecutionPlan, variables: VariablesJson, context: Context): String =
        coroutineScope {
            val root = jsonNodeFactory.objectNode()
            val data = root.putObject("data")

            val resultMap = plan.toMapAsync(dispatcher) {
                val ctx = ExecutionContext(Variables(schema, variables, it.variables), context)
                if (shouldInclude(ctx, it)) {
                    writeOperation(
                        isSubscription = plan.isSubscription,
                        ctx = ctx,
                        node = it,
                        operation = it.field as Field.Function<*, *>
                    )
                } else {
                    null
                }
            }

            for (operation in plan) {
                // Remove all by skip/include directives
                if (resultMap[operation] != null) {
                    data.set<JsonNode>(operation.aliasOrKey, resultMap[operation])
                }
            }

            objectWriter.writeValueAsString(root)
        }

    private suspend fun <T> writeOperation(
        isSubscription: Boolean,
        ctx: ExecutionContext,
        node: Execution.Node,
        operation: FunctionWrapper<T>
    ): JsonNode {
        node.field.checkAccess(null, ctx.requestContext)
        val operationResult: T? = operation.invoke(
            isSubscription = isSubscription,
            children = node.children,
            funName = node.field.name,
            receiver = null,
            inputValues = node.field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )

        return createNode(ctx, operationResult, node, node.field.returnType)
    }

    private suspend fun <T> createUnionOperationNode(
        ctx: ExecutionContext,
        parent: T,
        node: Execution.Union,
        unionProperty: Field.Union<T>
    ): JsonNode {
        node.field.checkAccess(parent, ctx.requestContext)

        val operationResult: Any? = unionProperty.invoke(
            funName = unionProperty.name,
            receiver = parent,
            inputValues = node.field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )

        val possibleTypes = (unionProperty.returnType.unwrapped() as Type.Union).possibleTypes
        val returnType = possibleTypes.find { it.isInstance(operationResult) }

        if (returnType == null && unionProperty.returnType.isNotNullable()) {
            val expectedOneOf = possibleTypes.joinToString { it.name.toString() }
            throw ExecutionException(
                "Unexpected type of union property value, expected one of [$expectedOneOf] but was $operationResult",
                node
            )
        }

        return createNode(ctx, operationResult, node, returnType ?: unionProperty.returnType)
    }

    private suspend fun <T> createNode(
        ctx: ExecutionContext,
        value: T?,
        node: Execution.Node,
        returnType: Type
    ): JsonNode {
        if (value == null || value is NullNode) {
            return createNullNode(node, returnType)
        }

        val unboxed = schema.configuration.genericTypeResolver.unbox(value)
        if (unboxed !== value) {
            return createNode(ctx, unboxed, node, returnType)
        }

        return when {
            // Check value, not returnType, because this method can be invoked with element value
            value is Collection<*> || value is Array<*> || value is ArrayNode -> {
                val values: Collection<*> = when (value) {
                    is Array<*> -> value.toList()
                    is ArrayNode -> value.toList()
                    else -> value as Collection<*>
                }
                if (returnType.isList()) {
                    val valuesMap = values.toMapAsync(dispatcher) {
                        createNode(ctx, it, node, returnType.unwrapList())
                    }
                    values.fold(jsonNodeFactory.arrayNode(values.size)) { array, v ->
                        array.add(valuesMap[v])
                    }
                } else {
                    throw ExecutionException("Invalid collection value for non-collection property", node)
                }
            }

            value is String -> jsonNodeFactory.textNode(value)
            value is Int -> jsonNodeFactory.numberNode(value)
            value is Float -> jsonNodeFactory.numberNode(value)
            value is Double -> jsonNodeFactory.numberNode(value)
            value is Boolean -> jsonNodeFactory.booleanNode(value)
            value is Long -> jsonNodeFactory.numberNode(value)
            value is Short -> jsonNodeFactory.numberNode(value)

            node.children.isNotEmpty() -> {
                createObjectNode(ctx, value, node, returnType)
            }

            node is Execution.Union -> {
                createObjectNode(ctx, value, node.memberExecution(returnType), returnType)
            }

            // TODO: do we have to consider more? more validation e.g.?
            value is JsonNode -> value

            else -> createSimpleValueNode(returnType, value, node)
        }
    }

    private fun <T> createSimpleValueNode(returnType: Type, value: T, node: Execution.Node): JsonNode =
        when (val unwrapped = returnType.unwrapped()) {
            is Type.Scalar<*> -> {
                serializeScalar(jsonNodeFactory, unwrapped, value, node)
            }

            is Type.Enum<*> -> {
                jsonNodeFactory.textNode(value.toString())
            }

            else -> throw ExecutionException("Invalid Type: ${unwrapped.name}", node)
        }

    private fun createNullNode(node: Execution.Node, returnType: Type): NullNode {
        if (returnType.kind != TypeKind.NON_NULL) {
            return jsonNodeFactory.nullNode()
        } else {
            throw ExecutionException("null result for non-nullable operation ${node.field.name}", node)
        }
    }

    private suspend fun <T> createObjectNode(
        ctx: ExecutionContext,
        value: T,
        node: Execution.Node,
        type: Type
    ): ObjectNode {
        val objectNode = jsonNodeFactory.objectNode()
        for (child in node.children) {
            when (child) {
                is Execution.Fragment -> objectNode.setAll<JsonNode>(handleFragment(ctx, value, child))
                else -> {
                    val (key, jsonNode) = handleProperty(ctx, value, child, type) ?: continue
                    objectNode.merge(key, jsonNode)
                }
            }
        }
        return objectNode
    }

    private suspend fun <T> handleProperty(
        ctx: ExecutionContext,
        value: T,
        child: Execution,
        type: Type
    ): Pair<String, JsonNode?>? {
        when (child) {
            // Union is subclass of Node so check it first
            is Execution.Union -> {
                val field = checkNotNull(type.unwrapped()[child.key]) {
                    "Execution unit ${child.key} is not contained by operation return type"
                }
                if (field is Field.Union<*>) {
                    return child.aliasOrKey to createUnionOperationNode(ctx, value, child, field as Field.Union<T>)
                } else {
                    throw ExecutionException("Unexpected non-union field for union execution node", child)
                }
            }

            is Execution.Remote -> {
                return child.aliasOrKey to handleFunctionProperty(
                    ctx,
                    value,
                    child,
                    child.field as Field.Function<*, *>
                )
            }

            is Execution.Node -> {
                val field = checkNotNull(type.unwrapped()[child.key]) {
                    "Execution unit ${child.key} is not contained by operation return type"
                }
                return child.aliasOrKey to (createPropertyNode(ctx, value, child, field) ?: return null)
            }

            else -> {
                throw UnsupportedOperationException("Handling containers is not implemented yet")
            }
        }
    }

    private suspend fun <T> handleFragment(
        ctx: ExecutionContext,
        value: T,
        container: Execution.Fragment
    ): Map<String, JsonNode?> {
        val expectedType = container.condition.onType
        val include = shouldInclude(ctx, container)

        if (include) {
            if (expectedType.kind == TypeKind.OBJECT || expectedType.kind == TypeKind.INTERFACE) {
                // TODO: for remote objects we now rely on the presence of the __typename. So maybe we should/need to automatically add it if not present already? Can this break something?
                if (expectedType.isInstance(value) || (value is JsonNode && value["__typename"].textValue() == expectedType.name)) {
                    return container.elements.flatMap { child ->
                        when (child) {
                            is Execution.Fragment -> handleFragment(ctx, value, child).toList()
                            else -> listOfNotNull(handleProperty(ctx, value, child, expectedType))
                        }
                    }.fold(mutableMapOf()) { map, entry -> map.merge(entry.first, entry.second) }
                }
            } else if (expectedType.kind == TypeKind.UNION) {
                // Union types do not define any fields, so children can only be fragments, cf.
                // https://spec.graphql.org/October2021/#sec-Unions
                return container.elements.filterIsInstance<Execution.Fragment>().flatMap {
                    handleFragment(ctx, value, it).toList()
                }.fold(mutableMapOf()) { map, entry -> map.merge(entry.first, entry.second) }
            } else {
                error("fragments can be specified on object types, interfaces, and unions")
            }
        }
        // Not included, or type condition is not matched
        return emptyMap()
    }

    private suspend fun <T> createPropertyNode(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field
    ): JsonNode? {
        val include = shouldInclude(ctx, node)
        node.field.checkAccess(parentValue, ctx.requestContext)

        if (include) {
            // TODO: this feels a bit hacky
            if (parentValue is JsonNode) {
                return createNode(ctx, (parentValue as JsonNode).get(node.aliasOrKey), node, field.returnType)
            }
            when (field) {
                is Field.Kotlin<*, *> -> {
                    val rawValue = try {
                        (field.kProperty as KProperty1<T, *>).get(parentValue)
                    } catch (e: IllegalArgumentException) {
                        throw ExecutionException(
                            "Couldn't retrieve '${field.kProperty.name}' from class ${parentValue}}",
                            node,
                            e
                        )
                    }
                    val value: Any? = field.transformation?.invoke(
                        funName = field.name,
                        receiver = rawValue,
                        inputValues = field.arguments,
                        args = node.arguments,
                        executionNode = node,
                        ctx = ctx
                    ) ?: rawValue
                    return createNode(ctx, value, node, field.returnType)
                }

                is Field.Function<*, *> -> {
                    return handleFunctionProperty(ctx, parentValue, node, field)
                }

                is Field.DataLoader<*, *, *> -> {
                    return handleDataProperty(ctx, parentValue, node, field)
                }

                else -> error("Unexpected field type: $field, should be Field.Kotlin, Field.Function or Field.DataLoader")
            }
        } else {
            return null
        }
    }

    private suspend fun <T> handleDataProperty(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field.DataLoader<*, *, *>
    ): JsonNode {
        val preparedValue = field.kql.prepare.invoke(
            funName = field.name,
            receiver = parentValue,
            inputValues = field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )

        // as this isn't the DataLoaderPreparedRequestExecutor. We'll use this instant workaround instead.
        val loader = field.loader.constructNew(null) as DataLoader<Any?, Any?>
        val value = loader.loadAsync(preparedValue)
        loader.dispatch()

        return createNode(ctx, value.await(), node, field.returnType)
    }

    private suspend fun <T> handleFunctionProperty(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field.Function<*, *>
    ): JsonNode {
        val result = field.invoke(
            funName = field.name,
            receiver = parentValue,
            inputValues = field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )
        return createNode(ctx, result, node, field.returnType)
    }

    private suspend fun shouldInclude(ctx: ExecutionContext, executionNode: Execution): Boolean {
        if (executionNode.directives?.isEmpty() == true) {
            return true
        }
        return executionNode.directives?.map { (directive, arguments) ->
            directive.execution.invoke(
                funName = directive.name,
                inputValues = directive.arguments,
                receiver = null,
                args = arguments,
                executionNode = executionNode,
                ctx = ctx
            )?.include
                ?: throw ExecutionException("Illegal directive implementation returning null result", executionNode)
        }?.reduce { acc, b -> acc && b } ?: true
    }

    internal suspend fun <T> FunctionWrapper<T>.invoke(
        isSubscription: Boolean = false,
        children: Collection<Execution> = emptyList(),
        funName: String,
        receiver: Any?,
        inputValues: List<InputValue<*>>,
        args: ArgumentNodes?,
        executionNode: Execution,
        ctx: ExecutionContext
    ): T? {
        val transformedArgs = if (executionNode is Execution.Remote) {
            remoteArgumentsHandler.transformArguments(
                receiver,
                inputValues,
                executionNode,
                ctx,
                (this as? Field.RemoteOperation<*, *>)?.field?.argsFromParent.orEmpty()
            ) ?: return null
        } else {
            argumentsHandler.transformArguments(
                funName,
                inputValues,
                args,
                ctx.variables,
                executionNode,
                ctx.requestContext
            )
        }

        // exceptions are not caught on purpose to pass up business logic errors
        return try {
            when {
                hasReceiver -> invoke(receiver, *transformedArgs.toTypedArray())
                isSubscription -> {
                    val subscriptionArgs = children.map { (it as Execution.Node).aliasOrKey }
                    invoke(transformedArgs, subscriptionArgs, objectWriter)
                }

                else -> invoke(*transformedArgs.toTypedArray())
            }
        } catch (e: Throwable) {
            if (schema.configuration.wrapErrors && e !is GraphQLError) {
                throw ExecutionException(e.message ?: "", node = executionNode, cause = e)
            } else {
                throw e
            }
        }
    }
}
