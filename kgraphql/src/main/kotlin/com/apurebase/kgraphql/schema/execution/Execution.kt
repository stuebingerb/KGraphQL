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
    abstract val parent: Execution?
    abstract val fullPath: List<Any>

    internal abstract fun withParent(parent: Execution): Execution

    @NotIntrospected
    open class Node(
        override val selectionNode: SelectionNode.FieldNode,
        val field: Field,
        val children: Collection<Execution>,
        val arguments: ArgumentNodes?,
        override val directives: Map<Directive, ArgumentNodes?>?,
        val variables: List<VariableDefinitionNode>?,
        val arrayIndex: Int?,
        override val parent: Execution?
    ) : Execution() {
        val key = selectionNode.name.value
        val aliasOrKey = selectionNode.aliasOrName.value

        override val fullPath = parent?.fullPath.orEmpty() + aliasOrKey + listOfNotNull(arrayIndex)

        internal fun withIndex(index: Int): Node = Node(
            selectionNode,
            field,
            children,
            arguments,
            directives,
            variables,
            index,
            parent
        )

        override fun withParent(parent: Execution): Node = Node(
            selectionNode,
            field,
            children,
            arguments,
            directives,
            variables,
            arrayIndex,
            parent
        )
    }

    class Fragment(
        override val selectionNode: SelectionNode.FragmentNode,
        val condition: TypeCondition,
        val elements: List<Execution>,
        override val directives: Map<Directive, ArgumentNodes?>?,
        override val parent: Execution?
    ) : Execution() {
        override val fullPath = parent?.fullPath.orEmpty()

        override fun withParent(parent: Execution): Fragment = Fragment(
            selectionNode,
            condition,
            elements,
            directives,
            parent
        )
    }

    class Union(
        node: SelectionNode.FieldNode,
        val unionField: Field.Union<*>,
        val memberChildren: Map<Type, Collection<Execution>>,
        directives: Map<Directive, ArgumentNodes?>?,
        parent: Execution?
    ) : Node(
        selectionNode = node,
        field = unionField,
        children = emptyList(),
        arguments = null,
        directives = directives,
        variables = null,
        arrayIndex = null,
        parent = parent
    ) {
        fun memberExecution(type: Type): Node = Node(
            selectionNode = selectionNode,
            field = field,
            children = requireNotNull(memberChildren[type]) {
                "Union ${unionField.name} has no member $type"
            },
            arguments = arguments,
            directives = directives,
            variables = variables,
            arrayIndex = arrayIndex,
            parent = parent
        )

        override fun withParent(parent: Execution): Union = Union(
            selectionNode,
            unionField,
            memberChildren,
            directives,
            parent
        )
    }

    class Remote(
        selectionNode: SelectionNode.FieldNode,
        field: Field,
        children: Collection<Execution>,
        arguments: ArgumentNodes?,
        directives: Map<Directive, ArgumentNodes?>?,
        variables: List<VariableDefinitionNode>?,
        val namedFragments: List<Fragment>?,
        val remoteUrl: String,
        val remoteOperation: String,
        val operationType: OperationTypeNode,
        parent: Execution?
    ) : Node(selectionNode, field, children, arguments, directives, variables, null, parent) {
        override fun withParent(parent: Execution): Remote = Remote(
            selectionNode,
            field,
            children,
            arguments,
            directives,
            variables,
            namedFragments,
            remoteUrl,
            remoteOperation,
            operationType,
            parent
        )
    }
}
