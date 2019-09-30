package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.request.Arguments
import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.structure2.Field
import com.apurebase.kgraphql.schema.structure2.Type
import com.apurebase.kgraphql.schema.jol.ast.ArgumentNodes
import com.apurebase.kgraphql.schema.jol.ast.VariableDefinitionNode
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.*
import com.apurebase.kgraphql.schema.structure2.InputValue
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger


sealed class Execution {

    open class Node (
        val field: Field,
        val children: Collection<Execution>,
        val key : String,
        val alias: String? = null,
        val arguments : ArgumentNodes? = null,
        val typeCondition: TypeCondition? = null,
        val directives: Map<Directive, ArgumentNodes?>? = null,
        val variables: List<VariableDefinitionNode>? = null
    ) : Execution() {
        val aliasOrKey = alias ?: key
    }

    class Fragment(
        val condition: TypeCondition,
        val elements : List<Execution>,
        val directives: Map<Directive, ArgumentNodes?>?
    ) : Execution()

    // TODO: Rename
//    class DLT<K, R>(
//        val name: String,
//        val prepare: FunctionWrapper<K>,
//        val loader: FunctionWrapper<out Map<out K, R>>,
//        val arguments: Arguments?,
//        val cache: ConcurrentMap<K, R>
//    ) {
//        private val executionMap: ConcurrentMap<Any, Pair<AtomicInteger, Int>> = ConcurrentHashMap()
//        private val channels: MutableMap<K, Queue<Channel<R>>> = ConcurrentHashMap()
//
//        internal fun setTotalCount(parent: DataValue<*, *>, count: Int) {
////            val countToUse = if (parent.parentValue is List<*>) count * parent.parentValue.size
////            else count
//            val countToUse = count
//            executionMap.putIfAbsent(
//                parent.parentValue ?: throw TODO("Please don't let this ever happen!"),
//                AtomicInteger() to countToUse
//            )
//        }
//
//        private fun safePrint(str: String) {
//            synchronized(this) {
//                println(str)
//            }
//        }
//
//        suspend fun load(
//            test: ParallelRequestExecutor,
//            ctx: ParallelRequestExecutor.ExecutionContext,
//            inputArguments: List<InputValue<*>>,
//            abc: DataValue<*, *>
//        ): Any? = coroutineScope {
//            with(test) {
//                val key = prepare.invoke(
//                    funName = name,
//                    receiver = abc.value,
//                    inputValues = inputArguments,
//                    args = arguments,
//                    ctx = ctx
//                ) ?: throw TODO("Don't think I wan't this to be possible to be null?")
//
//                val channel: Channel<R> = Channel(Channel.CONFLATED)
//
//                // Add channel to list
//                channels.putIfAbsent(key, ConcurrentLinkedQueue())
//                channels[key]?.add(channel) ?: throw TODO("Please do exist!")
//
//
//                val (count, total) = executionMap[abc.parentValue] ?: TODO("Should never happen!")
//
//                val currentCount = count.incrementAndGet()
//
//                safePrint("CHECKING: $currentCount(${count.hashCode()}) == $total (${currentCount == total}) - $key")
//
//                if (currentCount == total) {
//                    launch {
//                        val missingKeys = channels.keys.filter { k -> k !in cache.keys }
//
//                        safePrint("LOADING: missing keys : ${missingKeys.joinToString()}")
//                        // TODO: validate the the loader is returning the right value map
//                        (if (missingKeys.isEmpty()) mapOf() else loader.invoke(missingKeys)!!).forEach { (key, value) ->
//                            cache.putIfAbsent(key, value)
//                        }
//
//                        channels.keys.forEach { k ->
//                            var value = cache[k] ?: cache[k] ?: cache[k]
//
//                            if (value == null) {
//                                delay(1)
//                                value = cache[k]
//                                println("Please fix me! - no need ;) $value")
//                            }
//
//                            val a = channels[k]
//                            a?.forEach {
//                                it.send(value ?: TODO("Damn"))
//                            } ?: throw Exception("key: $key was not found :'(")
//                        }
//                    }
//                }
//
//
//                safePrint("$key waiting")
//                val result = channel.receive()
//                channel.close()
//
//                safePrint("Returning results for key: $key")
//                result
//            }
//        }
//    }

//    class DataLoad(
//        val childFields: Map<Field.DataLoader<*, *, *>, DLT<*, *>>,
//        field: Field,
//        children: Collection<Execution>,
//        key : String,
//        alias: String? = null,
//        arguments : ArgumentNodes? = null,
//        typeCondition: TypeCondition? = null,
//        directives: Map<Directive, ArgumentNodes?>?,
//        variables: List<VariableDefinitionNode>?
//    ): Execution.Node(
//        field = field,
//        children = children,
//        key = key,
//        alias = alias,
//        arguments = arguments,
//        typeCondition = typeCondition,
//        directives = directives,
//        variables = variables
//    )


    class Union (
        val unionField: Field.Union<*>,
        val memberChildren: Map<Type, Collection<Execution>>,
        key: String,
        alias: String? = null,
        condition : TypeCondition? = null,
        directives: Map<Directive, ArgumentNodes?>? = null
    ) : Execution.Node (
        field = unionField,
        children = emptyList(),
        key = key,
        alias = alias,
        typeCondition = condition,
        directives = directives
    ) {
        fun memberExecution(type: Type): Execution.Node {
            return Execution.Node (
                field = field,
                children = memberChildren[type] ?: throw IllegalArgumentException("Union ${unionField.name} has no member $type"),
                key = key,
                alias = alias,
                arguments = arguments,
                typeCondition = typeCondition,
                directives = directives,
                variables = null
            )
        }
    }
}
