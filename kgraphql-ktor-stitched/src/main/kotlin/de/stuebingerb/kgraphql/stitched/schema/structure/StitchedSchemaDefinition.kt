package de.stuebingerb.kgraphql.stitched.schema.structure

import de.stuebingerb.kgraphql.schema.directive.Directive
import de.stuebingerb.kgraphql.schema.introspection.__Schema
import de.stuebingerb.kgraphql.schema.model.MutationDef
import de.stuebingerb.kgraphql.schema.model.QueryDef
import de.stuebingerb.kgraphql.schema.model.SchemaDefinition
import de.stuebingerb.kgraphql.schema.model.SubscriptionDef
import de.stuebingerb.kgraphql.schema.model.TypeDef

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
