package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.scalar.deserializeScalar
import com.apurebase.kgraphql.schema.structure.InputValue
import com.apurebase.kgraphql.schema.structure.Type
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

open class ArgumentTransformer(val schema: DefaultSchema) {

    private fun transformValue(type: Type, value: ValueNode, variables: Variables): Any? {
        val kType = type.toKType()
        val typeName = type.unwrapped().name

        return when {
            value is ValueNode.VariableNode -> {
                variables.get(kType.jvmErasure, kType, typeName, value) { subValue ->
                    transformValue(type, subValue, variables)
                }
            }

            type.isList() && value !is ValueNode.ListValueNode -> {
                if (type.isNullable() && value is ValueNode.NullValueNode) {
                    null
                } else {
                    throw InvalidInputValueException(
                        "argument '${value.valueNodeName}' is not valid value of type List",
                        value
                    )
                }
            }

            value is ValueNode.ObjectValueNode -> {
                // SchemaCompilation ensures that input types have a primaryConstructor
                val constructor = checkNotNull(type.unwrapped().kClass?.primaryConstructor)
                val params = constructor.parameters.associateBy { it.name }
                val valueMap = value.fields.associate { valueField ->
                    val inputField = type
                        .unwrapped()
                        .inputFields
                        ?.firstOrNull { it.name == valueField.name.value }
                        ?: throw InvalidInputValueException(
                            "Constructor parameter '${valueField.name.value}' cannot be found in '${type.unwrapped().kClass!!.simpleName}'",
                            value
                        )

                    val paramType = inputField.type as? Type
                        ?: throw InvalidInputValueException(
                            "Something went wrong while searching for the constructor parameter type '${valueField.name.value}'",
                            value
                        )

                    params.getValue(valueField.name.value) to transformValue(paramType, valueField.value, variables)
                }

                val missingNonOptionalInputs = params.values.filter { !it.isOptional && !valueMap.containsKey(it) }

                if (missingNonOptionalInputs.isNotEmpty()) {
                    val inputs = missingNonOptionalInputs.map { it.name }.joinToString(",")
                    throw InvalidInputValueException("missing non-optional input fields: $inputs", value)
                }

                constructor.callBy(valueMap)
            }

            value is ValueNode.NullValueNode -> {
                if (type.isNotNullable()) {
                    throw InvalidInputValueException(
                        "argument '${value.valueNodeName}' is not valid value of type ${type.unwrapped().name}",
                        value
                    )
                } else {
                    null
                }
            }

            value is ValueNode.ListValueNode -> {
                if (type.isNotList()) {
                    throw InvalidInputValueException(
                        "argument '${value.valueNodeName}' is not valid value of type ${type.unwrapped().name}",
                        value
                    )
                } else {
                    value.values.map { valueNode ->
                        transformValue(type.unwrapList(), valueNode, variables)
                    }
                }
            }

            else -> transformString(value, kType)
        }
    }

    private fun transformString(value: ValueNode, kType: KType): Any {

        val kClass = kType.jvmErasure

        fun throwInvalidEnumValue(enumType: Type.Enum<*>) {
            throw InvalidInputValueException(
                "Invalid enum ${schema.model.enums[kClass]?.name} value. Expected one of ${enumType.values.map { it.value }}",
                value
            )
        }

        schema.model.enums[kClass]?.let { enumType ->
            return if (value is ValueNode.EnumValueNode) {
                enumType.values.find { it.name == value.value }?.value ?: throwInvalidEnumValue(enumType)
            } else {
                throw InvalidInputValueException(
                    "String literal '${value.valueNodeName}' is invalid value for enum type ${enumType.name}",
                    value
                )
            }
        } ?: schema.model.scalars[kClass]?.let { scalarType ->
            return deserializeScalar(scalarType, value)
        } ?: throw InvalidInputValueException(
            "Invalid argument value '${value.valueNodeName}' for type ${schema.model.inputTypes[kClass]?.name}",
            value
        )
    }

    fun transformCollectionElementValue(inputValue: InputValue<*>, value: ValueNode, variables: Variables): Any? {
        assert(inputValue.type.isList())
        val elementType = inputValue.type.unwrapList().ofType as Type?
            ?: throw ExecutionException("Unable to handle value of element of collection without type", value)

        return transformValue(elementType, value, variables)
    }

    fun transformPropertyValue(parameter: InputValue<*>, value: ValueNode, variables: Variables): Any? {
        return transformValue(parameter.type, value, variables)
    }

    fun transformPropertyObjectValue(
        parameter: InputValue<*>,
        value: ValueNode.ObjectValueNode,
        variables: Variables
    ): Any? {
        return transformValue(parameter.type, value, variables)
    }
}
