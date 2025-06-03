package com.apurebase.kgraphql.stitched

import com.apurebase.kgraphql.BuiltInErrorCodes
import com.apurebase.kgraphql.ExperimentalAPI
import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.schema.execution.Execution

// TODO: support multiple remote errors
@ExperimentalAPI
class RemoteExecutionException(message: String, node: Execution.Remote) : GraphQLError(
    message = message,
    nodes = listOf(node.selectionNode),
    extensions = mapOf(
        "type" to BuiltInErrorCodes.INTERNAL_SERVER_ERROR.name,
        "detail" to mapOf("remoteUrl" to node.remoteUrl, "remoteOperation" to node.remoteOperation)
    )
)

