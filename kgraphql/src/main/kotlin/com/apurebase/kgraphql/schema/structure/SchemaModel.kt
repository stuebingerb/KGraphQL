package com.apurebase.kgraphql.schema.structure

import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.introspection.NotIntrospected
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.introspection.__Type
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

data class SchemaModel(
    val query: Type,
    val mutation: Type?,
    val subscription: Type?,
    val allTypes: List<Type>,
    val queryTypes: Map<KClass<*>, Type>,
    val inputTypes: Map<KClass<*>, Type>,
    override val directives: List<Directive>
) : __Schema {

    val allTypesByName = allTypes.associateBy { it.name }

    val queryTypesByName = queryTypes.values.associateBy { it.name }

    override val types: List<__Type> = toTypeList()

    private fun toTypeList(): List<__Type> {
        val list = allTypes
            // workaround on the fact that Double and Float are treated as GraphQL Float
            .filterNot { it is Type.Scalar<*> && it.kClass == Float::class }
            .filterNot { it.kClass?.findAnnotation<NotIntrospected>() != null }
            // query must be present in introspection 'types' field for introspection tools
            .plus(query)
            .toMutableList()
        if (mutation != null) {
            list += mutation
        }
        if (subscription != null) {
            list += subscription
        }
        return list.toList()
    }

    override val queryType: __Type = query

    override val mutationType: __Type? = mutation

    override val subscriptionType: __Type? = subscription

    override fun findTypeByName(name: String): __Type? = allTypesByName[name]
}

