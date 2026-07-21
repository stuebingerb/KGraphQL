package de.stuebingerb.kgraphql.schema.execution

import de.stuebingerb.kgraphql.schema.directive.Directive
import de.stuebingerb.kgraphql.schema.introspection.NotIntrospected
import de.stuebingerb.kgraphql.schema.model.ast.ArgumentNodes
import de.stuebingerb.kgraphql.schema.model.ast.OperationTypeNode
import de.stuebingerb.kgraphql.schema.model.ast.SelectionNode
import de.stuebingerb.kgraphql.schema.model.ast.VariableDefinitionNode
import de.stuebingerb.kgraphql.schema.structure.Field

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
