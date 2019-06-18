@file:Suppress("UNCHECKED_CAST")

package com.apurebase.kgraphql.schema.model

import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.structure2.validateName
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.reflect


/**
 * FunctionWrapper is common interface for classes storing functions registered in schema by server code.
 * Only up to 9 arguments are supported, because kotlin-reflect doesn't support
 * invoking lambdas, local and anonymous functions yet, making implementation severely limited.
 */
interface FunctionWrapper <T>{
    //lots of boilerplate here, because kotlin-reflect doesn't support invoking lambdas, local and anonymous functions yet
    companion object {
        fun <T> on (function : KFunction<T>) : FunctionWrapper<T>
                = ArityN(function)

        fun <T> on (function : suspend () -> T) : FunctionWrapper<T>
            = ArityZero(function)

        fun <T, R> on (function : suspend (R) -> T)
            = ArityOne(function, false)

        fun <T, R> on (function : suspend (R) -> T, hasReceiver: Boolean = false)
            = ArityOne(function, hasReceiver)

        fun <T, R, E> on (function : suspend (R, E) -> T, hasReceiver: Boolean = false)
            = ArityTwo(function, hasReceiver)

        fun <T, R, E, W> on (function : suspend (R, E, W) -> T, hasReceiver: Boolean = false)
            = ArityThree(function, hasReceiver)

        fun <T, R, E, W, Q> on (function : suspend (R, E, W, Q) -> T, hasReceiver: Boolean = false)
            = ArityFour(function, hasReceiver)

        fun <T, R, E, W, Q, A> on (function : suspend (R, E, W, Q, A) -> T, hasReceiver: Boolean = false)
            = ArityFive(function, hasReceiver)

        fun <T, R, E, W, Q, A, S> on (function : suspend (R, E, W, Q, A, S) -> T, hasReceiver: Boolean = false)
            = AritySix(function, hasReceiver)

        fun <T, R, E, W, Q, A, S, G> on (function : suspend (R, E, W, Q, A, S, G) -> T, hasReceiver: Boolean = false)
            = AritySeven(function, hasReceiver)

        fun <T, R, E, W, Q, A, S, G, H> on (function : suspend (R, E, W, Q, A, S, G, H) -> T, hasReceiver: Boolean = false)
            = ArityEight(function, hasReceiver)

        fun <T, R, E, W, Q, A, S, G, H, J> on (function : suspend (R, E, W, Q, A, S, G, H, J) -> T, hasReceiver: Boolean = false)
            = ArityNine(function, hasReceiver)

    }

    val kFunction: KFunction<T>

    suspend fun invoke(vararg args: Any?) : T?

    fun arity() : Int

    /**
     * denotes whether function is called with receiver argument.
     * Receiver argument in GraphQL is somewhat similar to kotlin receivers:
     * its value is passed by framework, usually it is parent of function property with [FunctionWrapper]
     * Receiver argument is omitted in schema, and cannot be stated in query document.
     */
    val hasReceiver : Boolean

    val argumentsDescriptor : Map<String, KType>

    abstract class Base<T> : FunctionWrapper<T>{
        private fun createArgumentsDescriptor(): Map<String, KType> {
            return valueParameters().associate { parameter ->
                val parameterName = parameter.name
                        ?: throw SchemaException("Cannot handle nameless argument on function: $kFunction")

                validateName(parameterName)
                parameterName to parameter.type
            }
        }

        override val argumentsDescriptor: Map<String, KType> by lazy { createArgumentsDescriptor() }

        override suspend fun invoke(vararg args: Any?) : T? {
            return invoke(*args)
        }
    }

    /**
     * returns list of function parameters without receiver
     */
    fun valueParameters(): List<kotlin.reflect.KParameter> {
        return kFunction.valueParameters.let {
            if(hasReceiver) it.drop(1) else it
        }
    }

    class ArityN<T>(override val kFunction: KFunction<T>): Base<T>() {
        override fun arity() = kFunction.parameters.size

        override val hasReceiver: Boolean
            get() = kFunction.extensionReceiverParameter != null

        override suspend fun invoke(vararg args: Any?): T? {
            return kFunction.callSuspend(*args)
        }
    }

    class ArityZero<T>(val implementation : suspend ()-> T, override val hasReceiver: Boolean = false ) : Base<T>() {
        override val kFunction: KFunction<T> by lazy { implementation.reflect()!! }
        override fun arity(): Int = 0

        override suspend fun invoke(vararg args: Any?): T? {
            if(args.isNotEmpty()){
                throw IllegalArgumentException("This function does not accept arguments")
            } else {
                return implementation()
            }
        }
    }

    class ArityOne<T, R>(
        val implementation : suspend (R)-> T, override val hasReceiver: Boolean
    ) : Base<T>() {

        override val kFunction: KFunction<T> by lazy { implementation.reflect()!! }

        override fun arity(): Int = 1

        override suspend fun invoke(vararg args: Any?): T? {
            if(args.size == arity()){
                return implementation(args[0] as R)
            } else {
                throw IllegalArgumentException("This function needs exactly ${arity()} arguments")
            }
        }
    }

