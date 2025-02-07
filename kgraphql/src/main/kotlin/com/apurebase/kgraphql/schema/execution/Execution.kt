package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.introspection.NotIntrospected
import com.apurebase.kgraphql.schema.model.ast.ArgumentNodes
import com.apurebase.kgraphql.schema.model.ast.OperationTypeNode
import com.apurebase.kgraphql.schema.model.ast.SelectionNode
import com.apurebase.kgraphql.schema.model.ast.VariableDefinitionNode
import com.apurebase.kgraphql.schema.structure.Field
import com.apurebase.kgraphql.schema.structure.Type

sealed class Execution {
    abstract val selectionNode: SelectionNode
    abstract val directives: Map<Directive, ArgumentNodes?>?

    @NotIntrospected
    open class Node(
        override val selectionNode: SelectionNode,
        val field: Field,
        val children: Collection<Execution>,
        val key: String,
        val alias: String? = null,
        val arguments: ArgumentNodes? = null,
        override val directives: Map<Directive, ArgumentNodes?>? = null,
        val variables: List<VariableDefinitionNode>? = null
    ) : Execution() {
        val aliasOrKey = alias ?: key
    }

    class Fragment(
        override val selectionNode: SelectionNode,
        val condition: TypeCondition,
        val elements: List<Execution>,
        override val directives: Map<Directive, ArgumentNodes?>?
    ) : Execution()

    class Union(
        node: SelectionNode,
        val unionField: Field.Union<*>,
        val memberChildren: Map<Type, Collection<Execution>>,
        key: String,
        alias: String?,
        directives: Map<Directive, ArgumentNodes?>?
    ) : Node(
        selectionNode = node,
        field = unionField,
        children = emptyList(),
        key = key,
        alias = alias,
        directives = directives
    ) {
        fun memberExecution(type: Type): Node {
            return Node(
                selectionNode = selectionNode,
                field = field,
                children = requireNotNull(memberChildren[type]) {
                    "Union ${unionField.name} has no member $type"
                },
                key = key,
                alias = alias,
                arguments = arguments,
                directives = directives
            )
        }
    }

    class Remote(
        selectionNode: SelectionNode,
        field: Field,
        children: Collection<Execution>,
        key: String,
        alias: String?,
        arguments: ArgumentNodes?,
        directives: Map<Directive, ArgumentNodes?>?,
        variables: List<VariableDefinitionNode>?,
        val namedFragments: List<Fragment>?,
        val remoteUrl: String,
        val remoteOperation: String,
        val operationType: OperationTypeNode
    ) : Node(selectionNode, field, children, key, alias, arguments, directives, variables)
}
