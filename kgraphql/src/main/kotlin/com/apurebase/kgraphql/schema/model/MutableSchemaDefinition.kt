package com.apurebase.kgraphql.schema.model

import com.apurebase.kgraphql.defaultKQLTypeName
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.builtin.BuiltInScalars
import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.directive.DirectiveLocation
import com.apurebase.kgraphql.schema.dsl.types.TypeDSL
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.introspection.__Directive
import com.apurebase.kgraphql.schema.introspection.__EnumValue
import com.apurebase.kgraphql.schema.introspection.__Field
import com.apurebase.kgraphql.schema.introspection.__InputValue
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.introspection.__Type
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Intermediate, mutable data structure used to prepare [SchemaDefinition]
 * Performs basic validation (names duplication etc.) when methods for adding schema components are invoked
 */
data class MutableSchemaDefinition(
    private val objects: ArrayList<TypeDef.Object<*>> = arrayListOf(
        TypeDef.Object(__Schema::class.defaultKQLTypeName(), __Schema::class),
        create__TypeDefinition(),
        create__DirectiveDefinition(),
        create__FieldDefinition()
    ),
    private val queries: ArrayList<QueryDef<*>> = arrayListOf(),
    private val scalars: ArrayList<TypeDef.Scalar<*>> = BuiltInScalars.entries.mapTo(ArrayList()) { it.typeDef },
    private val mutations: ArrayList<MutationDef<*>> = arrayListOf(),
    private val subscriptions: ArrayList<SubscriptionDef<*>> = arrayListOf(),
    private val enums: ArrayList<TypeDef.Enumeration<*>> = arrayListOf(
        TypeDef.Enumeration(
            "__" + TypeKind::class.defaultKQLTypeName(),
            TypeKind::class,
            enumValues<TypeKind>().map { EnumValueDef(it) }
        ),
        TypeDef.Enumeration(
            "__" + DirectiveLocation::class.defaultKQLTypeName(),
            DirectiveLocation::class,
            enumValues<DirectiveLocation>().map { EnumValueDef(it) }
        )
    ),
    private val unions: ArrayList<TypeDef.Union> = arrayListOf(),
    private val directives: ArrayList<Directive.Partial> = arrayListOf(
        Directive.SKIP,
        Directive.INCLUDE
    ),
    private val inputObjects: ArrayList<TypeDef.Input<*>> = arrayListOf()
) {

    val unionsMonitor: List<TypeDef.Union>
        get() = unions

    fun toSchemaDefinition(): SchemaDefinition {
        val compiledObjects = ArrayList(this.objects)

        unions.forEach { union ->
            if (union.members.isEmpty()) {
                throw SchemaException("The union type '${union.name}' has no possible types defined, requires at least one. Please refer to https://stuebingerb.github.io/KGraphQL/Reference/Type%20System/unions/")
            }
            union.members.forEach { member ->
                validateUnionMember(union, member, compiledObjects)
            }
        }

        return SchemaDefinition(
            compiledObjects,
            queries,
            scalars,
            mutations,
            subscriptions,
            enums,
            unions,
            directives,
            inputObjects
        )
    }

    private fun validateUnionMember(
        union: TypeDef.Union,
        member: KClass<*>,
        compiledObjects: ArrayList<TypeDef.Object<*>>
    ) {
        if (scalars.any { it.kClass == member } || enums.any { it.kClass == member }) {
            throw SchemaException(
                "The member types of a Union type must all be Object base types; " +
                        "Scalar, Interface and Union types may not be member types of a Union"
            )
        }

        if (member.isSubclassOf(Collection::class)) {
            throw SchemaException("Collection may not be member type of a Union '${union.name}'")
        }

        if (member.isSubclassOf(Map::class)) {
            throw SchemaException("Map may not be member type of a Union '${union.name}'")
        }

        if (compiledObjects.none { it.kClass == member }) {
            compiledObjects.add(TypeDef.Object(member.defaultKQLTypeName(), member))
        }
    }

    fun addQuery(query: QueryDef<*>) {
        if (query.checkEqualName(queries)) {
            throw SchemaException("Cannot add query with duplicated name ${query.name}")
        }
        queries.add(query)
    }

    fun addMutation(mutation: MutationDef<*>) {
        if (mutation.checkEqualName(mutations)) {
            throw SchemaException("Cannot add mutation with duplicated name ${mutation.name}")
        }
        mutations.add(mutation)
    }

    fun addSubscription(subscription: SubscriptionDef<*>) {
        if (subscription.checkEqualName(subscriptions)) {
            throw SchemaException("Cannot add mutation with duplicated name ${subscription.name}")
        }
        subscriptions.add(subscription)
    }

    fun addScalar(scalar: TypeDef.Scalar<*>) = addType(scalar, scalars, "Scalar")

    fun addEnum(enum: TypeDef.Enumeration<*>) = addType(enum, enums, "Enumeration")

    fun addObject(objectType: TypeDef.Object<*>) = addType(objectType, objects, "Object")

    fun addUnion(union: TypeDef.Union) = addType(union, unions, "Union")

    fun addInputObject(input: TypeDef.Input<*>) = addType(input, inputObjects, "Input")

    fun <T : Definition> addType(type: T, target: ArrayList<T>, typeCategory: String) {
        if (type.name.startsWith("__")) {
            throw SchemaException("Type name starting with \"__\" are excluded for introspection system")
        }
        if (type.checkEqualName(objects, scalars, unions, enums)) {
            throw SchemaException("Cannot add $typeCategory type with duplicated name ${type.name}")
        }
        target.add(type)
    }

    private fun Definition.checkEqualName(vararg collections: List<Definition>): Boolean {
        return collections.fold(false) { acc, list -> acc || list.any { it.equalName(this) } }
    }

    private fun Definition.equalName(other: Definition): Boolean {
        return this.name.equals(other.name, true)
    }
}

