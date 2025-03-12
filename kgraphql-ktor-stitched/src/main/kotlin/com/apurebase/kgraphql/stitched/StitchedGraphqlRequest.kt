package com.apurebase.kgraphql.stitched

import com.apurebase.kgraphql.ExperimentalAPI
import com.fasterxml.jackson.databind.JsonNode

@ExperimentalAPI
data class StitchedGraphqlRequest(
    val operationName: String? = null,
    val variables: JsonNode? = null,
    val query: String
)
