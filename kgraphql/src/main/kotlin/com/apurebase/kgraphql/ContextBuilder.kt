package com.apurebase.kgraphql

import kotlin.reflect.KClass

class ContextBuilder(block: ContextBuilder.() -> Unit) {

    val components: MutableMap<KClass<*>, Any> = mutableMapOf()

    init {
        block()
    }

    fun <T : Any> inject(kClass: KClass<T>, component: T) {
        require(components[kClass] == null) {
            "There's already object of type $kClass in this context -> ${components[kClass]}"
        }
        components[kClass] = component
    }

    inline infix fun <reified T : Any> inject(component: T) {
        inject(T::class, component)
    }

    inline operator fun <reified T : Any> T.unaryPlus() {
        inject(T::class, this)
    }
}

fun context(block: ContextBuilder.() -> Unit): Context {
    return Context(ContextBuilder(block).components)
}
