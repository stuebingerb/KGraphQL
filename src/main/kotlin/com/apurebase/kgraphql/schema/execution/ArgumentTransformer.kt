package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.RequestException
import com.apurebase.kgraphql.isLiteral
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.jol.ast.ValueNode
import com.apurebase.kgraphql.schema.scalar.deserializeScalar
import com.apurebase.kgraphql.schema.structure2.InputValue
import com.apurebase.kgraphql.schema.structure2.Type
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure


open class ArgumentTransformer(val schema : DefaultSchema) {

    private fun transformValue(type: Type, value: ValueNode, variables: Variables) : Any? {
        val kType = type.toKType()
        val typeName = type.unwrapped().name

        return when {
            value is ValueNode.VariableNode -> {
                variables.get(kType.jvmErasure, kType, typeName, value) { subValue ->
                    transformValue(type, subValue, variables)
                }
            }
            value is ValueNode.StringValueNode && value.value.startsWith("$") -> {
                throw TODO("Delete this case")
//                variables.get(kType.jvmErasure, kType, typeName, value) { subValue ->
//                    transformValue(type, subValue, variables)
//                }
            }
            value is ValueNode.NullValueNode && type.isNullable() -> null
            value is ValueNode.NullValueNode && type.isNotNullable() -> {
                throw RequestException("argument '${value.valueNodeName}' is not valid value of type ${type.unwrapped().name}")
            }
            else -> {
                return transformString(value, kType)
            }
        }

    }

    private fun transformString(value: ValueNode, kType: KType): Any {

        val kClass = kType.jvmErasure

        fun throwInvalidEnumValue(enumType : Type.Enum<*>){
            throw RequestException(
                    "Invalid enum ${schema.model.enums[kClass]?.name} value. Expected one of ${enumType.values}"
            )
        }

        schema.model.enums[kClass]?.let { enumType ->
            return if (value is ValueNode.EnumValueNode) {
                enumType.values.find { it.name == value.value }?.value ?: throwInvalidEnumValue(enumType)
            } else throw RequestException("String literal '${value.valueNodeName}' is invalid value for enum type ${enumType.name}")
        } ?: schema.model.scalars[kClass]?.let { scalarType ->
            return deserializeScalar(scalarType, value)
        } ?: throw RequestException("Invalid argument value '${value.valueNodeName}' for type ${schema.model.inputTypes[kClass]?.name}")
    }

    fun transformCollectionElementValue(inputValue: InputValue<*>, value: ValueNode, variables: Variables): Any? {
        assert(inputValue.type.isList())
        val elementType = inputValue.type.unwrapList().ofType as Type?
                ?: throw ExecutionException("Unable to handle value of element of collection without type")

        return transformValue(elementType, value, variables)
    }

    fun transformPropertyValue(parameter: InputValue<*>, value: ValueNode, variables: Variables): Any? {
        return transformValue(parameter.type, value, variables)
    }
}
