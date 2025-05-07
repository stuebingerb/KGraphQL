package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.ast.ArgumentNodes
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode.ObjectValueNode
import com.apurebase.kgraphql.schema.scalar.deserializeScalar
import com.apurebase.kgraphql.schema.structure.InputValue
import com.apurebase.kgraphql.schema.structure.Type
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

open class ArgumentTransformer {

    open fun transformArguments(
        funName: String,
        receiver: Any?,
        inputValues: List<InputValue<*>>,
        args: ArgumentNodes?,
        variables: Variables,
        executionNode: Execution,
        requestContext: Context,
        field: FunctionWrapper<*>
    ): List<Any?>? {
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
        return when {
            value is ValueNode.VariableNode -> {
                variables.get(type, value)?.let { transformValue(type, it, variables, coerceSingleValueAsList) }
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
                val constructorParametersByName = constructor.parameters.associateBy { it.name }
                val inputFieldsByName = type.unwrapped().inputFields.orEmpty().associateBy { it.name }

                val providedValuesByKParameter = value.fields.associate { valueField ->
                    val fieldName = valueField.name.value
                    val inputField = inputFieldsByName[fieldName]
                        ?: throw InvalidInputValueException(
                            "Property '$fieldName' on '${type.unwrapped().name}' does not exist", value
                        )

                    val paramType = inputField.type as? Type
                        ?: throw InvalidInputValueException(
                            "Something went wrong while searching for the constructor parameter type '$fieldName'",
                            value
                        )

                    val parameterName = (inputField as? InputValue<*>)?.parameterName ?: fieldName
                    constructorParametersByName[parameterName] to transformValue(
                        paramType,
                        valueField.value,
                        variables,
                        coerceSingleValueAsList
                    )
                }

                // Constructor parameters that are neither provided explicitly nor have a Kotlin default value
                val missingNonOptionalInputs = mutableListOf<String>()
                val valueMap = constructorParametersByName.mapNotNull { (name, parameter) ->
                    if (providedValuesByKParameter.containsKey(parameter)) {
                        // Value was provided: use provided value
                        parameter to providedValuesByKParameter[parameter]
                    } else if (parameter.isOptional) {
                        // Value was not provided but parameter is optional: skip it (and use default from Kotlin)
                        null
                    } else if (parameter.type.isMarkedNullable) {
                        // Value was not provided and parameter is nullable: use null
                        parameter to null
                    } else {
                        // Value was not provided and parameter is required: error
                        val inputField =
                            type.unwrapped().inputFields?.firstOrNull { (it as? InputValue<*>)?.parameterName == name }
                        missingNonOptionalInputs.add(inputField?.name ?: name ?: "Parameter #${parameter.index}")
                        null
                    }
                }.toMap()

                if (missingNonOptionalInputs.isNotEmpty()) {
                    throw InvalidInputValueException(
                        "Missing non-optional input fields: ${missingNonOptionalInputs.joinToString()}", value
                    )
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
