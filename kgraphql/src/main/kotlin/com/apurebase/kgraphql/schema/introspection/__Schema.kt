package com.apurebase.kgraphql.schema.introspection

interface __Schema : Describable {
    val types: List<__Type>

    val queryType: __Type

    val mutationType: __Type?

    val subscriptionType: __Type?

    val directives: List<__Directive>
}
