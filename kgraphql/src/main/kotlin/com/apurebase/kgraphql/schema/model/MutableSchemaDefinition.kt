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
import com.apurebase.kgraphql.schema.structure.validateName
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Intermediate, mutable data structure used to prepare [SchemaDefinition]
 * Performs basic validation (names duplication etc.) when methods for adding schema components are invoked
 */
open class MutableSchemaDefinition {
    protected val objects: ArrayList<TypeDef.Object<*>> = arrayListOf(
        TypeDef.Object(__Schema::class.defaultKQLTypeName(), __Schema::class),
        create__TypeDefinition(),
        create__DirectiveDefinition(),
        create__FieldDefinition()
    )
    protected val queries: ArrayList<QueryDef<*>> = arrayListOf()
    protected val scalars: ArrayList<TypeDef.Scalar<*>> = BuiltInScalars.entries.mapTo(ArrayList()) { it.typeDef }
    protected val mutations: ArrayList<MutationDef<*>> = arrayListOf()
    protected val subscriptions: ArrayList<SubscriptionDef<*>> = arrayListOf()
    protected val enums: ArrayList<TypeDef.Enumeration<*>> = arrayListOf(
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
    )
    protected val unions: ArrayList<TypeDef.Union> = arrayListOf()
    protected val directives: ArrayList<Directive.Partial> = arrayListOf(
        Directive.SKIP,
        Directive.INCLUDE,
        Directive.DEPRECATED
    )
    protected val inputObjects: ArrayList<TypeDef.Input<*>> = arrayListOf()

    val unionsMonitor: List<TypeDef.Union>
        get() = unions

    open fun toSchemaDefinition(): SchemaDefinition {
        validateUnions()

        return SchemaDefinition(
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
    }

    protected fun validateUnions() {
        unions.forEach { union ->
            if (union.members.isEmpty()) {
                throw SchemaException("The union type '${union.name}' has no possible types defined, requires at least one. Please refer to https://stuebingerb.github.io/KGraphQL/Reference/Type%20System/unions/")
            }
            union.members.forEach { member ->
                validateUnionMember(union, member, objects)
            }
        }
    }

    private fun validateUnionMember(
        union: TypeDef.Union,
        member: KClass<*>,
        compiledObjects: ArrayList<TypeDef.Object<*>>
    ) {
        if (scalars.any { it.kClass == member } || enums.any { it.kClass == member }) {
            throw SchemaException(
                "The member types of a union type must all be object base types; scalar, interface and union types may not be member types of a union"
            )
        }

        if (member.isSubclassOf(Collection::class)) {
            throw SchemaException("Collection may not be member type of a union '${union.name}'")
        }

        if (member.isSubclassOf(Map::class)) {
            throw SchemaException("Map may not be member type of a union '${union.name}'")
        }

        if (compiledObjects.none { it.kClass == member }) {
            compiledObjects.add(TypeDef.Object(member.defaultKQLTypeName(), member))
        }
    }

    fun addQuery(query: QueryDef<*>) {
        if (query.checkEqualName(queries)) {
            throw SchemaException("Cannot add query with duplicated name '${query.name}'")
        }
        queries.add(query)
    }

    fun addMutation(mutation: MutationDef<*>) {
        if (mutation.checkEqualName(mutations)) {
            throw SchemaException("Cannot add mutation with duplicated name '${mutation.name}'")
        }
        mutations.add(mutation)
    }

    fun addSubscription(subscription: SubscriptionDef<*>) {
        if (subscription.checkEqualName(subscriptions)) {
            throw SchemaException("Cannot add subscription with duplicated name '${subscription.name}'")
        }
        subscriptions.add(subscription)
    }

    fun addScalar(scalar: TypeDef.Scalar<*>) = addType(scalar, scalars, "scalar")

    fun addEnum(enum: TypeDef.Enumeration<*>) = addType(enum, enums, "enumeration")

    fun addObject(objectType: TypeDef.Object<*>) = addType(objectType, objects, "object")

    fun addUnion(union: TypeDef.Union) = addType(union, unions, "union")

    fun addInputObject(input: TypeDef.Input<*>) = addType(input, inputObjects, "input")

    private fun <T : Definition> addType(type: T, target: ArrayList<T>, typeCategory: String) {
        validateName(type.name)
        if (type.checkEqualName(objects, inputObjects, scalars, unions, enums)) {
            throw SchemaException("Cannot add $typeCategory type with duplicated name '${type.name}'")
        }
        target.add(type)
    }

    // https://spec.graphql.org/October2021/#sec-Names
    // "Names in GraphQL are case-sensitive. That is to say name, Name, and NAME all refer to different names."
    private fun Definition.checkEqualName(vararg collections: List<Definition>): Boolean {
        return collections.fold(false) { acc, list -> acc || list.any { it.name == name } }
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
