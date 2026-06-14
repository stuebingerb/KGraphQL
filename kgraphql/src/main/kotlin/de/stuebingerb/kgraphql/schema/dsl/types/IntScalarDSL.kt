package de.stuebingerb.kgraphql.schema.dsl.types

import de.stuebingerb.kgraphql.schema.SchemaException
import de.stuebingerb.kgraphql.schema.model.ast.ValueNode
import de.stuebingerb.kgraphql.schema.scalar.IntScalarCoercion
import de.stuebingerb.kgraphql.schema.scalar.ScalarCoercion
import kotlin.reflect.KClass

class IntScalarDSL<T : Any>(kClass: KClass<T>) : ScalarDSL<T, Int>(kClass) {

    override fun createCoercionFromFunctions(): ScalarCoercion<T, Int> {
        return object : IntScalarCoercion<T> {

            val serializeImpl = serialize ?: throw SchemaException(PLEASE_SPECIFY_COERCION)

            val deserializeImpl = deserialize ?: throw SchemaException(PLEASE_SPECIFY_COERCION)

            override fun serialize(instance: T): Int = serializeImpl(instance)

            override fun deserialize(raw: Int, valueNode: ValueNode): T = deserializeImpl(raw)
        }
    }
}
