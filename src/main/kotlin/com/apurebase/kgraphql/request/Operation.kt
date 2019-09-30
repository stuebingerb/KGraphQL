package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.request.graph.SelectionTree


data class Operation(
    val selectionTree: SelectionTree,
    val variables: List<OperationVariable>?,
    val name: String?,
    val action: Action?
) {

    enum class Action {
        QUERY, MUTATION;

        companion object {
            fun parse(input: String?) = input?.let {
                valueOf(it.toUpperCase())
            }
        }
    }
}
