package com.apurebase.kgraphql.schema.model.ast

sealed class ValueNode(override val loc: Location?) : ASTNode() {

    class VariableNode(val name: NameNode, loc: Location?) : ValueNode(loc)

    class NumberValueNode(val value: Long, loc: Location?) : ValueNode(loc) {
        constructor(value: String, loc: Location?) : this(value.toLong(), loc)
    }

    class DoubleValueNode(val value: Double, loc: Location?) : ValueNode(loc) {
        constructor(value: String, loc: Location?) : this(value.toDouble(), loc)
    }

    class StringValueNode(val value: String, val block: Boolean?, loc: Location?) : ValueNode(loc)

    class BooleanValueNode(val value: Boolean, loc: Location?) : ValueNode(loc)

    class NullValueNode(loc: Location?) : ValueNode(loc)

    class EnumValueNode(val value: String, loc: Location?) : ValueNode(loc)

    class ListValueNode(val values: List<ValueNode>, loc: Location?) : ValueNode(loc)

    class ObjectValueNode(val fields: List<ObjectFieldNode>, loc: Location?) : ValueNode(loc) {
        class ObjectFieldNode(
            loc: Location?,
            val name: NameNode,
            val value: ValueNode
        ) : ValueNode(loc)
    }

    private fun String.escaped() = buildString {
        for (char in this@escaped) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                else -> append(char)
            }
        }
    }

    val valueNodeName: String
        get() = when (this) {
            is VariableNode -> "\$${name.value}"
            is NumberValueNode -> "$value"
            is DoubleValueNode -> "$value"
            is StringValueNode -> if (block == true) {
                // TODO: Is escaping needed for block strings?
                "\"\"\"$value\"\"\""
            } else {
                "\"${value.escaped()}\""
            }

            is BooleanValueNode -> "$value"
            is NullValueNode -> "null"
            is EnumValueNode -> value
            is ListValueNode -> values.joinToString(prefix = "[", postfix = "]") { it.valueNodeName }
            is ObjectValueNode -> fields.joinToString(
                prefix = "{",
                postfix = "}"
            ) { "${it.name.value}: ${it.value.valueNodeName}" }

            is ObjectValueNode.ObjectFieldNode -> value.valueNodeName
        }
}
