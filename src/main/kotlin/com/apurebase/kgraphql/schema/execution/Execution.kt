package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.request.Arguments
import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.structure2.Field
import com.apurebase.kgraphql.schema.structure2.Type
import com.apurebase.kgraphql.schema.jol.ast.ArgumentNodes
import com.apurebase.kgraphql.schema.jol.ast.VariableDefinitionNode
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.*
import com.apurebase.kgraphql.schema.structure2.InputValue
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger


sealed class Execution {

    open class Node (
        val field: Field,
        val children: Collection<Execution>,
        val key : String,
        val alias: String? = null,
        val arguments : ArgumentNodes? = null,
        val typeCondition: TypeCondition? = null,
        val directives: Map<Directive, ArgumentNodes?>? = null,
        val variables: List<VariableDefinitionNode>? = null
    ) : Execution() {
        val aliasOrKey = alias ?: key
    }

    class Fragment(
        val condition: TypeCondition,
        val elements : List<Execution>,
        val directives: Map<Directive, ArgumentNodes?>?
    ) : Execution()

    class Union (
        val unionField: Field.Union<*>,
        val memberChildren: Map<Type, Collection<Execution>>,
        key: String,
        alias: String? = null,
        condition : TypeCondition? = null,
        directives: Map<Directive, ArgumentNodes?>? = null
    ) : Execution.Node (
        field = unionField,
        children = emptyList(),
        key = key,
        alias = alias,
        typeCondition = condition,
        directives = directives
    ) {
        fun memberExecution(type: Type): Execution.Node {
            return Execution.Node (
                field = field,
                children = memberChildren[type] ?: throw IllegalArgumentException("Union ${unionField.name} has no member $type"),
                key = key,
                alias = alias,
                arguments = arguments,
                typeCondition = typeCondition,
                directives = directives,
                variables = null
            )
        }
    }
}
