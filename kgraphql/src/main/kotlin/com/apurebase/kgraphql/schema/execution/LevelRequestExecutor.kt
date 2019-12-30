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
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.json.*
import kotlin.reflect.KProperty1

@Suppress("EXPERIMENTAL_API_USAGE")
fun CoroutineScope.actorTest() = actor<Pair<Int, SendChannel<CoroutineScope>>> {
    val scopes = mutableListOf<Pair<CoroutineScope, Job>>()

    for ((lvl, sendChannel) in channel) {
        if (scopes.getOrNull(lvl) == null) {
            val parent = if (lvl > 0) scopes[lvl-1] else null
            val newJob = SupervisorJob(parent?.second)
            scopes.add(lvl, CoroutineScope(newJob) to newJob)
        }
        sendChannel.send(scopes[lvl].first)
    }
}

class LevelRequestExecutor(val schema: DefaultSchema) : RequestExecutor, CoroutineScope {

    override val coroutineContext = Job()
    private val argumentsHandler = ArgumentsHandler(schema)
    private val dispatcher = schema.configuration.coroutineDispatcher

    inner class ExecutionContext(
        val variables: Variables,
        val requestContext: Context
    ) : Mutex by Mutex() {

        private val abc = actorTest()

        suspend fun get(lvl: Int): CoroutineScope {
            val channel = Channel<CoroutineScope>()
            abc.send(lvl to channel)
            val scope = channel.receive()
            channel.cancel()
            return scope
        }
    }


    private suspend fun <T> writeOperation(
        ctx: ExecutionContext,
        node: Execution.Node,
        operation: FunctionWrapper<T>
    ): JsonElement {
        node.field.checkAccess(null, ctx.requestContext)
        val result: T? = operation.invoke(
            funName = node.field.name,
            receiver = null,
            inputValues = node.field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )

        return createNode(ctx, result, node, node.field.returnType).await()
    }



    private fun SelectionNode.level(): Int {
        if (parent == null) return 0
        return 1 + parent.level()
    }

    private suspend fun <T> createNode(
        ctx: ExecutionContext,
        value: T?,
        node: Execution.Node,
        returnType: Type
    ): Deferred<JsonElement> = ctx.get(node.selectionNode.level()).async {
        when {
            value == null -> createNullNode(node, returnType)
            value is Collection<*> || value is Array<*> -> {
                if (returnType.isList()) {
                    val values = when (value) {
                        is Array<*> -> value.toList()
                        else -> value as Collection<*>
                    }

                    val list = arrayOfNulls<JsonElement>(values.size)
                    values.mapIndexed { index, v ->
                        launch {
                            list[index] = createNode(ctx, v, node, returnType.unwrapList()).await()
                        }
                    }.joinAll()

                    JsonArray(list.asList().filterNotNull()) // TODO: not finalized!
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
                // ctx.get(node.selectionNode.level()).launch
                createObjectNode(ctx, value, node, returnType)
            }
            node is Execution.Union -> throw IllegalArgumentException("Unions are not supported at the moment!")
            else -> throw IllegalArgumentException("Simple valueNode not supported")
        }
    }

    private suspend fun <T> createObjectNode(ctx: ExecutionContext, value: T, node: Execution.Node, type: Type) = kson {
        for (child in node.children) {
            when (child) {
                is Execution.Fragment -> handleFragment(ctx, value, child)
                else -> applyProperty(ctx, value, child, type)
            }
        }
    }

    private suspend fun <T> KJsonObjectBuilder.handleFragment(ctx: ExecutionContext, value: T, container: Execution.Fragment) {
        val expectedType = container.condition.type

        if (expectedType.kind == TypeKind.OBJECT || expectedType.kind == TypeKind.INTERFACE) {
            if (expectedType.isInstance(value)) {
                container.elements.map { child ->
                    when (child) {
                        is Execution.Fragment -> handleFragment(ctx, value, child)
                        else -> listOf(applyProperty(ctx, value, child, expectedType))
                    }
                }
            }
        } else {
            throw IllegalStateException("fragments can be specified on object types, interfaces, and unions")
        }
    }

    private suspend fun <T> KJsonObjectBuilder.applyProperty(ctx: ExecutionContext, value: T, child: Execution, type: Type) {
        when (child) {
            is Execution.Union -> throw UnsupportedOperationException("Unions are currently not supported!")
            is Execution.Node -> {
                val field = type.unwrapped()[child.key]
                    ?: throw IllegalStateException("Execution unit ${child.key} is not contained by operation return type")
                child.aliasOrKey to createPropertyNodeAsync(ctx, value, child, field).await()
            }
            else -> throw UnsupportedOperationException("Whatever this is isn't supported!")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> createPropertyNodeAsync(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field
    ): Deferred<JsonElement> = ctx.get(node.selectionNode.level()).async {
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

                createNode(ctx, value, node, field.returnType).await()
            }
            is Field.Function<*, *> -> {
                handleFunctionProperty(ctx, parentValue, node, field)
            }
            is Field.DataLoader<*, *, *> -> {
                handleDataProperty(ctx, parentValue, node, field as Field.DataLoader<T, *, *>).await()
            }
            else -> throw TODO("Only Kotlin Fields are supported!")
        }
    }

    private suspend fun <T> handleDataProperty(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field.DataLoader<T, *, *>
    ): Deferred<JsonElement> = ctx.get(node.selectionNode.level()).async {
        val preparedValue = field.kql.prepare.invoke(
            funName = field.name,
            receiver = parentValue,
            inputValues = field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        ) ?: TODO("Nullable prepare functions isn't supported")

        val value = (field.loader as nidomiro.kdataloader.DataLoader<Any, *>).loadAsync(preparedValue)

        launch {
            delay(1000)
            field.loader.dispatch()
        }

        val loaded = value.await()

        createNode(ctx, loaded, node, field.returnType).await()
    }

    private suspend fun <T> handleFunctionProperty(
        ctx: ExecutionContext,
        parentValue: T,
        node: Execution.Node,
        field: Field.Function<*, *>
    ): JsonElement {
        val result = field(
            funName = field.name,
            receiver = parentValue,
            inputValues = field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )
        return createNode(ctx, result, node, field.returnType).await()
    }

    override suspend fun suspendExecute(plan: ExecutionPlan, variables: VariablesJson, context: Context): String {
        val ctx = ExecutionContext(Variables(schema, variables, plan.firstOrNull { it.variables != null }?.variables), context)

        return kson {
            "data" to kson {
                plan.forEach { node ->
                    node.aliasOrKey to writeOperation(ctx, node, node.field as Field.Function<*, *>)
                }
            }
        }.toString()
    }

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
