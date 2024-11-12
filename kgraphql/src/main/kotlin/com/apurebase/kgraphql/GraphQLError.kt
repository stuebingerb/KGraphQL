package com.apurebase.kgraphql

import com.apurebase.kgraphql.helpers.toJsonElement
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.model.ast.ASTNode
import com.apurebase.kgraphql.schema.model.ast.Location.Companion.getLocation
import com.apurebase.kgraphql.schema.model.ast.Source
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class BuiltInErrorCodes {
    GRAPHQL_PARSE_FAILED, GRAPHQL_VALIDATION_FAILED, BAD_USER_INPUT, INTERNAL_SERVER_ERROR
}

open class GraphQLError(

    /**
     * A message describing the Error for debugging purposes.
     */
    message: String,

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
    val originalError: Throwable? = null,

    /**
     * The type of the error, based on Apollo Server's built-in error codes:
     *   https://www.apollographql.com/docs/apollo-server/data/errors#built-in-error-codes
     *
     * For supported built-in codes see [BuiltInErrorCodes].
     */
    val extensionsErrorType: String,

    /**
     * Details regarding this error.
     */
    val extensionsErrorDetail: Map<String, Any?>? = null
) : Exception(message) {

    /**
     * An array of { line, column } locations within the source GraphQL document
     * that correspond to this error.
     *
     * Errors during validation often contain multiple locations, for example to
     * point out two things with the same name. Errors during execution include a
     * single location, the field that produced the error.
     */
    val locations: List<Source.LocationSource>? by lazy {
        if (positions != null && source != null) {
            positions.map { pos -> getLocation(source, pos) }
        } else {
            nodes?.mapNotNull { node ->
                node.loc?.let { getLocation(it.source, it.start) }
            }
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

    open val extensions: Map<String, Any?>? by lazy {
        val extensions = mutableMapOf<String, Any?>(
            "type" to extensionsErrorType
        )
        extensionsErrorDetail?.let { extensions.put("detail", extensionsErrorDetail) }
        extensions
    }

    open fun serialize(): String = buildJsonObject {
        put("errors", buildJsonArray {
            addJsonObject {
                put("message", message)
                put("locations", buildJsonArray {
                    locations?.forEach {
                        addJsonObject {
                            put("liane", it.line)
                            put("column", it.column)
                        }
                    }
                })
                put("path", buildJsonArray {
                    // TODO: Build this path. https://spec.graphql.org/June2018/#example-90475
                })
                extensions?.let {
                    put("extensions", it.toJsonElement())
                }
            }
        })
    }.toString()
}

class ExecutionException(message: String, node: ASTNode? = null, cause: Throwable? = null) :
    GraphQLError(
        message,
        nodes = node?.let(::listOf),
        originalError = cause,
        extensionsErrorType = BuiltInErrorCodes.INTERNAL_SERVER_ERROR.name
    ) {
        constructor(message: String, node: Execution, cause: Throwable? = null) : this(message, node.selectionNode, cause)
    }

class InvalidInputValueException(message: String, node: ASTNode?, originalError: Throwable? = null) :
    GraphQLError(
        message,
        nodes = node?.let(::listOf),
        originalError = originalError,
        extensionsErrorType = BuiltInErrorCodes.BAD_USER_INPUT.name
    )

class InvalidSyntaxException(message: String, source: Source, positions: List<Int>) :
    GraphQLError(
        message,
        source = source,
        positions = positions,
        extensionsErrorType = BuiltInErrorCodes.GRAPHQL_PARSE_FAILED.name
    )

class ValidationException(message: String, nodes: List<ASTNode>? = null) :
    GraphQLError(message, nodes = nodes, extensionsErrorType = BuiltInErrorCodes.GRAPHQL_VALIDATION_FAILED.name) {
    constructor(message: String, node: ASTNode?) : this(message, node?.let(::listOf))
}
