package de.stuebingerb.kgraphql.stitched

import com.fasterxml.jackson.databind.JsonNode
import de.stuebingerb.kgraphql.ExperimentalAPI

@ExperimentalAPI
data class StitchedGraphqlRequest(
    val operationName: String? = null,
    val variables: JsonNode? = null,
    val query: String
)
