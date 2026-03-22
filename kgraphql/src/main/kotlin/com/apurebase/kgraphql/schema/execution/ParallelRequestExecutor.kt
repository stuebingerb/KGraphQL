package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.ExecutionError
import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.RequestError
import com.apurebase.kgraphql.helpers.toJsonNode
import com.apurebase.kgraphql.mapIndexedParallel
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.ast.ArgumentNodes
import com.apurebase.kgraphql.schema.scalar.serializeScalar
import com.apurebase.kgraphql.schema.structure.Field
import com.apurebase.kgraphql.schema.structure.InputValue
import com.apurebase.kgraphql.schema.structure.Type
import com.apurebase.kgraphql.typeByPrimitiveArrayClass
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import nidomiro.kdataloader.DataLoader
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KProperty1

@Suppress("UNCHECKED_CAST") // For valid structure there is no risk of ClassCastException
class ParallelRequestExecutor(val schema: DefaultSchema) : RequestExecutor {

    class ExecutionContext(
        val variables: Variables,
        val requestContext: Context,
        val loaders: Map<Field.DataLoader<*, *, *>, DataLoader<Any?, *>>
    )

    private suspend fun ExecutionPlan.constructLoaders(): Map<Field.DataLoader<*, *, *>, DataLoader<Any?, *>> =
        coroutineScope {
            val loaders = mutableMapOf<Field.DataLoader<*, *, *>, DataLoader<Any?, *>>()

            suspend fun Collection<Execution>.inspect() {
                forEach { execution ->
                    when (execution) {
                        is Execution.Fragment -> execution.elements.inspect()
                        is Execution.Node -> {
                            execution.children.inspect()
                            if (execution.field is Field.DataLoader<*, *, *>) {
                                loaders[execution.field] =
                                    execution.field.loader.constructNew(coroutineContext.job) as DataLoader<Any?, *>
                            }
                        }
                    }
                }
            }
            operations.inspect()
            loaders
        }

    private val argumentsHandler = schema.configuration.argumentTransformer

    private val dispatcher = schema.configuration.coroutineDispatcher

    private val jsonNodeFactory = schema.configuration.objectMapper.nodeFactory

    private val objectWriter = schema.configuration.objectMapper.writer().let {
        if (schema.configuration.useDefaultPrettyPrinter) {
            it.withDefaultPrettyPrinter()
        } else {
            it
        }
    }

    override suspend fun suspendExecute(plan: ExecutionPlan, variables: VariablesJson, context: Context): String {
        val root = jsonNodeFactory.objectNode()
        val loaders = plan.constructLoaders()

        suspend fun executeOperation(operation: Execution.Node): Pair<Execution.Node, Deferred<JsonNode>?> =
            coroutineScope {
                val ctx = ExecutionContext(Variables(variables, operation.variables), context, loaders)
                if (shouldInclude(ctx, operation)) {
                    try {
                        operation to writeOperation(
                            isSubscription = plan.isSubscription,
                            ctx = ctx,
                            node = operation,
                            operation = operation.field as Field.Function<*, *>
                        )
                    } catch (e: ExecutionError) {
                        context.raiseError(e)
                        if (operation.field.returnType.isNullable()) {
                            operation to CompletableDeferred(jsonNodeFactory.nullNode())
                        } else {
                            operation to null
                        }
                    }
                } else {
                    operation to null
                }
            }

        // TODO: we might want a SerialRequestExecutor or at least rename *Parallel*RequestExecutor
        val resultMap = if (plan.executionMode == ExecutionMode.Normal) {
            plan.mapIndexedParallel(dispatcher) { _, operation -> executeOperation(operation) }.toMap()
        } else {
            plan.associate { operation -> executeOperation(operation) }
        }

        val data = if (resultMap.values.any { it != null }) {
            jsonNodeFactory.objectNode()
        } else {
            jsonNodeFactory.nullNode()
        }

        for (operation in plan) {
            // Remove all by skip/include directives
            if (resultMap[operation] != null) {
                (data as ObjectNode).merge(operation.aliasOrKey, resultMap[operation]!!.await())
            }
        }

        // https://spec.graphql.org/September2025/#note-19ca4
        // "When "errors" is present in an execution result, it may be helpful for it to appear first when serialized to make it more apparent that errors are present."
        // So let's put errors first
        if (context.errors.isNotEmpty()) {
            root.set<ArrayNode>("errors", context.errors.toJsonNode(schema.configuration.objectMapper))
        }
        root.set<ObjectNode>("data", data)

        return objectWriter.writeValueAsString(root)
    }

