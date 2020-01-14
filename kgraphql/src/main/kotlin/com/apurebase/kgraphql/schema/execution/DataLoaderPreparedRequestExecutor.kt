package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.ast.ArgumentNodes
import com.apurebase.kgraphql.schema.model.ast.SelectionNode
import com.apurebase.kgraphql.schema.structure.Field
import com.apurebase.kgraphql.schema.structure.InputValue
import com.apurebase.kgraphql.schema.structure.Type
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import nidomiro.kdataloader.DataLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KProperty1


class DataLoaderPreparedRequestExecutor(val schema: DefaultSchema) : RequestExecutor {

    private val argumentsHandler = ArgumentsHandler(schema)
    private val dispatcher = schema.configuration.coroutineDispatcher
    private val coroutineContext = Job() + this.dispatcher

    inner class ExecutionContext(
        val variables: Variables,
        val requestContext: Context
    ) : Mutex by Mutex() {

        private val dataCounters = ConcurrentHashMap<DataLoader<*, *>, Pair<SelectionNode, AtomicLong>>()

        suspend fun get(loader: DataLoader<*, *>): Long = withLock {
            dataCounters[loader]?.second?.get() ?: throw IllegalArgumentException("Something went wrong with execution")
        }
        suspend fun add(loader: DataLoader<*, *>, node: SelectionNode, count: Long) = withLock {
            if (dataCounters[loader] == null) {
                println("Creating new counter: $count")
                dataCounters[loader] = node to AtomicLong(count)
            } else {
                val (otherParentValue, counter) = dataCounters[loader]!!
                if (otherParentValue != node) {
                    counter.getAndUpdate {
                        println("${(node as SelectionNode.FieldNode).aliasOrName.value} Updating counter from $it to ${it + count}")
                        it + count
                    }
                }
            }
        }
    }


    private suspend fun <T> DeferredJsonMap.writeOperation(
        ctx: ExecutionContext,
        node: Execution.Node,
        operation: FunctionWrapper<T>
    )  {
        node.field.checkAccess(null, ctx.requestContext)
        val result: T? = operation.invoke(
            funName = node.field.name,
            receiver = null,
            inputValues = node.field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )

        node.aliasOrKey to createNodeAsync(ctx, result, node, node.field.returnType, 0).await()
    }



    private fun SelectionNode.level(): Int {
        if (parent == null) return 0
        return 1 + parent.level()
    }

    private fun <T> DeferredJsonMap.createNodeAsync(
        ctx: ExecutionContext,
        value: T?,
        node: Execution.Node,
        returnType: Type,
        parentCount: Long
    ): Deferred<JsonElement> = async {
        println("abc")
        when {
            value == null -> createNullNode(node, returnType)
            value is Collection<*> || value is Array<*> -> {
                if (returnType.isList()) {
                    val values = when (value) {
                        is Array<*> -> value.toList()
                        else -> value as Collection<*>
                    }

                    arr(values) {
                        createNodeAsync(ctx, it, node, returnType.unwrapList(), values.size.toLong()).await()
                    }
                } else {
                    throw ExecutionException("Invalid collection value for non collection property", node)
                }
            }
            value is String -> JsonPrimitive(value)
            value is Int -> JsonPrimitive(value)
            value is Float -> JsonPrimitive(value)
            value is Double -> JsonPrimitive(value)
            value is Boolean -> JsonPrimitive(value)
            value is Long -> JsonPrimitive(value)
            node.children.isNotEmpty() -> {
                println("-- Creating objectNode!")
                obj {
                    createObjectNode(ctx, value, node, returnType, parentCount)
                }
            }
            node is Execution.Union -> throw IllegalArgumentException("Unions are not supported at the moment!")
            else -> throw IllegalArgumentException("Simple valueNode not supported")
        }
    }

    private suspend fun <T> DeferredJsonMap.createObjectNode(ctx: ExecutionContext, value: T, node: Execution.Node, type: Type, parentCount: Long) {
        node.children.map { child ->
            when (child) {
                is Execution.Fragment -> handleFragment(ctx, value, child)
                else -> applyProperty(ctx, value, child, type, parentCount)
            }
        }
    }

