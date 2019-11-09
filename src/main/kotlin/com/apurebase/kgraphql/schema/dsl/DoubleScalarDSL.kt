package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.scalar.DoubleScalarCoercion
import com.apurebase.kgraphql.schema.scalar.ScalarCoercion
import kotlin.reflect.KClass


class DoubleScalarDSL<T : Any>(kClass: KClass<T>, block: ScalarDSL<T, Double>.() -> Unit)
    : ScalarDSL<T, Double>(kClass, block){

    override fun createCoercionFromFunctions(): ScalarCoercion<T, Double> {
        return object : DoubleScalarCoercion<T> {

            val serializeImpl = serialize ?: throw SchemaException(PLEASE_SPECIFY_COERCION)

            val deserializeImpl = deserialize ?: throw SchemaException(PLEASE_SPECIFY_COERCION)

            override fun serialize(instance: T): Double = serializeImpl(instance)

            override fun deserialize(raw: Double, valueNode: ValueNode?): T = deserializeImpl(raw)
        }
    }

}