    private suspend fun <T> writeOperation(
        isSubscription: Boolean,
        ctx: ExecutionContext,
        node: Execution.Node,
        operation: FunctionWrapper<T>
    ): Deferred<JsonNode> {
        try {
            node.field.checkAccess(null, ctx.requestContext)
        } catch (e: Throwable) {
            return handleException(ctx, node, node.field.returnType, e)
        }

        try {
            val operationResult = operation.invoke(
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
        } catch (e: Throwable) {
            return handleException(ctx, node, node.field.returnType, e)
        }
    }

    private suspend fun <T> createUnionOperationNode(
        ctx: ExecutionContext,
        parent: T,
        node: Execution.Union,
        unionProperty: Field.Union<T>
    ): Deferred<JsonNode> {
        try {
            node.field.checkAccess(parent, ctx.requestContext)
        } catch (e: Throwable) {
            return handleException(ctx, node, node.field.returnType, e)
        }
        val operationResult: Any? = unionProperty.invoke(
            funName = unionProperty.name,
            receiver = parent,
            inputValues = node.field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        ).await()

        val possibleTypes = (unionProperty.returnType.unwrapped() as Type.Union).possibleTypes
        val returnType = possibleTypes.find { it.isInstance(operationResult) }

        if (returnType == null && unionProperty.returnType.isNotNullable()) {
            val expectedOneOf = possibleTypes.joinToString { it.name.toString() }
            throw ExecutionException(
                "Unexpected type of union property value, expected one of [$expectedOneOf] but was '$operationResult'",
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
    ): Deferred<JsonNode> {
        try {
            if (value == null || value is NullNode) {
                return CompletableDeferred(createNullNode(node, returnType))
            }

            val unboxed = schema.configuration.genericTypeResolver.unbox(value)
            if (unboxed !== value) {
                return createNode(ctx, unboxed, node, returnType)
            }

            return when {
                // Check value, not returnType, because this method can be invoked with element value
                value is Collection<*> || value is Array<*> || value is ArrayNode || value::class in typeByPrimitiveArrayClass.keys -> {
                    val values: Collection<*> = when (value) {
                        is Array<*> -> value.toList()
                        is ArrayNode -> value.toList()
                        is IntArray -> value.toList()
                        is ShortArray -> value.toList()
                        is LongArray -> value.toList()
                        is FloatArray -> value.toList()
                        is DoubleArray -> value.toList()
                        is CharArray -> value.toList()
                        is BooleanArray -> value.toList()
                        else -> value as Collection<*>
                    }
                    if (returnType.isList()) {
                        val unwrappedReturnType = returnType.unwrapList()
                        val valueNodes = values.mapIndexedParallel(dispatcher) { i, value ->
                            createNode(ctx, value, node.withIndex(i), unwrappedReturnType)
                        }
                        CompletableDeferred(valueNodes.fold(jsonNodeFactory.arrayNode(values.size)) { array, v ->
                            array.add(v.await())
                        })
                    } else {
                        handleException(
                            ctx,
                            node,
                            returnType,
                            ExecutionException(
                                "Invalid collection value for non-collection property '${node.aliasOrKey}'",
                                node
                            )
                        )
                    }
                }

                value is String -> CompletableDeferred(jsonNodeFactory.textNode(value))
                value is Int -> CompletableDeferred(jsonNodeFactory.numberNode(value))
                value is Float -> CompletableDeferred(jsonNodeFactory.numberNode(value))
                value is Double -> CompletableDeferred(jsonNodeFactory.numberNode(value))
                value is Boolean -> CompletableDeferred(jsonNodeFactory.booleanNode(value))
                value is Long -> CompletableDeferred(jsonNodeFactory.numberNode(value))
                value is Short -> CompletableDeferred(jsonNodeFactory.numberNode(value))

                value is Deferred<*> -> createNode(ctx, value.await(), node, returnType)

                node.children.isNotEmpty() -> createObjectNode(ctx, value, node, returnType)

                node is Execution.Union -> createObjectNode(
                    ctx,
                    value,
                    node.memberExecution(returnType),
                    returnType
                )

                // TODO: do we have to consider more? more validation e.g.?
                value is JsonNode -> CompletableDeferred(value)

                else -> CompletableDeferred(createSimpleValueNode(returnType, value, node))
            }
        } catch (e: ExecutionError) {
            return handleException(ctx, node, returnType, e)
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

            else -> throw ExecutionException("Invalid type '${unwrapped.name}'", node)
        }

    private fun createNullNode(node: Execution.Node, returnType: Type): NullNode {
        if (returnType.kind != TypeKind.NON_NULL) {
            return jsonNodeFactory.nullNode()
        } else {
            throw ExecutionError("Null result for non-nullable operation '${node.aliasOrKey}'", node)
        }
    }

    private suspend fun <T> createObjectNode(
        ctx: ExecutionContext,
        value: T,
        node: Execution.Node,
        type: Type
    ): Deferred<ObjectNode> {
        val objectNode = jsonNodeFactory.objectNode()
        val deferreds = node.children.mapIndexedParallel { _, child ->
            when (child) {
                is Execution.Fragment -> handleFragment(ctx, value, child.withParent(node))
                else -> handleProperty(ctx, value, child.withParent(node), type)?.let { mapOf(it) } ?: emptyMap()
            }
        }

        deferreds.forEach {
            it.forEach { (key, value) ->
                objectNode.merge(key, value.await())
            }
        }
        return CompletableDeferred(objectNode)
    }

    private suspend fun <T> handleProperty(
        ctx: ExecutionContext,
        value: T,
        child: Execution,
        type: Type
    ): Pair<String, Deferred<JsonNode>>? {
        when (child) {
            // Union is subclass of Node so check it first
            is Execution.Union -> {
                val field = checkNotNull(type.unwrapped()[child.key]) {
                    "Execution unit '${child.key}' is not contained by operation return type"
                }
                if (field is Field.Union<*>) {
                    return child.aliasOrKey to createUnionOperationNode(ctx, value, child, field as Field.Union<T>)
                } else {
                    throw ExecutionException(
                        "Unexpected non-union field for union execution node '${child.aliasOrKey}'",
                        child
                    )
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
                    "Execution unit '${child.key}' is not contained by operation return type"
                }
                return child.aliasOrKey to (createPropertyNode(ctx, value, child, field) ?: return null)
            }

            is Execution.Fragment -> throw UnsupportedOperationException("Handling fragments is not implemented yet")
        }
    }

    private suspend fun <T> handleFragment(
        ctx: ExecutionContext,
        value: T,
        container: Execution.Fragment
    ): Map<String, Deferred<JsonNode?>> {
        val include = shouldInclude(ctx, container)
        if (include) {
            val expectedType = container.condition.onType
            if (expectedType.kind == TypeKind.OBJECT || expectedType.kind == TypeKind.INTERFACE) {
                // TODO: for remote objects we now rely on the presence of the __typename. So maybe we should/need to automatically add it if not present already? Can this break something?
                if (expectedType.isInstance(value) || (value is JsonNode && value["__typename"].textValue() == expectedType.name)) {
                    val childElements = container.elements.flatMap { child ->
                        when (child) {
                            is Execution.Fragment -> handleFragment(ctx, value, child.withParent(container)).toList()
                            else -> listOfNotNull(handleProperty(ctx, value, child.withParent(container), expectedType))
                        }
                    }
                    val mapped: Map<String, Deferred<JsonNode?>> = childElements.fold(mutableMapOf()) { map, entry ->
                        map.merge(entry.first, entry.second)
                    }
                    return mapped
                }
            } else if (expectedType.kind == TypeKind.UNION) {
                // Union types do not define any fields, so children can only be fragments, cf.
                // https://spec.graphql.org/October2021/#sec-Unions
                return container.elements.filterIsInstance<Execution.Fragment>().flatMap {
                    handleFragment(ctx, value, it.withParent(container)).toList()
                }.fold(mutableMapOf()) { map, entry -> map.merge(entry.first, entry.second) }
            } else {
                error("Fragments can be specified on object types, interfaces, and unions")
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
    ): Deferred<JsonNode>? {
        val include = shouldInclude(ctx, node)
        if (include) {
            try {
                node.field.checkAccess(parentValue, ctx.requestContext)
            } catch (e: Throwable) {
                return handleException(ctx, node, node.field.returnType, e)
            }
            when {
                parentValue is JsonNode -> {
                    // This covers a) Field.Delegated but also b) *local* types that are returned from
                    //   remote queries, which can happen if we stitch to a local query (and where field
                    //   is Field.Kotlin<*, *> but parentValue is an ObjectNode)
                    // TODO: We might want to have separated types and then *only* deal with delegated fields here
                    // TODO: Can this break with functions or local types that are *actually* JsonNodes?
                    return createNode(ctx, (parentValue as JsonNode).get(node.aliasOrKey), node, field.returnType)
                }

                field is Field.Kotlin<*, *> -> {
                    val rawValue = (field.kProperty as KProperty1<T, *>).get(parentValue)
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

                field is Field.Function<*, *> -> {
                    return handleFunctionProperty(ctx, parentValue, node, field)
                }

                field is Field.DataLoader<*, *, *> -> {
                    return handleDataProperty(ctx, parentValue, node, field)
                }

                else -> error("Unexpected field type '$field', should be Field.Kotlin, Field.Function or Field.DataLoader")
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
    ): Deferred<JsonNode> {
        val preparedValue = field.kql.prepare.invoke(
            funName = field.name,
            receiver = parentValue,
            inputValues = field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        ).await()

        val value = ctx.loaders[field]!!.loadAsync(preparedValue)
        return createNode(ctx, value, node, field.returnType)
    }

    private suspend fun handleException(
        ctx: ExecutionContext,
        node: Execution.Node,
        returnType: Type,
        exception: Throwable
    ): CompletableDeferred<NullNode> {
        if (!schema.configuration.wrapErrors || exception is CancellationException) {
            throw exception
        }

        when (val handledError = schema.configuration.errorHandler.handleException(ctx.requestContext, node, exception)) {
            is RequestError -> throw handledError
            is ExecutionError -> {
                if (returnType.isNullable()) {
                    ctx.requestContext.raiseError(handledError)
                    return CompletableDeferred(createNullNode(node, returnType))
                } else {
                    // Propagate error to parent
                    throw handledError
                }
            }
        }
    }

    private suspend fun <T> handleFunctionProperty(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field.Function<*, *>
    ): Deferred<JsonNode> {
        try {
            val result = field.invoke(
                funName = field.name,
                receiver = parentValue,
                inputValues = field.arguments,
                args = node.arguments,
                executionNode = node,
                ctx = ctx
            )
            return createNode(ctx, result, node, field.returnType)
        } catch (e: Throwable) {
            return handleException(ctx, node, field.returnType, e)
        }
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
            ).await()?.include
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
    ): Deferred<T?> {
        val transformedArgs = argumentsHandler.transformArguments(
            funName,
            receiver,
            inputValues,
            args,
            ctx.variables,
            executionNode,
            ctx.requestContext,
            this@invoke
        ) ?: return CompletableDeferred(value = null)

        return when {
            hasReceiver -> CompletableDeferred(invoke(receiver, *transformedArgs.toTypedArray()))
            isSubscription -> {
                val subscriptionArgs = children.map { (it as Execution.Node).aliasOrKey }
                CompletableDeferred(invoke(transformedArgs, subscriptionArgs, objectWriter))
            }

            else -> CompletableDeferred(invoke(*transformedArgs.toTypedArray()))
        }
    }
}