    private suspend fun <T> DeferredJsonMap.handleFragment(ctx: ExecutionContext, value: T, container: Execution.Fragment) {
        val expectedType = container.condition.type

        if (expectedType.kind == TypeKind.OBJECT || expectedType.kind == TypeKind.INTERFACE) {
            if (expectedType.isInstance(value)) {
                container.elements.map { child ->
                    when (child) {
                        is Execution.Fragment -> handleFragment(ctx, value, child)
                        else -> applyProperty(ctx, value, child, expectedType, container.elements.size.toLong())
                    }
                }
            }
        } else {
            throw IllegalStateException("fragments can be specified on object types, interfaces, and unions")
        }
    }

    private suspend fun <T> DeferredJsonMap.applyProperty(ctx: ExecutionContext, value: T, child: Execution, type: Type, parentCount: Long) {
        when (child) {
            is Execution.Union -> throw UnsupportedOperationException("Unions are currently not supported!")
            is Execution.Node -> {
                val field = type.unwrapped()[child.key]
                    ?: throw IllegalStateException("Execution unit ${child.key} is not contained by operation return type")
                child.aliasOrKey toObj {
                    createPropertyNodeAsync(ctx, value, child, field, parentCount)
                }
            }
            else -> throw UnsupportedOperationException("Whatever this is isn't supported!")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> DeferredJsonMap.createPropertyNodeAsync(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field,
        parentCount: Long
    ) {
        // TODO: Check include directive
        node.field.checkAccess(parentValue, ctx.requestContext)

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

                node.aliasOrKey to createNodeAsync(ctx, value, node, field.returnType, parentCount).await()
            }
            is Field.Function<*, *> -> {
                handleFunctionProperty(ctx, parentValue, node, field, parentCount)
            }
            is Field.DataLoader<*, *, *> -> {
                field as Field.DataLoader<T, *, *>
                ctx.add(field.loader, node.selectionNode, parentCount)
                handleDataPropertyAsync(ctx, parentValue, node, field, parentCount)
            }
            else -> throw TODO("Only Kotlin Fields are supported!")
        }
    }

    private fun <T> DeferredJsonMap.handleDataPropertyAsync(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field.DataLoader<T, *, *>,
        parentCount: Long
    ): Job = launch {
        val preparedValue = field.kql.prepare.invoke(
            funName = field.name,
            receiver = parentValue,
            inputValues = field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        ) ?: TODO("Nullable prepare functions isn't supported")

        val dLoader = (field.loader as DataLoader<Any, *>)

        val value = dLoader.loadAsync(preparedValue)

        val count = ctx.get(dLoader)
        val stats = dLoader.createStatisticsSnapshot()


        if (stats.objectsRequested >= count) {
            println("Sending dispatch!")
            dLoader.dispatch()
        }
        else println("No dispatch ${stats.objectsRequested} == $count")

        val loaded = value.await()

        node.aliasOrKey to createNodeAsync(ctx, loaded, node, field.returnType, parentCount).await()
    }

    private suspend fun <T> DeferredJsonMap.handleFunctionProperty(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field.Function<*, *>,
        parentCount: Long
    ) {
        val result = field(
            funName = field.name,
            receiver = parentValue,
            inputValues = field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )

        node.aliasOrKey to createNodeAsync(ctx, result, node, field.returnType, parentCount).await()
    }

    override suspend fun suspendExecute(plan: ExecutionPlan, variables: VariablesJson, context: Context) = kson2(dispatcher) {
        val ctx = ExecutionContext(Variables(schema, variables, plan.firstOrNull { it.variables != null }?.variables), context)


        "data" toObj {
            plan.forEach { node ->
                writeOperation(ctx, node, node.field as Field.Function<*, *>)
            }
        }
    }.toString()

    private fun createNullNode(node: Execution.Node, returnType: Type): JsonNull = if (returnType !is Type.NonNull) {
        JsonNull
    } else {
        throw ExecutionException("null result for non-nullable operation ${node.field}", node)
    }

    override fun execute(plan: ExecutionPlan, variables: VariablesJson, context: Context) = runBlocking {
        suspendExecute(plan, variables, context)
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
            inputValues,
            args,
            ctx.variables,
            executionNode,
            ctx.requestContext
        )
        //exceptions are not caught on purpose to pass up business logic errors
        return when {
            hasReceiver -> invoke(receiver, *transformedArgs.toTypedArray())
            else -> invoke(*transformedArgs.toTypedArray())
        }
    }

    private fun log(str: String) = println("LevelRequestExecutor: $str")
}
