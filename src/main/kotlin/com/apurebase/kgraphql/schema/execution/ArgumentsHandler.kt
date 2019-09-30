package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.RequestException
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.jol.ast.ArgumentNodes
import com.apurebase.kgraphql.schema.jol.ast.ValueNode.*
import com.apurebase.kgraphql.schema.structure2.InputValue


internal class ArgumentsHandler(schema : DefaultSchema) : ArgumentTransformer(schema) {

    fun transformArguments (
        funName: String,
        inputValues: List<InputValue<*>>,
        args: ArgumentNodes?,
        variables: Variables,
        requestContext: Context
    ) : List<Any?>{
        val unsupportedArguments = args?.filter { arg ->
            inputValues.none { value -> value.name == arg.key }
        }

        if(unsupportedArguments?.isNotEmpty() == true){
            throw RequestException("$funName does support arguments ${inputValues.map { it.name }}. found arguments ${args.keys}"
            )
        }

        return inputValues.map { parameter ->
            val value = args?.get(parameter.name)

            parameter.type.isInstance(requestContext)

            when {
                //inject request context
                parameter.type.isInstance(requestContext) -> requestContext
                value == null && parameter.type.kind != TypeKind.NON_NULL -> parameter.default
                value == null && parameter.type.kind == TypeKind.NON_NULL -> {
                    parameter.default ?: throw RequestException(
                            "argument '${parameter.name}' of type ${schema.typeReference(parameter.type)} " +
                                    "on field '$funName' is not nullable, value cannot be null"
                    )
                }
                value is StringValueNode ||
                        value is EnumValueNode ||
                        value is NumberValueNode ||
                        value is DoubleValueNode ||
                        value is VariableNode ||
                        value is BooleanValueNode ||
                        value is NullValueNode-> {
                    val transformedValue = transformPropertyValue(parameter, value, variables)
                    if (transformedValue == null && parameter.type.isNotNullable()) {
                        throw RequestException("argument ${parameter.name} is not optional, value cannot be null")
                    }
                    transformedValue
                }
                value is ListValueNode && parameter.type.isList() -> {
                    value.values.map { element ->
                        when (element) {
                            is StringValueNode,
                            is EnumValueNode,
                            is NumberValueNode,
                            is DoubleValueNode,
                            is VariableNode,
                            is BooleanValueNode,
                            is NullValueNode -> transformCollectionElementValue(parameter, element, variables)
                            else -> throw ExecutionException("Unexpected non-string list element")
                        }
                    }
                }
                value is ListValueNode && parameter.type.isNotList() -> {
                    throw RequestException("Invalid list value passed to non-list argument")
                }
                else -> throw RequestException("Non string arguments are not supported yet")
            }
        }
    }
}
