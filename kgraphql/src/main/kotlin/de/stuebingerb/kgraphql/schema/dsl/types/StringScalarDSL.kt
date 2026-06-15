package de.stuebingerb.kgraphql.schema.dsl.types

import de.stuebingerb.kgraphql.schema.SchemaException
import de.stuebingerb.kgraphql.schema.model.ast.ValueNode
import de.stuebingerb.kgraphql.schema.scalar.ScalarCoercion
import de.stuebingerb.kgraphql.schema.scalar.StringScalarCoercion
import kotlin.reflect.KClass

class StringScalarDSL<T : Any>(kClass: KClass<T>) : ScalarDSL<T, String>(kClass) {

    override fun createCoercionFromFunctions(): ScalarCoercion<T, String> {
        return object : StringScalarCoercion<T> {

            val serializeImpl = serialize ?: throw SchemaException(PLEASE_SPECIFY_COERCION)

            val deserializeImpl = deserialize ?: throw SchemaException(PLEASE_SPECIFY_COERCION)

            override fun serialize(instance: T): String = serializeImpl(instance)

            override fun deserialize(raw: String, valueNode: ValueNode): T = deserializeImpl(raw)
        }
    }
}
