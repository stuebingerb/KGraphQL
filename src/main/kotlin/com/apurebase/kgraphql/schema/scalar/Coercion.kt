package com.apurebase.kgraphql.schema.scalar

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.RequestException
import com.apurebase.kgraphql.schema.builtin.BOOLEAN_COERCION
import com.apurebase.kgraphql.schema.builtin.DOUBLE_COERCION
import com.apurebase.kgraphql.schema.builtin.FLOAT_COERCION
import com.apurebase.kgraphql.schema.builtin.INT_COERCION
import com.apurebase.kgraphql.schema.builtin.LONG_COERCION
import com.apurebase.kgraphql.schema.builtin.STRING_COERCION
import com.apurebase.kgraphql.schema.jol.ast.ValueNode
import com.apurebase.kgraphql.schema.jol.ast.ValueNode.*
import com.apurebase.kgraphql.schema.structure2.Type

// TODO: Restructure how to handle scalars
@Suppress("UNCHECKED_CAST")
fun <T : Any> deserializeScalar(scalar: Type.Scalar<T>, value : ValueNode): T {
    try {
        return when(scalar.coercion){
            //built in scalars
            STRING_COERCION -> STRING_COERCION.deserialize((value as StringValueNode).value) as T
            FLOAT_COERCION -> try {
                (value as NumberValueNode).value.toDouble()
            } catch(e: ClassCastException) {
                (value as DoubleValueNode).value
            } as T
            DOUBLE_COERCION -> try {
                (value as NumberValueNode).value.toDouble()
            } catch(e: ClassCastException) {
                (value as DoubleValueNode).value
            } as T


            INT_COERCION -> INT_COERCION.deserialize((value as NumberValueNode).value.toString()) as T
            BOOLEAN_COERCION -> BOOLEAN_COERCION.deserialize((value as BooleanValueNode).value.toString()) as T
            LONG_COERCION -> LONG_COERCION.deserialize((value as NumberValueNode).value.toString()) as T

            is StringScalarCoercion<T> -> scalar.coercion.deserialize((value as StringValueNode).value)
            is IntScalarCoercion<T> -> scalar.coercion.deserialize((value as NumberValueNode).value.toInt())
            is DoubleScalarCoercion<T> -> scalar.coercion.deserialize(try {
                (value as NumberValueNode).value.toDouble()
            } catch(e: ClassCastException) {
                (value as DoubleValueNode).value
            })
            is BooleanScalarCoercion<T> -> scalar.coercion.deserialize((value as BooleanValueNode).value)
            is LongScalarCoercion<T> -> scalar.coercion.deserialize((value as NumberValueNode).value)
            else -> throw ExecutionException("Unsupported coercion for scalar type ${scalar.name}")
        }
    } catch (e: Exception){
        throw RequestException("argument '${value.valueNodeName}' is not valid value of type ${scalar.name}", e)
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> serializeScalar(jsonNodeFactory: JsonNodeFactory, scalar: Type.Scalar<*>, value: T) = when(scalar.coercion){
    is StringScalarCoercion<*> -> {
        jsonNodeFactory.textNode((scalar.coercion as StringScalarCoercion<T>).serialize(value))
    }
    is IntScalarCoercion<*> -> {
        jsonNodeFactory.numberNode((scalar.coercion as IntScalarCoercion<T>).serialize(value))
    }
    is DoubleScalarCoercion<*> -> {
        jsonNodeFactory.numberNode((scalar.coercion as DoubleScalarCoercion<T>).serialize(value))
    }
    is LongScalarCoercion<*> -> {
        jsonNodeFactory.numberNode((scalar.coercion as LongScalarCoercion<T>).serialize(value))
    }
    is BooleanScalarCoercion<*> -> {
        jsonNodeFactory.booleanNode((scalar.coercion as BooleanScalarCoercion<T>).serialize(value))
    }
    else -> throw ExecutionException("Unsupported coercion for scalar type ${scalar.name}")
}

