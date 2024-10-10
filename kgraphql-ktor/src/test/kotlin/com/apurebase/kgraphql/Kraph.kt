package com.apurebase.kgraphql

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

// MARK: Using this is a temporary solution as JCenter is closed down.
//       We should either create our own GraphQL client or find some other active library to do this for us.

fun graphqlQuery(block: Kraph.() -> Unit): Kraph {
    return Kraph("query").apply(block)
}

fun graphqlMutation(block: Kraph.() -> Unit): Kraph {
    return Kraph("mutation").apply(block)
}

class Kraph(
    private val type: String,
    private val variables: MutableList<Variable> = mutableListOf(),
    private val root: Boolean = true,
) : List<Kraph.Variable> by variables {

    private val fields = mutableListOf<Kraph>()

    fun field(name: String) = fields.add(Kraph(name, variables, false))
    fun field(name: String, block: Kraph.() -> Unit) = fields.add(Kraph(name, variables, false).apply(block))

    data class Variable(
        val name: String,
        val typeName: String,
        val value: JsonElement,
    )

    fun variable(name: String, typeName: String, block: JsonObjectBuilder.() -> Unit) = variables.add(
        Variable(name, typeName, buildJsonObject(block))
    )

    fun variable(name: String, typeName: String, value: String) = variables.add(
        Variable(name, typeName, JsonPrimitive(value))
    )

    fun variable(name: String, typeName: String, value: Int) = variables.add(
        Variable(name, typeName, JsonPrimitive(value))
    )

    private fun print(): String = buildString {
        append("$type ")
        if (variables.isNotEmpty() && root) {
            append("MyQuery(${variables.joinToString(",") { "\$${it.name}: ${it.typeName}" }})")
        }
        if (fields.isNotEmpty()) {
            appendLine("{")
            fields.map { appendLine(it.print()) }
            appendLine("}")
        } else appendLine()
    }

    fun build(): String = buildString {
        append("{")
        append("\"query\": \"${print()}\"")

        if (variables.isNotEmpty()) {
            append(",")
            append("\"variables\": {")
            variables.map {
                append("\"${it.name}\": ${it.value}")
            }
            append("}")
        }
        append("}")
    }
}
