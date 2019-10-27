package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.*
import com.apurebase.kgraphql.schema.model.*
import com.fasterxml.jackson.databind.ObjectWriter
<<<<<<< HEAD
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
=======
import java.util.concurrent.Flow
import kotlin.reflect.full.memberProperties
>>>>>>> #10 Add Subscription support


class SubscriptionDSL(
        val name : String,
        block : SubscriptionDSL.() -> Unit
) : LimitedAccessItemDSL<Nothing>(), ResolverDSL.Target {

    private val inputValues = mutableListOf<InputValueDef<*>>()

    init {
        block()
    }

    private var functionWrapper : FunctionWrapper<*>? = null

    private fun resolver(function: FunctionWrapper<*>): ResolverDSL {
        functionWrapper = function
        return ResolverDSL(this)
    }

    fun <T>resolver(function: suspend (String) -> T) = resolver(FunctionWrapper.on(function))

    fun accessRule(rule: (Context) -> Exception?){
        val accessRuleAdapter: (Nothing?, Context) -> Exception? = { _, ctx -> rule(ctx) }
        this.accessRuleBlock = accessRuleAdapter
    }

    override fun addInputValues(inputValues: Collection<InputValueDef<*>>) {
        this.inputValues.addAll(inputValues)
    }

    internal fun toKQLSubscription(): SubscriptionDef<out Any?> {
        val function = functionWrapper ?: throw IllegalArgumentException("resolver has to be specified for subscription [$name]")

        return SubscriptionDef (
                name = name,
                resolver = function,
                description = description,
                isDeprecated = isDeprecated,
                deprecationReason = deprecationReason,
                inputValues = inputValues,
                accessRule = accessRuleBlock
        )
    }
}



private fun <T : Any> getFieldValue(clazz: T, field: String): Any? {
    val properties = clazz.javaClass.kotlin.memberProperties
    for (p in properties) {
        if (p.name == field) {
            return p.getter.call(clazz)
        }
    }
    return null
}

<<<<<<< HEAD
fun <T : Any> subscribe(subscription: String, publisher: Publisher, output: T, function: (response: String) -> Unit): T {
    if (!(publisher as FunctionWrapper<*>).kFunction.returnType.isSubtypeOf(output::class.starProjectedType))  
        throw SchemaException("Subscription return type must be the same as the publisher's")
=======
fun <T : Any?> subscribe(subscription: String, publisher: Publisher, output: T?, function: (response: String) -> Unit): T? {
>>>>>>> #10 Add Subscription support
    val subscriber = object : Subscriber {
        override fun setObjectWriter(objectWriter: ObjectWriter) {
            this.objectWriter = objectWriter
        }

        private var args = emptyArray<String>()
        private lateinit var objectWriter: ObjectWriter
        override fun setArgs(args: Array<String>) {
            this.args = args
        }

        override fun onNext(item: Any?) {
            val response = mutableMapOf<String, Any?>()
            val result = mutableMapOf<String, Any?>()
            response["data"] = result
            args.forEach {
                result[it] = getFieldValue(item!!, it)
            }
            function(objectWriter.writeValueAsString(response))
        }

        override fun onComplete() {
            TODO("not needed for now")
        }

<<<<<<< HEAD
        override fun onSubscribe(subscription: Subscription) {
=======
        override fun onSubscribe(subscription: Flow.Subscription) {
>>>>>>> #10 Add Subscription support
            TODO("not needed for now")
        }

        override fun onError(throwable: Throwable) {
            publisher.unsubscribe(subscription)
        }

    }
    publisher.subscribe(subscription, subscriber)
    return output
}

fun <T : Any> unsubscribe(subscription: String, publisher: Publisher, output: T): T {
    publisher.unsubscribe(subscription)
    return output
}