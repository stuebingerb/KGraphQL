package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.jol.ast.ValueNode
import com.apurebase.kgraphql.schema.jol.ast.ValueNode.NumberValueNode
import com.apurebase.kgraphql.schema.scalar.LongScalarCoercion
import com.apurebase.kgraphql.schema.scalar.ScalarCoercion
import kotlin.reflect.KClass


class LongScalarDSL<T : Any>(kClass: KClass<T>, block: ScalarDSL<T, Long>.() -> Unit)
    : ScalarDSL<T, Long>(kClass, block){

    override fun createCoercionFromFunctions(): ScalarCoercion<T, Long> {
        return object : LongScalarCoercion<T> {

            val serializeImpl = serialize ?: throw SchemaException(PLEASE_SPECIFY_COERCION)

            val deserializeImpl = deserialize ?: throw SchemaException(PLEASE_SPECIFY_COERCION)

            override fun serialize(instance: T): Long = serializeImpl(instance)

            override fun deserialize(raw: Long, valueNode: ValueNode?): T = deserializeImpl(raw)
        }
    }

}
