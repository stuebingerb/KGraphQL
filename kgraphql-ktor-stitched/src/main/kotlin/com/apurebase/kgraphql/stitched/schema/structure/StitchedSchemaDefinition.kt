package com.apurebase.kgraphql.stitched.schema.structure

import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.model.MutationDef
import com.apurebase.kgraphql.schema.model.QueryDef
import com.apurebase.kgraphql.schema.model.SchemaDefinition
import com.apurebase.kgraphql.schema.model.SubscriptionDef
import com.apurebase.kgraphql.schema.model.TypeDef

class StitchedSchemaDefinition(
    objects: List<TypeDef.Object<*>>,
    queries: List<QueryDef<*>>,
    scalars: List<TypeDef.Scalar<*>>,
    mutations: List<MutationDef<*>>,
    subscriptions: List<SubscriptionDef<*>>,
    enums: List<TypeDef.Enumeration<*>>,
    unions: List<TypeDef.Union>,
    directives: List<Directive.Partial>,
    inputObjects: List<TypeDef.Input<*>>,
    val remoteSchemas: Map<String, __Schema>,
    val stitchedProperties: List<StitchedProperty>
) : SchemaDefinition(
    objects,
    queries,
    scalars,
    mutations,
    subscriptions,
    enums,
    unions,
    directives,
    inputObjects
)