private fun create__FieldDefinition() = TypeDSL(emptyList(), __Field::class).apply {
    transformation(__Field::args) { args: List<__InputValue>, includeDeprecated: Boolean? ->
        if (includeDeprecated == true) {
            args
        } else {
            args.filterNot { it.isDeprecated }
        }
    }
}.toKQLObject()

private fun create__TypeDefinition() = TypeDSL(emptyList(), __Type::class).apply {
    transformation(__Type::fields) { fields: List<__Field>?, includeDeprecated: Boolean? ->
        if (includeDeprecated == true) {
            fields
        } else {
            fields?.filterNot { it.isDeprecated }
        }
    }
    transformation(__Type::inputFields) { fields: List<__InputValue>?, includeDeprecated: Boolean? ->
        if (includeDeprecated == true) {
            fields
        } else {
            fields?.filterNot { it.isDeprecated }
        }
    }
    transformation(__Type::enumValues) { enumValues: List<__EnumValue>?, includeDeprecated: Boolean? ->
        if (includeDeprecated == true) {
            enumValues
        } else {
            enumValues?.filterNot { it.isDeprecated }
        }
    }
}.toKQLObject()

private fun create__DirectiveDefinition() = TypeDSL(
    emptyList(),
    __Directive::class
).apply {
    property("onField") {
        resolver { dir: __Directive ->
            dir.locations.contains(DirectiveLocation.FIELD)
        }
        deprecate("Use `locations`.")
    }
    property("onFragment") {
        resolver { dir: __Directive ->
            dir.locations.containsAny(
                DirectiveLocation.FRAGMENT_SPREAD,
                DirectiveLocation.FRAGMENT_DEFINITION,
                DirectiveLocation.INLINE_FRAGMENT
            )
        }
        deprecate("Use `locations`.")
    }
    property("onOperation") {
        resolver { dir: __Directive ->
            dir.locations.containsAny(
                DirectiveLocation.QUERY,
                DirectiveLocation.MUTATION,
                DirectiveLocation.SUBSCRIPTION
            )
        }
        deprecate("Use `locations`.")
    }
    transformation(__Directive::args) { args: List<__InputValue>, includeDeprecated: Boolean? ->
        if (includeDeprecated == true) {
            args
        } else {
            args.filterNot { it.isDeprecated }
        }
    }
}.toKQLObject()

private fun <T> List<T>.containsAny(vararg elements: T) = elements.any { this.contains(it) }
