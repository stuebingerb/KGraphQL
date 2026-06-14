package de.stuebingerb.kgraphql.schema.scalar

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import de.stuebingerb.kgraphql.GraphQLError
import de.stuebingerb.kgraphql.InvalidInputValueException
import de.stuebingerb.kgraphql.dropQuotes
import de.stuebingerb.kgraphql.schema.builtin.BOOLEAN_COERCION
import de.stuebingerb.kgraphql.schema.builtin.DOUBLE_COERCION
import de.stuebingerb.kgraphql.schema.builtin.FLOAT_COERCION
import de.stuebingerb.kgraphql.schema.builtin.INT_COERCION
import de.stuebingerb.kgraphql.schema.builtin.LONG_COERCION
import de.stuebingerb.kgraphql.schema.builtin.SHORT_COERCION
import de.stuebingerb.kgraphql.schema.builtin.STRING_COERCION
import de.stuebingerb.kgraphql.schema.execution.Execution
import de.stuebingerb.kgraphql.schema.model.ast.ValueNode
import de.stuebingerb.kgraphql.schema.structure.Type

private typealias JsonValueNode = com.fasterxml.jackson.databind.node.ValueNode

@Suppress("UNCHECKED_CAST")
// TODO: Re-structure scalars, as it's a bit too complicated now.
internal fun <T : Any> deserializeScalar(scalar: Type.Scalar<T>, value: ValueNode, executionNode: Execution): T =
    try {
        when (scalar.coercion) {
            // built-in scalars
            STRING_COERCION -> STRING_COERCION.deserialize(value.valueNodeName, value) as T
            FLOAT_COERCION -> FLOAT_COERCION.deserialize(value.valueNodeName, value) as T
            DOUBLE_COERCION -> DOUBLE_COERCION.deserialize(value.valueNodeName, value) as T
            SHORT_COERCION -> SHORT_COERCION.deserialize(value.valueNodeName, value) as T
            INT_COERCION -> INT_COERCION.deserialize(value.valueNodeName, value) as T
            BOOLEAN_COERCION -> BOOLEAN_COERCION.deserialize(value.valueNodeName, value) as T
            LONG_COERCION -> LONG_COERCION.deserialize(value.valueNodeName, value) as T

            is StringScalarCoercion<T> -> scalar.coercion.deserialize(value.valueNodeName.dropQuotes(), value)
            is ShortScalarCoercion<T> -> scalar.coercion.deserialize(value.valueNodeName.toShort(), value)
            is IntScalarCoercion<T> -> scalar.coercion.deserialize(value.valueNodeName.toInt(), value)
            is DoubleScalarCoercion<T> -> scalar.coercion.deserialize(value.valueNodeName.toDouble(), value)
            is BooleanScalarCoercion<T> -> scalar.coercion.deserialize(value.valueNodeName.toBoolean(), value)
            is LongScalarCoercion<T> -> scalar.coercion.deserialize(value.valueNodeName.toLong(), value)
        }
    } catch (e: Exception) {
        throw InvalidInputValueException(
            message = (e as? GraphQLError)?.message ?: "Cannot coerce '${value.valueNodeName}' to ${scalar.name}",
            node = executionNode,
            originalError = e
        )
    }

@Suppress("UNCHECKED_CAST")
internal fun <T> serializeScalar(jsonNodeFactory: JsonNodeFactory, scalar: Type.Scalar<*>, value: T): JsonValueNode =
    when (scalar.coercion) {
        is StringScalarCoercion<*> -> {
            jsonNodeFactory.textNode((scalar.coercion as StringScalarCoercion<T>).serialize(value))
        }

        is ShortScalarCoercion<*> -> {
            jsonNodeFactory.numberNode((scalar.coercion as ShortScalarCoercion<T>).serialize(value))
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
    }
