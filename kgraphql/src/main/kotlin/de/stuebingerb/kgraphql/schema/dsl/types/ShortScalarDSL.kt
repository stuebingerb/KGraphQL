package de.stuebingerb.kgraphql.schema.dsl.types

import de.stuebingerb.kgraphql.schema.SchemaException
import de.stuebingerb.kgraphql.schema.model.ast.ValueNode
import de.stuebingerb.kgraphql.schema.scalar.ScalarCoercion
import de.stuebingerb.kgraphql.schema.scalar.ShortScalarCoercion
import kotlin.reflect.KClass

class ShortScalarDSL<T : Any>(kClass: KClass<T>) : ScalarDSL<T, Short>(kClass) {

    override fun createCoercionFromFunctions(): ScalarCoercion<T, Short> {
        return object : ShortScalarCoercion<T> {

            val serializeImpl = serialize ?: throw SchemaException(PLEASE_SPECIFY_COERCION)

            val deserializeImpl = deserialize ?: throw SchemaException(PLEASE_SPECIFY_COERCION)

            override fun serialize(instance: T): Short = serializeImpl(instance)

            override fun deserialize(raw: Short, valueNode: ValueNode): T = deserializeImpl(raw)
        }
    }
}
