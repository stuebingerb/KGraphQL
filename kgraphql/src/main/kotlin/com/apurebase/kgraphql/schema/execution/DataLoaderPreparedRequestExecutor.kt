package com.apurebase.kgraphql.schema.execution

import com.apurebase.deferredJson.DeferredJsonMap
import com.apurebase.deferredJson.deferredJsonBuilder
import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.GraphQLError
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import nidomiro.kdataloader.DataLoader
import kotlin.reflect.KProperty1

class DataLoaderPreparedRequestExecutor(val schema: DefaultSchema) : RequestExecutor {

    private val argumentsHandler = ArgumentTransformer()

    inner class ExecutionContext(
        val variables: Variables,
        val requestContext: Context,
        val loaders: Map<Field.DataLoader<*, *, *>, DataLoader<Any?, *>>
    )

    private suspend fun ExecutionPlan.constructLoaders(): Map<Field.DataLoader<*, *, *>, DataLoader<Any?, *>> =
        coroutineScope {
            val loaders = mutableMapOf<Field.DataLoader<*, *, *>, DataLoader<Any?, *>>()

            suspend fun Collection<Execution>.look() {
                forEach { ex ->
                    when (ex) {
                        is Execution.Fragment -> ex.elements.look()
                        is Execution.Node -> {
                            ex.children.look()
                            if (ex.field is Field.DataLoader<*, *, *>) {
                                loaders[ex.field] =
                                    ex.field.loader.constructNew(coroutineContext.job) as DataLoader<Any?, *>
                            }
                        }
                    }
                }
            }
            operations.look()
            loaders
        }

    private suspend fun <T> DeferredJsonMap.writeOperation(
        ctx: ExecutionContext,
        node: Execution.Node,
        operation: FunctionWrapper<T>
    ) {
        node.field.checkAccess(null, ctx.requestContext)
        val result: T? = operation.invoke(
            funName = node.field.name,
            receiver = null,
            inputValues = node.field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )

        applyKeyToElement(ctx, result, node, node.field.returnType, 1)
    }

    private fun Any?.toPrimitive(node: Execution.Node, returnType: Type): JsonElement = when {
        this == null -> createNullNode(node, returnType.unwrapList())
        this is Collection<*> || this is Array<*> -> when (this) {
            is Array<*> -> toList()
            else -> this as Collection<*>
        }.map { it.toPrimitive(node, returnType.unwrapList()) }.let(::JsonArray)

        this is String -> JsonPrimitive(this)
        this is Int -> JsonPrimitive(this)
        this is Float -> JsonPrimitive(this)
        this is Double -> JsonPrimitive(this)
        this is Boolean -> JsonPrimitive(this)
        this is Long -> JsonPrimitive(this)
        returnType.unwrapped() is Type.Enum<*> -> JsonPrimitive(toString())
        else -> error("Unexpected primitive class: $this")
    }

    private suspend fun <T> DeferredJsonMap.applyKeyToElement(
        ctx: ExecutionContext,
        value: T?,
        node: Execution.Node,
        returnType: Type,
        parentCount: Long
    ) {
        return when {
            value == null -> node.aliasOrKey toValue createNullNode(node, returnType)
            value is Collection<*> || value is Array<*> -> {
                if (returnType.isList()) {
                    val values = when (value) {
                        is Array<*> -> value.toList()
                        else -> value as Collection<*>
                    }

                    if (node.children.isEmpty()) {
                        node.aliasOrKey toDeferredArray {
                            values.map { addValue(it.toPrimitive(node, returnType)) }
                        }
                    } else {
                        node.aliasOrKey toDeferredArray {
                            values.map { v ->
                                when {
                                    v == null -> addValue(createNullNode(node, returnType.unwrapList()))
                                    node.children.isNotEmpty() -> addDeferredObj {
                                        this@addDeferredObj.applyObjectProperties(
                                            ctx = ctx,
                                            value = v,
                                            node = node,
                                            type = returnType.unwrapList(),
                                            parentCount = values.size.toLong()
                                        )
                                    }

                                    else -> error("Unknown error!")
                                }
                            }
                        }
                    }
                } else {
                    throw ExecutionException("Invalid collection value for non-collection property", node)
                }
            }

            value is String -> node.aliasOrKey toValue JsonPrimitive(value)
            value is Int -> node.aliasOrKey toValue JsonPrimitive(value)
            value is Float -> node.aliasOrKey toValue JsonPrimitive(value)
            value is Double -> node.aliasOrKey toValue JsonPrimitive(value)
            value is Boolean -> node.aliasOrKey toValue JsonPrimitive(value)
            value is Long -> node.aliasOrKey toValue JsonPrimitive(value)
            value is Short -> node.aliasOrKey toValue JsonPrimitive(value)
            value is Deferred<*> -> {
                deferredLaunch {
                    applyKeyToElement(ctx, value.await(), node, returnType, parentCount)
                }
            }

            node.children.isNotEmpty() -> node.aliasOrKey toDeferredObj {
                applyObjectProperties(ctx, value, node, returnType, parentCount)
            }

            node is Execution.Union -> node.aliasOrKey toDeferredObj {
                applyObjectProperties(ctx, value, node.memberExecution(returnType), returnType, parentCount)
            }

            else -> node.aliasOrKey toValue createSimpleValueNode(returnType, value, node)
        }
    }

