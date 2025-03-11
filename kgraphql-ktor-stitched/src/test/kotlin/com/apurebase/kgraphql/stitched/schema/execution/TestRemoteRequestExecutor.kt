package com.apurebase.kgraphql.stitched.schema.execution

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.stitched.StitchedGraphqlRequest
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

class TestRemoteRequestExecutor(private val client: HttpClient, val objectMapper: ObjectMapper) :
    AbstractRemoteRequestExecutor(objectMapper) {
    override suspend fun executeRequest(url: String, request: StitchedGraphqlRequest, ctx: Context): String =
        client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(request))
        }.bodyAsText()
}
