package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.schema.model.ast.TypeNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.model.ast.VariableDefinitionNode
import com.apurebase.kgraphql.schema.structure.Type
import com.fasterxml.jackson.databind.JsonNode

data class Variables(private val variablesJson: VariablesJson, private val variables: List<VariableDefinitionNode>?) {
    internal fun get(type: Type, keyNode: ValueNode.VariableNode, defaultValue: Any? = null): ValueNode? {
        val variable = variables?.firstOrNull { keyNode.name.value == it.variable.name.value }
        if (variable == null) {
            throw ValidationException(
                "Variable '$${keyNode.name.value}' was not declared for this operation",
                listOf(keyNode)
            )
        }

        validateVariable(type, variable, defaultValue)

        return variablesJson.get(type, keyNode.name) ?: variable.defaultValue
    }

    fun getRaw(): JsonNode? = variablesJson.getRaw()

    private fun validateVariable(expectedType: Type, variable: VariableDefinitionNode, defaultValue: Any?) {
        val variableType = variable.type
        val invalidName = expectedType.unwrapped().name != variable.type.nameNode.value
        val invalidIsList = expectedType.isList() != variableType.isList
        val invalidTypeNullability = !expectedType.isNullable() && variableType.isNullable
        val invalidElementNullability = !expectedType.isElementNullable() && when (variableType) {
            is TypeNode.ListTypeNode -> variableType.isElementNullable
            else -> false
        }
        val noDefaultProvided = variable.defaultValue == null && defaultValue == null
        val invalidNullability = (invalidTypeNullability || invalidElementNullability) && noDefaultProvided

        if (invalidName || invalidIsList || invalidNullability) {
            throw InvalidInputValueException(
                "Invalid variable '$${variable.variable.name.value}' argument type '${variableType.nameNode.value}', expected '${expectedType.typeReference()}'",
                variable
            )
        }
    }
}
