package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.schema.Publisher
import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.builtin.ExtendedBuiltInScalars
import com.apurebase.kgraphql.schema.dsl.operations.MutationDSL
import com.apurebase.kgraphql.schema.dsl.operations.QueryDSL
import com.apurebase.kgraphql.schema.dsl.operations.SubscriptionDSL
import com.apurebase.kgraphql.schema.dsl.types.BooleanScalarDSL
import com.apurebase.kgraphql.schema.dsl.types.DoubleScalarDSL
import com.apurebase.kgraphql.schema.dsl.types.EnumDSL
import com.apurebase.kgraphql.schema.dsl.types.InputTypeDSL
import com.apurebase.kgraphql.schema.dsl.types.IntScalarDSL
import com.apurebase.kgraphql.schema.dsl.types.LongScalarDSL
import com.apurebase.kgraphql.schema.dsl.types.ScalarDSL
import com.apurebase.kgraphql.schema.dsl.types.ShortScalarDSL
import com.apurebase.kgraphql.schema.dsl.types.StringScalarDSL
import com.apurebase.kgraphql.schema.dsl.types.TypeDSL
import com.apurebase.kgraphql.schema.dsl.types.UnionTypeDSL
import com.apurebase.kgraphql.schema.model.EnumValueDef
import com.apurebase.kgraphql.schema.model.MutableSchemaDefinition
import com.apurebase.kgraphql.schema.model.SchemaDefinition
import com.apurebase.kgraphql.schema.model.TypeDef
import com.apurebase.kgraphql.schema.structure.SchemaCompilation
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

/**
 * SchemaBuilder exposes rich DSL to setup GraphQL schema
 */
class SchemaBuilder {

    private val model = MutableSchemaDefinition()

    val schemaDefinition: SchemaDefinition
        get() = model.toSchemaDefinition()

    var configuration = SchemaConfigurationDSL()

    fun build(): Schema {
        return runBlocking {
            SchemaCompilation(configuration.build(), schemaDefinition).perform()
        }
    }

    fun configure(block: SchemaConfigurationDSL.() -> Unit) {
        configuration.update(block)
    }

    //================================================================================
    // OPERATIONS
    //================================================================================

    fun query(name: String, init: QueryDSL.() -> Unit): Publisher {
        val query = QueryDSL(name)
            .apply(init)
            .toKQLQuery()
        model.addQuery(query)
        return query
    }

    fun mutation(name: String, init: MutationDSL.() -> Unit): Publisher {
        val mutation = MutationDSL(name)
            .apply(init)
            .toKQLMutation()

        model.addMutation(mutation)
        return mutation
    }

    fun subscription(name: String, init: SubscriptionDSL.() -> Unit) {
        val subscription = SubscriptionDSL(name)
            .apply(init)
            .toKQLSubscription()

        model.addSubscription(subscription)
    }

    //================================================================================
    // SCALAR
    //================================================================================

    fun <T : Any> stringScalar(kClass: KClass<T>, block: ScalarDSL<T, String>.() -> Unit) {
        val scalar = StringScalarDSL(kClass).apply(block)
        configuration.appendMapper(scalar, kClass)
        model.addScalar(TypeDef.Scalar(scalar.name, kClass, scalar.createCoercion(), scalar.description))
    }

    inline fun <reified T : Any> stringScalar(noinline block: ScalarDSL<T, String>.() -> Unit) {
        stringScalar(T::class, block)
    }

    fun <T : Any> shortScalar(kClass: KClass<T>, block: ScalarDSL<T, Short>.() -> Unit) {
        val scalar = ShortScalarDSL(kClass).apply(block)
        configuration.appendMapper(scalar, kClass)
        model.addScalar(TypeDef.Scalar(scalar.name, kClass, scalar.createCoercion(), scalar.description))
    }

    inline fun <reified T : Any> shortScalar(noinline block: ScalarDSL<T, Short>.() -> Unit) {
        shortScalar(T::class, block)
    }

    fun <T : Any> intScalar(kClass: KClass<T>, block: ScalarDSL<T, Int>.() -> Unit) {
        val scalar = IntScalarDSL(kClass).apply(block)
        configuration.appendMapper(scalar, kClass)
        model.addScalar(TypeDef.Scalar(scalar.name, kClass, scalar.createCoercion(), scalar.description))
    }

    inline fun <reified T : Any> intScalar(noinline block: ScalarDSL<T, Int>.() -> Unit) {
        intScalar(T::class, block)
    }

    fun <T : Any> floatScalar(kClass: KClass<T>, block: ScalarDSL<T, Double>.() -> Unit) {
        val scalar = DoubleScalarDSL(kClass).apply(block)
        configuration.appendMapper(scalar, kClass)
        model.addScalar(TypeDef.Scalar(scalar.name, kClass, scalar.createCoercion(), scalar.description))
    }

