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

/**
 * The base class for all GraphQL errors, will either be a [RequestError] or an [ExecutionError].
 */
sealed class GraphQLError(
    /**
     * A message describing the error intended for the developer as a guide to understand
     * and correct the error.
     */
    override val message: String,

    /**
     * GraphQL AST Node corresponding to this error.
     */
    val node: ASTNode?,

    /**
     * The source GraphQL document for the first location of this error.
     *
     * Note that if this error represents more than one node, the source may not
     * represent nodes after the first node.
     */
    val source: Source?,

    /**
     * An array of character offsets within the source GraphQL document
     * which correspond to this error.
     */
    val positions: List<Int>?,

    /**
     * The original error thrown from a field resolver during execution.
     */
    val originalError: Throwable?,

    /**
     * Custom error extensions.
     */
    open val extensions: Map<String, Any?>?
) : Exception(message, originalError) {

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
            positions.map { position -> getLocation(source, position) }
        } else {
            node?.loc?.let { listOf(getLocation(it.source, it.start)) }
        }
    }

    fun prettyPrint(): String {
        var output = message

        if (node?.loc != null) {
            output += "\n\n" + node.loc!!.printLocation()
        } else if (source != null && locations != null) {
            for (location in locations!!) {
                output += "\n\n" + source.print(location)
            }
        }

        return output
    }

    open fun serialize(): String = buildJsonObject {
        put("errors", buildJsonArray {
            addJsonObject {
                put("message", message)
                locations?.let {
                    put("locations", buildJsonArray {
                        it.forEach { location ->
                            addJsonObject {
                                put("line", location.line)
                                put("column", location.column)
                            }
                        }
                    })
                }
                extensions?.let {
                    put("extensions", it.toJsonElement())
                }
            }
        })
    }.toString()
}

/**
 * An execution error is an error raised during the execution of a particular field which results in partial response
 * data. This may occur due to failure to coerce the arguments for the field, an internal error during value resolution,
 * or failure to coerce the resulting value.
 *
 * The result of field execution will be `null` and, if the field is declared as `Non-Null`, bubble up to the next
 * nullable parent. Partial responses will always contain a "data" entry.
 *
 * An execution error is typically the fault of a GraphQL service.
 *
 * cf. https://spec.graphql.org/September2025/#execution-error
 */
open class ExecutionError(
    message: String,
    node: Execution,
    originalError: Throwable? = null,
    extensions: Map<String, Any?>? = mapOf("type" to BuiltInErrorCodes.INTERNAL_SERVER_ERROR.name)
) : GraphQLError(
    message = message,
    node = node.selectionNode,
    source = null,
    positions = null,
    originalError = originalError,
    extensions = extensions
)

/**
 * A request error is an error raised during a request which results in no response data. Typically raised before
 * execution begins, a request error may occur due to a parse grammar or validation error, an inability to determine
 * which operation to execute, or invalid input values for variables.
 *
 * A request error is typically the fault of the requesting client.
 *
 * If a request error is raised, request execution will be halted, and the response will not contain a "data" entry.
 *
 * cf. https://spec.graphql.org/September2025/#request-error
 */
open class RequestError(
    message: String,
    source: Source? = null,
    positions: List<Int>? = null,
    node: ASTNode? = null,
    originalError: Throwable? = null,
    extensions: Map<String, Any?>? = mapOf("type" to BuiltInErrorCodes.BAD_USER_INPUT.name)
) : GraphQLError(
    message = message,
    node = node,
    source = source,
    positions = positions,
    originalError = originalError,
    extensions = extensions
)

class ExecutionException(message: String, node: Execution, cause: Throwable? = null) : ExecutionError(
    message = message,
    node = node,
    originalError = cause,
    extensions = mapOf("type" to BuiltInErrorCodes.INTERNAL_SERVER_ERROR.name)
)

class InvalidInputValueException(message: String, node: ASTNode?, originalError: Throwable? = null) : RequestError(
    message = message,
    node = node,
    originalError = originalError,
    extensions = mapOf("type" to BuiltInErrorCodes.BAD_USER_INPUT.name)
)

class InvalidSyntaxException(message: String, source: Source, positions: List<Int>) : RequestError(
    message = message,
    source = source,
    positions = positions,
    extensions = mapOf("type" to BuiltInErrorCodes.GRAPHQL_PARSE_FAILED.name)
)

class ValidationException(message: String, node: ASTNode? = null) : RequestError(
    message = message,
    node = node,
    extensions = mapOf("type" to BuiltInErrorCodes.GRAPHQL_VALIDATION_FAILED.name)
)
