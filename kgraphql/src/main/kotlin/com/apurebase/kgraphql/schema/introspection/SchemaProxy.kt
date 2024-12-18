package com.apurebase.kgraphql.schema.introspection

/**
 * Wrapper for [proxiedSchema] to resolve introspection types
 */
class SchemaProxy(
    var proxiedSchema: __Schema? = null
) : __Schema {
    companion object {
        const val ILLEGAL_STATE_MESSAGE = "Missing proxied __Schema instance"
    }

    private fun getProxied() = checkNotNull(proxiedSchema) { ILLEGAL_STATE_MESSAGE }

    override val types: List<__Type>
        get() = getProxied().types

    override val queryType: __Type
        get() = getProxied().queryType

    override val mutationType: __Type?
        get() = getProxied().mutationType

    override val subscriptionType: __Type?
        get() = getProxied().subscriptionType

    override val directives: List<__Directive>
        get() = getProxied().directives
}
