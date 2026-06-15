package de.stuebingerb.kgraphql.schema.execution

import de.stuebingerb.kgraphql.Context
import de.stuebingerb.kgraphql.ExecutionError
import de.stuebingerb.kgraphql.GraphQLError

/**
 * Error handler used to transform [Throwable]s encountered during execution into [GraphQLError]s included in
 * the response. Default implementation will leave existing GraphQL errors as-is, and wrap everything else as
 * [ExecutionError].
 *
 * Intended to be subclassed for custom error handling while preserving access to default mapping.
 *
 * Note that exceptions caused during [handleException] are *not* wrapped, and will abort execution.
 */
open class ErrorHandler {
    open suspend fun handleException(ctx: Context, node: Execution.Node, exception: Throwable): GraphQLError =
        when (exception) {
            is GraphQLError -> exception
            else -> ExecutionError(
                exception.message ?: exception::class.simpleName ?: "Error during execution",
                node,
                exception
            )
        }
}
