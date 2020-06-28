package com.apurebase.kgraphql

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject


@Serializable
data class GraphqlRequest(
    val operationName: String?,
    val variables: JsonObject?,
    val query: String
)
