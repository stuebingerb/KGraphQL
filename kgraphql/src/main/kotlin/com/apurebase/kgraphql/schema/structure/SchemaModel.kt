package com.apurebase.kgraphql.schema.structure

import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.introspection.NotIntrospected
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.introspection.__Schema
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

data class SchemaModel(
    private val query: Type,
    private val mutation: Type?,
    private val subscription: Type?,
    val allTypes: List<Type>,
    val queryTypes: Map<KClass<*>, Type>,
    val inputTypes: Map<KClass<*>, Type>,
    override val directives: List<Directive>,
    val remoteTypesBySchema: Map<String, List<Type>>
) : __Schema {

    val allTypesByName = allTypes.associateBy { it.name }

    val queryTypesByName =
        allTypes.filterNot { it.kind == TypeKind.INPUT_OBJECT || it.kind == TypeKind.SCALAR || it.kind == TypeKind.ENUM }
            .associateBy { it.name }

    override val types: List<Type> = toTypeList()

    private fun toTypeList(): List<Type> {
        val list = allTypes
            // workaround on the fact that Double and Float are treated as GraphQL Float
            .filterNot { it is Type.Scalar<*> && it.kClass == Double::class }
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
        return list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.toString() })
    }

    override val queryType: Type = query

    override val mutationType: Type? = mutation

    override val subscriptionType: Type? = subscription
}