    private fun <T> createSimpleValueNode(returnType: Type, value: T, node: Execution.Node): JsonElement {
        return when (val unwrapped = returnType.unwrapped()) {
            is Type.Scalar<*> -> {
                serializeScalar(unwrapped, value, node)
            }

            is Type.Enum<*> -> JsonPrimitive(value.toString())
            else -> throw ExecutionException("Invalid Type: ${unwrapped.name}", node)
        }
    }

    private suspend fun <T> DeferredJsonMap.applyObjectProperties(
        ctx: ExecutionContext,
        value: T,
        node: Execution.Node,
        type: Type,
        parentCount: Long
    ) {
        node.children.map { child ->
            when (child) {
                is Execution.Fragment -> handleFragment(ctx, value, child)
                else -> applyProperty(ctx, value, child, type, parentCount)
            }
        }
    }

    private suspend fun <T> DeferredJsonMap.handleFragment(
        ctx: ExecutionContext,
        value: T,
        container: Execution.Fragment
    ) {
        if (!shouldInclude(ctx, container)) {
            return
        }

        val expectedType = container.condition.onType
        if (expectedType.kind == TypeKind.OBJECT || expectedType.kind == TypeKind.INTERFACE) {
            if (expectedType.isInstance(value)) {
                container.elements.map { child ->
                    when (child) {
                        is Execution.Fragment -> handleFragment(ctx, value, child)
                        else -> applyProperty(ctx, value, child, expectedType, container.elements.size.toLong())
                    }
                }
            }
        } else if (expectedType.kind == TypeKind.UNION) {
            // Union types do not define any fields, so children can only be fragments, cf.
            // https://spec.graphql.org/October2021/#sec-Unions
            container.elements.filterIsInstance<Execution.Fragment>().map {
                handleFragment(ctx, value, it)
            }
        } else {
            error("fragments can be specified on object types, interfaces, and unions")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> DeferredJsonMap.applyProperty(
        ctx: ExecutionContext,
        value: T,
        child: Execution,
        type: Type,
        parentCount: Long
    ) {
        when (child) {
            is Execution.Union -> {
                val field = checkNotNull(type.unwrapped()[child.key]) {
                    "Execution unit ${child.key} is not contained by operation return type"
                }
                if (field is Field.Union<*>) {
                    createUnionOperationNode(ctx, value, child, field as Field.Union<T>, parentCount)
                } else {
                    throw ExecutionException("Unexpected non-union field for union execution node", child)
                }
            }

            is Execution.Node -> {
                val field = checkNotNull(type.unwrapped()[child.key]) {
                    "Execution unit ${child.key} is not contained by operation return type"
                }
                createPropertyNodeAsync(ctx, value, child, field, parentCount)
            }

            else -> throw UnsupportedOperationException("Whatever this is isn't supported!")
        }
    }

    private suspend fun <T> DeferredJsonMap.createUnionOperationNode(
        ctx: ExecutionContext,
        parent: T,
        node: Execution.Union,
        unionProperty: Field.Union<T>,
        parentCount: Long
    ) {
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

        applyKeyToElement(ctx, operationResult, node, returnType ?: unionProperty.returnType, parentCount)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> DeferredJsonMap.createPropertyNodeAsync(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field,
        parentCount: Long
    ) {
        node.field.checkAccess(parentValue, ctx.requestContext)
        if (!shouldInclude(ctx, node)) {
            return
        }

        when (field) {
            is Field.Kotlin<*, *> -> {
                val rawValue = (field.kProperty as KProperty1<T, *>).get(parentValue)
                val value: Any? = field.transformation?.invoke(
                    funName = field.name,
                    receiver = rawValue,
                    inputValues = field.arguments,
                    args = node.arguments,
                    executionNode = node,
                    ctx = ctx
                ) ?: rawValue

                applyKeyToElement(ctx, value, node, field.returnType, parentCount)
            }

            is Field.Function<*, *> -> {
                handleFunctionProperty(ctx, parentValue, node, field, parentCount)
            }

            is Field.DataLoader<*, *, *> -> {
                field as Field.DataLoader<T, *, *>
                handleDataPropertyAsync(ctx, parentValue, node, field, parentCount)
            }

            else -> error("Unexpected field type: $field, should be Field.Kotlin, Field.Function or Field.DataLoader")
        }
    }

    private suspend fun <T> DeferredJsonMap.handleDataPropertyAsync(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field.DataLoader<T, *, *>,
        parentCount: Long
    ) {
        val preparedValue = field.kql.prepare.invoke(
            funName = field.name,
            receiver = parentValue,
            inputValues = field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        ) // ?: TODO("Nullable prepare functions isn't supported")

        val value = ctx.loaders[field]!!.loadAsync(preparedValue)
        applyKeyToElement(ctx, value, node, field.returnType, parentCount)
    }

    private suspend fun <T> DeferredJsonMap.handleFunctionProperty(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field.Function<*, *>,
        parentCount: Long
    ) {
        val deferred = CompletableDeferred<Any?>()
        deferredLaunch {
            try {
                val res = field.invoke(
                    funName = field.name,
                    receiver = parentValue,
                    inputValues = field.arguments,
                    args = node.arguments,
                    executionNode = node,
                    ctx = ctx
                )
                deferred.complete(res)
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
            }
        }

        applyKeyToElement(ctx, deferred, node, field.returnType, parentCount)
    }

    override suspend fun suspendExecute(plan: ExecutionPlan, variables: VariablesJson, context: Context) =
        coroutineScope {
            val result = deferredJsonBuilder(timeout = plan.options.timeout ?: schema.configuration.timeout) {
                val ctx = ExecutionContext(
                    Variables(schema, variables, plan.firstOrNull { it.variables != null }?.variables),
                    context,
                    plan.constructLoaders(),
                )

                "data" toDeferredObj {
                    plan.forEach { node ->
                        if (shouldInclude(ctx, node)) {
                            writeOperation(ctx, node, node.field as Field.Function<*, *>)
                        }
                    }
                }
                ctx.loaders.values.map { it.dispatch() }
            }

            result.await().toString()
        }

    private fun createNullNode(node: Execution.Node, returnType: Type): JsonNull =
        if (returnType.kind != TypeKind.NON_NULL) {
            JsonNull
        } else {
            throw ExecutionException("null result for non-nullable operation ${node.field.name}", node)
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

    internal suspend operator fun <T> FunctionWrapper<T>.invoke(
        funName: String,
        receiver: Any?,
        inputValues: List<InputValue<*>>,
        args: ArgumentNodes?,
        executionNode: Execution,
        ctx: ExecutionContext
    ): T? {
        val transformedArgs = argumentsHandler.transformArguments(
            funName,
            receiver,
            inputValues,
            args,
            ctx.variables,
            executionNode,
            ctx.requestContext,
            this
        ) ?: return null

        return try {
            if (hasReceiver) {
                invoke(receiver, *transformedArgs.toTypedArray())
            } else {
                invoke(*transformedArgs.toTypedArray())
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

