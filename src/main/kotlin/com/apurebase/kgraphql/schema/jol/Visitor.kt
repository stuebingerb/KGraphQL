package com.apurebase.kgraphql.schema.jol

import com.apurebase.kgraphql.schema.jol.ast.ASTNode
import java.util.*

sealed class Visitor {

    data class EnterLeave<T>(
        val enter: T?,
        val leave: T?
    ) : Visitor()

    data class ShapeMap<O, F>(
        val map: MutableMap<O, F> = mutableMapOf()
    ) : Visitor()


    companion object {
        fun visit(
            root: ASTNode,
            visitor: Visitor,
            visitorKeys: Map<String, List<String>> = QueryDocumentKeys
        ): ASTNode {
            var stack: Any? = null
            var keys: List<Any>? = null
            var index = -1
            var edits = mutableListOf<Pair<ASTNode, ASTNode>>()
            var node: ASTNode? = null
            var key: Any? = null
            var parent: ASTNode? = null
            val path = mutableListOf<Any>()
            val ancestors = Stack<ASTNode>()
            var newRoot = root

            do {
                index++
                val isLeaving = index == keys?.size
                val isEdited = isLeaving && edits.isNotEmpty()
                if (isLeaving) {
                    key = if (ancestors.isEmpty()) null else path.last()
                    node = parent
                    parent = ancestors.pop()
                    if (isEdited) {
                        @Suppress("ConstantConditionIf", "ControlFlowWithEmptyBody")
                        if (false) {
                            // node = node.slice();
                        } else {
//                            node = node?.copy()
                            throw TODO("")
                        }
                        var editOffset = 0
                    }
                }




            } while (stack != null)

            if (edits.size != 0) {
                newRoot = edits[edits.size -1].second
            }

            return newRoot
        }

        val QueryDocumentKeys = mapOf(
            "Name" to emptyList(),

            "Document" to listOf("definitions"),
            "OperationDefinition" to listOf(
                "name",
                "variableDefinitions",
                "directives",
                "selectionSet"
            ),
            "VariableDefinition" to listOf("variable", "type", "defaultValue", "directives"),
            "Variable" to listOf("name"),
            "SelectionSet" to listOf("selections"),
            "Field" to listOf("alias", "name", "arguments", "directives", "selectionSet"),
            "Argument" to listOf("name", "value"),

            "FragmentSpread" to listOf("name", "directives"),
            "InlineFragment" to listOf("typeCondition", "directives", "selectionSet"),
            "FragmentDefinition" to listOf(
                "name",
                // Note: fragment variable definitions are experimental and may be changed
                // or removed in the future.
                "variableDefinitions",
                "typeCondition",
                "directives",
                "selectionSet"
            ),

            "IntValue" to listOf(),
            "FloatValue" to listOf(),
            "StringValue" to listOf(),
            "BooleanValue" to listOf(),
            "NullValue" to listOf(),
            "EnumValue" to listOf(),
            "ListValue" to listOf("values"),
            "ObjectValue" to listOf("fields"),
            "ObjectField" to listOf("name", "value"),

            "Directive" to listOf("name", "arguments"),

            "NamedType" to listOf("name"),
            "ListType" to listOf("type"),
            "NonNullType" to listOf("type"),

            "SchemaDefinition" to listOf("directives", "operationTypes"),
            "OperationTypeDefinition" to listOf("type"),

            "ScalarTypeDefinition" to listOf("description", "name", "directives"),
            "ObjectTypeDefinition" to listOf(
                "description",
                "name",
                "interfaces",
                "directives",
                "fields"
            ),
            "FieldDefinition" to listOf("description", "name", "arguments", "type", "directives"),
            "InputValueDefinition" to listOf(
                "description",
                "name",
                "type",
                "defaultValue",
                "directives"
            ),
            "InterfaceTypeDefinition" to listOf("description", "name", "directives", "fields"),
            "UnionTypeDefinition" to listOf("description", "name", "directives", "types"),
            "EnumTypeDefinition" to listOf("description", "name", "directives", "values"),
            "EnumValueDefinition" to listOf("description", "name", "directives"),
            "InputObjectTypeDefinition" to listOf("description", "name", "directives", "fields"),

            "DirectiveDefinition" to listOf("description", "name", "arguments", "locations"),

            "SchemaExtension" to listOf("directives", "operationTypes"),

            "ScalarTypeExtension" to listOf("name", "directives"),
            "ObjectTypeExtension" to listOf("name", "interfaces", "directives", "fields"),
            "InterfaceTypeExtension" to listOf("name", "directives", "fields"),
            "UnionTypeExtension" to listOf("name", "directives", "types"),
            "EnumTypeExtension" to listOf("name", "directives", "values"),
            "InputObjectTypeExtension" to listOf("name", "directives", "fields")
        )
    }
}
