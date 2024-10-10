package com.apurebase.kgraphql

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class KtorMultipleEndpoints : KtorTest() {

    @Test
    fun `basic multiple endpoints test`() = testApplication {
        install(GraphQL.FeatureInstance("KGraphql - Open")) {
            endpoint = "/open"
            schema {
                query("check") {
                    resolver { -> "Open" }
                }
            }
        }
        install(GraphQL.FeatureInstance("KGraphql - Closed")) {
            endpoint = "closed"
            schema {
                query("check") {
                    resolver { -> "Closed" }
                }
            }
        }

        val query = graphqlQuery {
            field("check")
        }.build()

        client.post("/open") {
            header(HttpHeaders.ContentType, "application/json;charset=UTF-8")
            setBody(query)
        }.bodyAsText() shouldBeEqualTo "{\"data\":{\"check\":\"Open\"}}"

        client.post("/closed") {
            header(HttpHeaders.ContentType, "application/json;charset=UTF-8")
            setBody(query)
        }.bodyAsText() shouldBeEqualTo "{\"data\":{\"check\":\"Closed\"}}"
    }
}
