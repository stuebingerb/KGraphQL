package com.apurebase.kgraphql.schema.jol.error

import com.apurebase.kgraphql.schema.jol.ast.ASTNode
import com.apurebase.kgraphql.schema.jol.ast.Location.Companion.getLocation
import com.apurebase.kgraphql.schema.jol.ast.Source

class GraphQLError(

    /**
     * A message describing the Error for debugging purposes.
     *
     * Enumerable, and appears in the result of JSON.stringify().
     *
     * Note: should be treated as readonly, despite invariant usage.
     */
    message: String,

    /**
     * An array describing the JSON-path into the execution response which
     * corresponds to this error. Only included for errors during execution.
     *
     * Enumerable, and appears in the result of JSON.stringify().
     */
    val path: List<String>? = null,

    /**
     * An array of GraphQL AST Nodes corresponding to this error.
     */
    val nodes: List<ASTNode>? = null,

    /**
     * The source GraphQL document for the first location of this error.
     *
     * Note that if this Error represents more than one node, the source may not
     * represent nodes after the first node.
     */
    val source: Source? = null,

    /**
     * An array of character offsets within the source GraphQL document
     * which correspond to this error.
     */
    val positions: List<Int>? = null,

    /**
     * The original error thrown from a field resolver during execution.
     */
    val originalError: Exception? = null
) : Exception(message) {

    /**
     * An array of { line, column } locations within the source GraphQL document
     * which correspond to this error.
     *
     * Errors during validation often contain multiple locations, for example to
     * point out two things with the same name. Errors during execution include a
     * single location, the field which produced the error.
     *
     * Enumerable, and appears in the result of JSON.stringify().
     */
    val locations: List<Source.LocationSource>? by lazy {
            if (positions != null && source != null) {
            positions.map { pos -> getLocation(source, pos) }
        } else nodes?.mapNotNull { node ->
            node.loc?.let { getLocation(it.source, it.start) }
        }
    }

    fun prettyPrint(): String {
        var output = message ?: ""

        if (nodes != null) {
            for (node in nodes) {
                if (node.loc != null) {
                    output += "\n\n" + node.loc!!.printLocation()
                }
            }
        } else if (source != null && locations != null) {
            for (location in locations!!) {
                output += "\n\n" + source.print(location)
            }
        }

        return output
    }
}
