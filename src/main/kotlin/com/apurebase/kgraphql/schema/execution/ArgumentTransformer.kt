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
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
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
            value is ValueNode.ObjectValueNode -> {
                val params = type.unwrapped().kClass!!.primaryConstructor!!.parameters.associateBy { it.name }
                val valueMap = value.fields.map { valueField ->
                    val paramType = type
                        .unwrapped()
                        .inputFields
                        ?.firstOrNull { it.name == valueField.name.value }
                        ?.type as? Type
                        ?: throw TODO("Handle this")

                    params.getValue(valueField.name.value) to transformValue(paramType, valueField.value, variables)
                }.toMap()

                type.unwrapped().kClass?.primaryConstructor?.callBy(valueMap)
            }
            value is ValueNode.NullValueNode -> {
                if (type.isNotNullable()) {
                    throw RequestException("argument '${value.valueNodeName}' is not valid value of type ${type.unwrapped().name}")
                } else null
            }
            value is ValueNode.ListValueNode && type.isList() -> {
                if (type.isNotList()) {
                    throw RequestException("argument '${value.valueNodeName}' is not valid value of type ${type.unwrapped().name}")
                } else {
                    value.values.map { valueNode ->
                        transformValue(type.unwrapList(), valueNode, variables)
                    }
                }
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

    fun transformPropertyObjectValue(parameter: InputValue<*>, value: ValueNode.ObjectValueNode, variables: Variables): Any? {
        return transformValue(parameter.type, value, variables)
    }
}
