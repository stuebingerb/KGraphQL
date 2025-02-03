package com.apurebase.kgraphql.schema.stitched

import com.apurebase.kgraphql.BuiltInErrorCodes
import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.schema.execution.Execution

// TODO: support multiple remote errors
class RemoteExecutionException(message: String, node: Execution.Remote) : GraphQLError(
    message,
    nodes = listOf(node.selectionNode),
    extensionsErrorType = BuiltInErrorCodes.INTERNAL_SERVER_ERROR.name,
    extensionsErrorDetail = mapOf("remoteSchema" to node.remoteUrl, "remoteOperation" to node.remoteOperation)
)

