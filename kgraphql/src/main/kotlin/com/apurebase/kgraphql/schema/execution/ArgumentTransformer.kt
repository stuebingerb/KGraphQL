package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.model.ast.ArgumentNodes
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode.ObjectValueNode
import com.apurebase.kgraphql.schema.scalar.deserializeScalar
import com.apurebase.kgraphql.schema.structure.InputValue
import com.apurebase.kgraphql.schema.structure.Type
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

open class ArgumentTransformer(val schema: __Schema) {

    fun transformArguments(
        funName: String,
        inputValues: List<InputValue<*>>,
        args: ArgumentNodes?,
        variables: Variables,
        executionNode: Execution,
        requestContext: Context
    ): List<Any?> {
        val unsupportedArguments = args?.filter { arg ->
            inputValues.none { value -> value.name == arg.key }
        }

        if (unsupportedArguments?.isNotEmpty() == true) {
            throw InvalidInputValueException(
                "$funName does support arguments ${inputValues.map { it.name }}. Found arguments ${args.keys}",
                executionNode.selectionNode
            )
        }

        return inputValues.map { parameter ->
            val value = args?.get(parameter.name)

            when {
                // inject request context
                parameter.type.isInstance(requestContext) -> requestContext
                parameter.type.isInstance(executionNode) -> executionNode
                value == null && parameter.type.kind != TypeKind.NON_NULL -> parameter.default
                value == null && parameter.type.kind == TypeKind.NON_NULL -> {
                    parameter.default ?: throw InvalidInputValueException(
                        "argument '${parameter.name}' of type ${parameter.type.typeReference()} on field '$funName' is not nullable, value cannot be null",
                        executionNode.selectionNode
                    )
                }

                else -> {
                    val transformedValue = transformValue(parameter.type, value!!, variables, true)
                    if (transformedValue == null && parameter.type.isNotNullable()) {
                        throw InvalidInputValueException(
                            "argument ${parameter.name} is not optional, value cannot be null",
                            executionNode.selectionNode
                        )
                    }
                    transformedValue
                }
            }
        }
    }

    private fun transformValue(
        type: Type,
        value: ValueNode,
        variables: Variables,
        // Normally, single values should be coerced to a list with one element. But not if that single value is a
        // list of a nested list. Seems strange but cf. https://spec.graphql.org/October2021/#sec-List.Input-Coercion
        // This parameter is used to track if we have seen a ListValueNode in the recursive call chain.
        coerceSingleValueAsList: Boolean
    ): Any? {
        val kType = type.toKType()
        val typeName = type.unwrapped().name

        return when {
            value is ValueNode.VariableNode -> {
                variables.get(kType.jvmErasure, kType, typeName, value) { subValue ->
                    transformValue(type, subValue, variables, coerceSingleValueAsList)
                }
            }

            // https://spec.graphql.org/October2021/#sec-List.Input-Coercion
            // If the value passed as an input to a list type is not a list and not the null value, then the result
            // of input coercion is a list of size one, where the single item value is the result of input coercion
            // for the list's item type on the provided value (note this may apply recursively for nested lists).
            type.isList() && value !is ValueNode.ListValueNode && value !is ValueNode.NullValueNode -> {
                if (coerceSingleValueAsList) {
                    transformToCollection(type.listType(), listOf(value), variables, true)
                } else {
                    throw InvalidInputValueException(
                        "argument '${value.valueNodeName}' is not valid value of type List",
                        value
                    )
                }
            }

            value is ObjectValueNode -> {
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

                    params.getValue(valueField.name.value) to transformValue(
                        paramType,
                        valueField.value,
                        variables,
                        coerceSingleValueAsList
                    )
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
                    transformToCollection(type.listType(), value.values, variables, false)
                }
            }

            else -> transformString(value, type.unwrapped())
        }
    }

    private fun transformToCollection(
        type: Type.AList,
        values: List<ValueNode>,
        variables: Variables,
        coerceSingleValueAsList: Boolean
    ): Collection<*> = if (type.kClass.isSubclassOf(Set::class)) {
        values.mapTo(mutableSetOf()) { valueNode ->
            transformValue(type.unwrapList(), valueNode, variables, coerceSingleValueAsList)
        }
    } else {
        values.map { valueNode ->
            transformValue(type.unwrapList(), valueNode, variables, coerceSingleValueAsList)
        }
    }

    private fun transformString(value: ValueNode, type: Type): Any {

        fun throwInvalidEnumValue(enumType: Type.Enum<*>) {
            throw InvalidInputValueException(
                "Invalid enum ${enumType.name} value. Expected one of ${enumType.values.map { it.value }}",
                value
            )
        }

        (type as? Type.Enum<*>)?.let { enumType ->
            return if (value is ValueNode.EnumValueNode) {
                enumType.values.firstOrNull { it.name == value.value }?.value ?: throwInvalidEnumValue(enumType)
            } else {
                throw InvalidInputValueException(
                    "String literal '${value.valueNodeName}' is invalid value for enum type ${enumType.name}",
                    value
                )
            }
        } ?: (type as? Type.Scalar<*>)?.let { scalarType ->
            return deserializeScalar(scalarType, value)
        } ?: throw InvalidInputValueException(
            "Invalid argument value '${value.valueNodeName}' for type ${type.name}",
            value
        )
    }
}