    class ArityTwo<T, R, E>(
        val implementation : suspend (R, E)-> T,
        override val hasReceiver: Boolean
    ) : Base<T>() {

        override val kFunction: KFunction<T> by lazy { implementation.reflect()!! }

        override fun arity(): Int = 2

        override suspend fun invoke(vararg args: Any?): T? {
            if(args.size == arity()){
                return implementation(args[0] as R, args[1] as E)
            } else {
                throw IllegalArgumentException("This function needs exactly ${arity()} arguments")
            }
        }
    }

    class ArityThree<T, R, E, W>(
        val implementation : suspend (R, E, W)-> T, override val hasReceiver: Boolean
    ) : Base<T>() {

        override val kFunction: KFunction<T> by lazy { implementation.reflect()!! }

        override fun arity(): Int = 3

        override suspend fun invoke(vararg args: Any?): T? {
            if(args.size == arity()){
                return implementation(args[0] as R, args[1] as E, args[2] as W)
            } else {
                throw IllegalArgumentException("This function needs exactly ${arity()} arguments")
            }
        }
    }

    class ArityFour<T, R, E, W, Q>(
        val implementation : suspend (R, E, W, Q)-> T, override val hasReceiver: Boolean
    ) : Base<T>() {

        override val kFunction: KFunction<T> by lazy { implementation.reflect()!! }

        override fun arity(): Int = 4

        override suspend fun invoke(vararg args: Any?): T? {
            if(args.size == arity()){
                return implementation(args[0] as R, args[1] as E, args[2] as W, args[3] as Q)
            } else {
                throw IllegalArgumentException("This function needs exactly ${arity()} arguments")
            }
        }
    }

    class ArityFive<T, R, E, W, Q, A>(
        val implementation : suspend (R, E, W, Q, A)-> T, override val hasReceiver: Boolean
    ) : Base<T>() {

        override val kFunction: KFunction<T> by lazy { implementation.reflect()!! }

        override fun arity(): Int = 5

        override suspend fun invoke(vararg args: Any?): T? {
            if(args.size == arity()){
                return implementation(args[0] as R, args[1] as E, args[2] as W, args[3] as Q, args[4] as A)
            } else {
                throw IllegalArgumentException("This function needs exactly ${arity()} arguments")
            }
        }
    }

    class AritySix<T, R, E, W, Q, A, S>(
        val implementation : suspend (R, E, W, Q, A, S)-> T, override val hasReceiver: Boolean
    ) : Base<T>() {

        override val kFunction: KFunction<T> by lazy { implementation.reflect()!! }
        override fun arity(): Int = 6

        override suspend fun invoke(vararg args: Any?): T? {
            if(args.size == arity()){
                return implementation(args[0] as R, args[1] as E, args[2] as W, args[3] as Q, args[4] as A, args[5] as S)
            } else {
                throw IllegalArgumentException("This function needs exactly ${arity()} arguments")
            }
        }
    }

    class AritySeven<T, R, E, W, Q, A, S, D>(
        val implementation : suspend (R, E, W, Q, A, S, D)-> T, override val hasReceiver: Boolean
    ) : Base<T>() {

        override val kFunction: KFunction<T> by lazy { implementation.reflect()!! }
        override fun arity(): Int = 7

        override suspend fun invoke(vararg args: Any?): T? {
            if(args.size == arity()){
                return implementation(args[0] as R, args[1] as E, args[2] as W, args[3] as Q, args[4] as A, args[5] as S, args[6] as D)
            } else {
                throw IllegalArgumentException("This function needs exactly ${arity()} arguments")
            }
        }
    }

    class ArityEight<T, R, E, W, Q, A, S, D, F>(
        val implementation : suspend (R, E, W, Q, A, S, D, F)-> T, override val hasReceiver: Boolean
    ) : Base<T>() {

        override val kFunction: KFunction<T> by lazy { implementation.reflect()!! }
        override fun arity(): Int = 8

        override suspend fun invoke(vararg args: Any?): T? {
            if(args.size == arity()){
                return implementation(args[0] as R, args[1] as E, args[2] as W, args[3] as Q, args[4] as A, args[5] as S, args[6] as D, args[7] as F)
            } else {
                throw IllegalArgumentException("This function needs exactly ${arity()} arguments")
            }
        }
    }

    class ArityNine<T, R, E, W, Q, A, S, D, F, G>(
        val implementation : suspend (R, E, W, Q, A, S, D, F, G)-> T, override val hasReceiver: Boolean
    ) : Base<T>() {

        override val kFunction: KFunction<T> by lazy { implementation.reflect()!! }
        override fun arity(): Int = 9

        override suspend fun invoke(vararg args: Any?): T? {
            if(args.size == arity()){
                return implementation(args[0] as R, args[1] as E, args[2] as W, args[3] as Q, args[4] as A, args[5] as S, args[6] as D, args[7] as F, args[8] as G)
            } else {
                throw IllegalArgumentException("This function needs exactly ${arity()} arguments")
            }
        }
    }
}