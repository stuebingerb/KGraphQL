package com.apurebase.kgraphql.stitched

import com.fasterxml.jackson.databind.JsonNode

data class StitchedGraphqlRequest(
    val operationName: String? = null,
    val variables: JsonNode? = null,
    val query: String
)
