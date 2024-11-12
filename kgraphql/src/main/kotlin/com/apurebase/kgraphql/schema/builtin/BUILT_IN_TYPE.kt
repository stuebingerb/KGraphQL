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
import com.apurebase.kgraphql.schema.scalar.StringScalarCoercion

private const val STRING_DESCRIPTION =
    "The String scalar type represents textual data, represented as UTF-8 character sequences"

private const val SHORT_DESCRIPTION =
    "The Short scalar type represents a signed 16-bit numeric non-fractional value"

private const val INT_DESCRIPTION =
    "The Int scalar type represents a signed 32-bit numeric non-fractional value"

private const val LONG_DESCRIPTION =
    "The Long scalar type represents a signed 64-bit numeric non-fractional value"

private const val FLOAT_DESCRIPTION =
    "The Float scalar type represents signed double-precision fractional values as specified by IEEE 754"

private const val BOOLEAN_DESCRIPTION =
    "The Boolean scalar type represents true or false"

/**
 * These scalars are created only for sake of documentation in introspection, not during execution
 */
object BUILT_IN_TYPE {

    val STRING = TypeDef.Scalar(String::class.defaultKQLTypeName(), String::class, STRING_COERCION, STRING_DESCRIPTION)

    val SHORT = TypeDef.Scalar(Short::class.defaultKQLTypeName(), Short::class, SHORT_COERCION, SHORT_DESCRIPTION)

    val INT = TypeDef.Scalar(Int::class.defaultKQLTypeName(), Int::class, INT_COERCION, INT_DESCRIPTION)

    // GraphQL does not differ float and double, treat double like float
    val DOUBLE = TypeDef.Scalar(Float::class.defaultKQLTypeName(), Double::class, DOUBLE_COERCION, FLOAT_DESCRIPTION)

    val FLOAT = TypeDef.Scalar(Float::class.defaultKQLTypeName(), Float::class, FLOAT_COERCION, FLOAT_DESCRIPTION)

    val BOOLEAN =
        TypeDef.Scalar(Boolean::class.defaultKQLTypeName(), Boolean::class, BOOLEAN_COERCION, BOOLEAN_DESCRIPTION)

    val LONG = TypeDef.Scalar(Long::class.defaultKQLTypeName(), Long::class, LONG_COERCION, LONG_DESCRIPTION)
}

object STRING_COERCION : StringScalarCoercion<String> {
    override fun serialize(instance: String): String = instance

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is StringValueNode -> valueNode.value
        else -> throw InvalidInputValueException(
            "Cannot coerce ${valueNode.valueNodeName} to string constant",
            valueNode
        )
    }
}

object DOUBLE_COERCION : StringScalarCoercion<Double> {
    override fun serialize(instance: Double): String = instance.toString()

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is DoubleValueNode -> valueNode.value
        is NumberValueNode -> valueNode.value.toDouble()
        else -> throw InvalidInputValueException(
            "Cannot coerce ${valueNode.valueNodeName} to numeric constant",
            valueNode
        )
    }
}

object FLOAT_COERCION : StringScalarCoercion<Float> {
    override fun serialize(instance: Float): String = instance.toDouble().toString()

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is DoubleValueNode -> DOUBLE_COERCION.deserialize(raw, valueNode).toFloat()
        is NumberValueNode -> DOUBLE_COERCION.deserialize(raw, valueNode).toFloat()
        else -> throw InvalidInputValueException(
            "Cannot coerce ${valueNode.valueNodeName} to numeric constant",
            valueNode
        )
    }
}

object INT_COERCION : StringScalarCoercion<Int> {
    override fun serialize(instance: Int): String = instance.toString()

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is NumberValueNode -> when {
            valueNode.value > Integer.MAX_VALUE -> throw InvalidInputValueException(
                "Cannot coerce to type of Int as '${valueNode.value}' is greater than (2^-31)-1",
                valueNode
            )

            valueNode.value < Integer.MIN_VALUE -> throw InvalidInputValueException(
                "Cannot coerce to type of Int as '${valueNode.value}' is less than -(2^-31)",
                valueNode
            )

            else -> valueNode.value.toInt()
        }

        else -> throw InvalidInputValueException(
            "Cannot coerce ${valueNode.valueNodeName} to numeric constant",
            valueNode
        )
    }
}

object SHORT_COERCION : StringScalarCoercion<Short> {
    override fun serialize(instance: Short): String = instance.toString()

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is NumberValueNode -> when {
            valueNode.value > Short.MAX_VALUE -> throw InvalidInputValueException(
                "Cannot coerce to type of Int as '${valueNode.value}' is greater than (2^-15)-1",
                valueNode
            )

            valueNode.value < Short.MIN_VALUE -> throw InvalidInputValueException(
                "Cannot coerce to type of Int as '${valueNode.value}' is less than -(2^-15)",
                valueNode
            )

            else -> valueNode.value.toShort()
        }

        else -> throw InvalidInputValueException(
            "Cannot coerce ${valueNode.valueNodeName} to numeric constant",
            valueNode
        )
    }
}

object LONG_COERCION : StringScalarCoercion<Long> {
    override fun serialize(instance: Long): String = instance.toString()

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is NumberValueNode -> valueNode.value
        else -> throw InvalidInputValueException(
            "Cannot coerce ${valueNode.valueNodeName} to expected numeric constant",
            valueNode
        )
    }
}


object BOOLEAN_COERCION : StringScalarCoercion<Boolean> {
    override fun serialize(instance: Boolean): String = instance.toString()

    override fun deserialize(raw: String, valueNode: ValueNode) = when (valueNode) {
        is BooleanValueNode -> valueNode.value
        is StringValueNode -> when {
            valueNode.value.equals("true", true) -> true
            valueNode.value.equals("false", true) -> false
            else -> throw IllegalArgumentException("${valueNode.value} does not represent valid Boolean value")
        }

        is NumberValueNode -> when (valueNode.value) {
            0L, -1L -> false
            1L -> true
            else -> throw IllegalArgumentException("${valueNode.value} does not represent valid Boolean value")
        }

        else -> throw InvalidInputValueException(
            "Cannot coerce ${valueNode.valueNodeName} to numeric constant",
            valueNode
        )
    }
}
