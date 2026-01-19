@file:Suppress("ClassName")

package com.apurebase.kgraphql.schema.builtin

import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.defaultKQLTypeName
import com.apurebase.kgraphql.schema.model.TypeDef
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode.BooleanValueNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode.DoubleValueNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode.NumberValueNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode.StringValueNode
import com.apurebase.kgraphql.schema.scalar.ID
import com.apurebase.kgraphql.schema.scalar.StringScalarCoercion

private const val STRING_DESCRIPTION =
    "The String scalar type represents textual data, represented as UTF-8 character sequences"

private const val SHORT_DESCRIPTION = "The Short scalar type represents a signed 16-bit numeric non-fractional value"

private const val INT_DESCRIPTION = "The Int scalar type represents a signed 32-bit numeric non-fractional value"

private const val LONG_DESCRIPTION = "The Long scalar type represents a signed 64-bit numeric non-fractional value"

private const val FLOAT_DESCRIPTION =
    "The Float scalar type represents signed double-precision fractional values as specified by IEEE 754"

private const val BOOLEAN_DESCRIPTION = "The Boolean scalar type represents true or false"

private const val ID_DESCRIPTION =
    "The ID scalar type represents a unique identifier, often used to refetch an object or as the key for a cache"

/**
 * https://spec.graphql.org/October2021/#sec-Scalars.Built-in-Scalars
 */
enum class BuiltInScalars(val typeDef: TypeDef.Scalar<*>) {
    STRING(TypeDef.Scalar(String::class.defaultKQLTypeName(), String::class, STRING_COERCION, STRING_DESCRIPTION)),
    INT(TypeDef.Scalar(Int::class.defaultKQLTypeName(), Int::class, INT_COERCION, INT_DESCRIPTION)),

    // GraphQL does not differentiate between float and double, treat double like float
    DOUBLE(TypeDef.Scalar(Float::class.defaultKQLTypeName(), Double::class, DOUBLE_COERCION, FLOAT_DESCRIPTION)),
    FLOAT(TypeDef.Scalar(Float::class.defaultKQLTypeName(), Float::class, FLOAT_COERCION, FLOAT_DESCRIPTION)),
    BOOLEAN(TypeDef.Scalar(Boolean::class.defaultKQLTypeName(), Boolean::class, BOOLEAN_COERCION, BOOLEAN_DESCRIPTION)),

    ID(
        TypeDef.Scalar(
            com.apurebase.kgraphql.schema.scalar.ID::class.defaultKQLTypeName(),
            com.apurebase.kgraphql.schema.scalar.ID::class,
            ID_COERCION,
            ID_DESCRIPTION
        )
    )
}

enum class ExtendedBuiltInScalars(val typeDef: TypeDef.Scalar<*>) {
    SHORT(TypeDef.Scalar(Short::class.defaultKQLTypeName(), Short::class, SHORT_COERCION, SHORT_DESCRIPTION)),
    LONG(TypeDef.Scalar(Long::class.defaultKQLTypeName(), Long::class, LONG_COERCION, LONG_DESCRIPTION))
}

object STRING_COERCION : StringScalarCoercion<String> {
    override fun serialize(instance: String): String = instance

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is StringValueNode -> valueNode.value

        else -> throw InvalidInputValueException("Cannot coerce '${valueNode.valueNodeName}' to String", valueNode)
    }
}

object DOUBLE_COERCION : StringScalarCoercion<Double> {
    override fun serialize(instance: Double): String = instance.toString()

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is DoubleValueNode -> valueNode.value
        is NumberValueNode -> valueNode.value.toDouble()

        else -> throw InvalidInputValueException("Cannot coerce '${valueNode.valueNodeName}' to Float", valueNode)
    }
}

object FLOAT_COERCION : StringScalarCoercion<Float> {
    override fun serialize(instance: Float): String = instance.toDouble().toString()

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is DoubleValueNode -> valueNode.value.toFloat()
        is NumberValueNode -> valueNode.value.toFloat()

        else -> throw InvalidInputValueException("Cannot coerce '${valueNode.valueNodeName}' to Float", valueNode)
    }
}

object INT_COERCION : StringScalarCoercion<Int> {
    override fun serialize(instance: Int): String = instance.toString()

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is NumberValueNode -> when {
            valueNode.value > Integer.MAX_VALUE -> throw InvalidInputValueException(
                "Cannot coerce '${valueNode.valueNodeName}' to Int as it is greater than (2^-31)-1",
                valueNode
            )

            valueNode.value < Integer.MIN_VALUE -> throw InvalidInputValueException(
                "Cannot coerce '${valueNode.valueNodeName}' to Int as it is less than -(2^-31)",
                valueNode
            )

            else -> valueNode.value.toInt()
        }

        else -> throw InvalidInputValueException("Cannot coerce '${valueNode.valueNodeName}' to Int", valueNode)
    }
}

object SHORT_COERCION : StringScalarCoercion<Short> {
    override fun serialize(instance: Short): String = instance.toString()

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is NumberValueNode -> when {
            valueNode.value > Short.MAX_VALUE -> throw InvalidInputValueException(
                "Cannot coerce '${valueNode.value}' to Short as it is greater than (2^-15)-1",
                valueNode
            )

            valueNode.value < Short.MIN_VALUE -> throw InvalidInputValueException(
                "Cannot coerce '${valueNode.value}' to Short as it is less than -(2^-15)",
                valueNode
            )

            else -> valueNode.value.toShort()
        }

        else -> throw InvalidInputValueException("Cannot coerce '${valueNode.valueNodeName}' to Short", valueNode)
    }
}

object LONG_COERCION : StringScalarCoercion<Long> {
    override fun serialize(instance: Long): String = instance.toString()

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is NumberValueNode -> valueNode.value
        else -> throw InvalidInputValueException("Cannot coerce '${valueNode.valueNodeName}' to Long", valueNode)
    }
}

object BOOLEAN_COERCION : StringScalarCoercion<Boolean> {
    override fun serialize(instance: Boolean): String = instance.toString()

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is BooleanValueNode -> valueNode.value
        is StringValueNode -> when {
            valueNode.value.equals("true", true) -> true
            valueNode.value.equals("false", true) -> false

            else -> throw IllegalArgumentException("Cannot coerce '${valueNode.value}' to Boolean")
        }

        is NumberValueNode -> when (valueNode.value) {
            0L, -1L -> false
            1L -> true

            else -> throw IllegalArgumentException("Cannot coerce '${valueNode.value}' to Boolean")
        }

        else -> throw InvalidInputValueException("Cannot coerce '${valueNode.valueNodeName}' to Boolean", valueNode)
    }
}

object ID_COERCION : StringScalarCoercion<ID> {
    override fun serialize(instance: ID): String = instance.value

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is StringValueNode -> ID(valueNode.value)
        is NumberValueNode -> ID(valueNode.value.toString())

        else -> throw InvalidInputValueException("Cannot coerce '${valueNode.valueNodeName}' to ID", valueNode)
    }
}
