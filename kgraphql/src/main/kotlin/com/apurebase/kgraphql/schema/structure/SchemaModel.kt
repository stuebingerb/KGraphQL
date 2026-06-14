package com.apurebase.kgraphql.schema.structure

import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.introspection.NotIntrospected
import com.apurebase.kgraphql.schema.introspection.__Schema
import kotlin.reflect.full.findAnnotation

data class SchemaModel(
    private val query: Type,
    private val mutation: Type?,
    private val subscription: Type?,
    val allTypes: List<Type>,
    override val directives: List<Directive>,
    val remoteTypesBySchema: Map<String, List<Type>>,
    override val description: String?
) : __Schema {

    val allTypesByName = allTypes.associateBy { it.name }

    // (only) used for resolving fragment conditions, which cannot be specified on any input type
    val queryTypesByName = allTypes.filterNot { it.isInputType() }.associateBy { it.name }

    override val types: List<Type> = toTypeList()

    private fun toTypeList(): List<Type> = allTypes
        // workaround on the fact that Double and Float are treated as GraphQL Float
        .filterNot { it is Type.Scalar<*> && it.kClass == Double::class }
        .filterNot { it.kClass?.findAnnotation<NotIntrospected>() != null }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.toString() })

    override val queryType: Type = query

    override val mutationType: Type? = mutation

    override val subscriptionType: Type? = subscription
}
