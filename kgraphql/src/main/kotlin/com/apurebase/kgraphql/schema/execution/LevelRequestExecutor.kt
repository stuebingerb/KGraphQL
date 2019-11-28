package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.request.VariablesJson
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.ast.ArgumentNodes
import com.apurebase.kgraphql.schema.structure.Field
import com.apurebase.kgraphql.schema.structure.InputValue
import com.apurebase.kgraphql.schema.structure.Type
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.json.*
import kotlin.reflect.KProperty1

sealed class JsonMsg
object Finished: JsonMsg()


fun CoroutineScope.l() = actor<JsonMsg> {
    val list = listOf("")
    for (msg in channel) {
        when (msg) {
            is Finished -> println("All Done!")
        }
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

        inner class ExecutionJob: Job by Job() {
            init {
                invokeOnCompletion {
                    println("Shall be shown!!!!!!!!!!!!!")
                }
            }
        }

        private val levelStack = mutableMapOf<Int, ExecutionJob>()

        suspend fun launchAt(index: Int, block: suspend () -> Unit) = launch(get(index)) {
            block()
        }

        suspend fun get(index: Int) = withLock(this) {
            levelStack.getOrElse(0) {
                ExecutionJob().also {
                    levelStack[index] = it
                }
            }
        }
    }


    private fun <T> CoroutineScope.writeOperation(
        ctx: ExecutionContext,
        node: Execution.Node,
        operation: FunctionWrapper<T>,
        channel: SendChannel<JsonElement>
    ) = launch {
        node.field.checkAccess(null, ctx.requestContext)
        val result: T? = operation.invoke(
            funName = node.field.name,
            receiver = null,
            inputValues = node.field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )

        createNode(ctx, 0, result, node, node.field.returnType, channel)
    }

    private fun <T> create() = flow<JsonElement> {
        emit(JsonNull)
    }

    private suspend fun <T> createNode(
        ctx: ExecutionContext,
        lvl: Int,
        value: T?,
        node: Execution.Node,
        returnType: Type,
        channel: SendChannel<JsonElement>
    ): Job = ctx.launchAt(lvl) {
        val result = when {
            value == null -> createNullNode(node, returnType)
            value is Collection<*> || value is Array<*> -> {
                if (returnType.isList()) {
                    val values = when (value) {
                        is Array<*> -> value.toList()
                        else -> value as Collection<*>
                    }

                    val list = arrayOfNulls<JsonElement>(values.size)
                    values.mapIndexed { index, v ->
                        val ch = Channel<JsonElement>()
                        createNode(ctx, lvl, v, node, returnType.unwrapList(), ch)
                        ctx.launchAt(lvl) { list[index] = ch.receive() }
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
                val map = mutableMapOf<String, JsonElement>()
                node.children
                    .asFlow(ctx, lvl + 1, value, node, returnType)
                    .collect { map[it.first] = it.second }

                map.let(::JsonObject)
            }
            node is Execution.Union -> throw IllegalArgumentException("Unions are not supported at the moment!")
            else -> throw IllegalArgumentException("Simple valueNode not supported")
        }
        channel.send(result)
        channel.close()
    }

    private fun <T> Collection<Execution>.asFlow(
        ctx: ExecutionContext,
        lvl: Int,
        value: T,
        node: Execution.Node,
        type: Type
    ) = flow {
        map { child ->
            when (child) {
                is Execution.Fragment -> throw IllegalArgumentException("Fragments aren't supported at the moment")
                is Execution.Node -> {
                    val field = type.unwrapped()[child.key]
                        ?: throw IllegalStateException("Execution unit ${child.key} is not contained by operation return type")

                    val ch = Channel<JsonElement>()
                    createPropertyNode(ctx, lvl + 1, value, child, field, ch)

                    emit(child.aliasOrKey to ch.receive())
                    ch.close()
                }
                else -> TODO("Case not supported")
            }
        }
    }

    private suspend fun <T> createPropertyNode(
        ctx: ExecutionContext,
        lvl: Int,
        parentValue: T,
        node: Execution.Node,
        field: Field,
        channel: SendChannel<JsonElement>
    ) = ctx.launchAt(lvl) {
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

                createNode(ctx, lvl, value, node, field.returnType, channel)
            }
            is Field.Function<*, *> -> {
                handleFunctionProperty(ctx, lvl, parentValue, node, field, channel)
            }
            is Field.DataLoader<*, *, *> -> {
                handleDataProperty(ctx, lvl, parentValue, node, field, channel)
            }
            else -> throw TODO("Only Kotlin Fields are supported!")
        }
    }

    private fun ExecutionPlan.toFlowOld(variables: VariablesJson, context: Context) = flow {
        forEach { node ->
            val ctx = ExecutionContext(Variables(schema, variables, node.variables), context)
            val operation = node.field as Field.Function<*, *>

            // TODO: Restructure
            val channel = Channel<JsonElement>()
            writeOperation(ctx, node, operation, channel)

            emit(node.aliasOrKey to channel.receive())
        }
    }

    private fun List<Execution>.toFlow() = flow {

    }

    private suspend fun <T> handleDataProperty(ctx: ExecutionContext, lvl: Int, parentValue: T, node: Execution.Node, field: Field.DataLoader<*,*,*>, sendChannel: SendChannel<JsonElement>) = ctx.launchAt(lvl) {
        val preparedValue = field.kql.prepare(
            funName = field.name,
            receiver = parentValue,
            inputValues = field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )
//        field.kql.loader()
    }

    private suspend fun <T> handleFunctionProperty(
        ctx: ExecutionContext,
        lvl: Int,
        parentValue: T,
        node: Execution.Node,
        field: Field.Function<*, *>,
        channel: SendChannel<JsonElement>
    ) = ctx.launchAt(lvl) {
        val result = field(
            funName = field.name,
            receiver = parentValue,
            inputValues = field.arguments,
            args = node.arguments,
            executionNode = node,
            ctx = ctx
        )
        createNode(ctx, lvl, result, node, field.returnType, channel)
    }

    override suspend fun suspendExecute(plan: ExecutionPlan, variables: VariablesJson, context: Context): String {
        val dataMap = mutableMapOf<String, JsonElement>()

        plan
            .toFlowOld(variables, context)
            .collect { dataMap[it.first] = it.second }

        return json {
            "data" to JsonObject(dataMap)
        }.toString()
    }

    private fun createNullNode(node: Execution.Node, returnType: Type): JsonNull {
        if (returnType !is Type.NonNull) {
            return JsonNull
        } else {
            throw ExecutionException("null result for non-nullable operation ${node.field}", node)
        }
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