    inline fun <reified T : Any> floatScalar(noinline block: ScalarDSL<T, Double>.() -> Unit) {
        floatScalar(T::class, block)
    }

    fun <T : Any> longScalar(kClass: KClass<T>, block: ScalarDSL<T, Long>.() -> Unit) {
        val scalar = LongScalarDSL(kClass).apply(block)
        configuration.appendMapper(scalar, kClass)
        model.addScalar(TypeDef.Scalar(scalar.name, kClass, scalar.createCoercion(), scalar.description))
    }

    inline fun <reified T : Any> longScalar(noinline block: ScalarDSL<T, Long>.() -> Unit) {
        longScalar(T::class, block)
    }

    fun <T : Any> booleanScalar(kClass: KClass<T>, block: ScalarDSL<T, Boolean>.() -> Unit) {
        val scalar = BooleanScalarDSL(kClass).apply(block)
        configuration.appendMapper(scalar, kClass)
        model.addScalar(TypeDef.Scalar(scalar.name, kClass, scalar.createCoercion(), scalar.description))
    }

    inline fun <reified T : Any> booleanScalar(noinline block: ScalarDSL<T, Boolean>.() -> Unit) {
        booleanScalar(T::class, block)
    }

    fun extendedScalars() {
        ExtendedBuiltInScalars.values().forEach {
            model.addScalar(it.typeDef)
        }
    }

    //================================================================================
    // TYPE
    //================================================================================

    fun <T : Any> type(kClass: KClass<T>, block: TypeDSL<T>.() -> Unit) {
        val type = TypeDSL(model.unionsMonitor, kClass).apply(block)
        model.addObject(type.toKQLObject())
    }

    inline fun <reified T : Any> type(noinline block: TypeDSL<T>.() -> Unit) {
        type(T::class, block)
    }

    inline fun <reified T : Any> type() {
        type(T::class) {}
    }

    //================================================================================
    // ENUM
    //================================================================================

    fun <T : Enum<T>> enum(kClass: KClass<T>, enumValues: Array<T>, block: (EnumDSL<T>.() -> Unit)? = null) {
        val type = EnumDSL(kClass).apply {
            if (block != null) {
                block()
            }
        }

        val kqlEnumValues = enumValues.map { value ->
            type.valueDefinitions[value]?.let { valueDSL ->
                EnumValueDef(
                    value = value,
                    description = valueDSL.description,
                    isDeprecated = valueDSL.isDeprecated,
                    deprecationReason = valueDSL.deprecationReason
                )
            } ?: EnumValueDef(value)
        }

        model.addEnum(TypeDef.Enumeration(type.name, kClass, kqlEnumValues, type.description))
    }

    inline fun <reified T : Enum<T>> enum(noinline block: (EnumDSL<T>.() -> Unit)? = null) {
        val enumValues = enumValues<T>()
        if (enumValues.isEmpty()) {
            throw SchemaException("Enum of type ${T::class} must have at least one value")
        } else {
            enum(T::class, enumValues<T>(), block)
        }
    }

    //================================================================================
    // UNION
    //================================================================================

    fun unionType(name: String, block: UnionTypeDSL.() -> Unit): TypeID {
        val union = UnionTypeDSL().apply(block)
        model.addUnion(TypeDef.Union(name, union.possibleTypes, union.description))
        return TypeID(name)
    }

    inline fun <reified T : Any> unionType(noinline block: UnionTypeDSL.() -> Unit = {}): TypeID {
        if (!T::class.isSealed) throw SchemaException("Can't generate a union type out of a non sealed class. '${T::class.simpleName}'")

        return unionType(T::class.simpleName!!) {
            block()
            T::class.sealedSubclasses.forEach {
                type(it, subTypeBlock) // <-- Adds to schema definition
                type(it) // <-- Adds to possible union type
            }
        }
    }

    //================================================================================
    // INPUT
    //================================================================================

    fun <T : Any> inputType(kClass: KClass<T>, block: InputTypeDSL<T>.() -> Unit) {
        val input = InputTypeDSL(kClass).apply(block)
        model.addInputObject(input.toKQLObject())
    }

    inline fun <reified T : Any> inputType(noinline block: InputTypeDSL<T>.() -> Unit = {}) {
        inputType(T::class, block)
    }
}

inline fun <T : Any, reified Raw : Any> SchemaConfigurationDSL.appendMapper(
    scalar: ScalarDSL<T, Raw>,
    kClass: KClass<T>
) {
    objectMapper.registerModule(SimpleModule().addDeserializer(kClass.java, object : UsesDeserializer<T>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): T? {
            return scalar.deserialize?.invoke(p.readValueAs(Raw::class.java))
        }
    }))
}

open class UsesDeserializer<T>(vc: Class<*>? = null) : StdDeserializer<T>(vc) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): T? = TODO("Implement")
}
