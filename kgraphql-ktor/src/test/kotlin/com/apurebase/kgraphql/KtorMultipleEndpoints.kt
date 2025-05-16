package com.apurebase.kgraphql

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
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
        }.bodyAsText() shouldBe "{\"data\":{\"check\":\"Open\"}}"

        client.post("/closed") {
            header(HttpHeaders.ContentType, "application/json;charset=UTF-8")
            setBody(query)
        }.bodyAsText() shouldBe "{\"data\":{\"check\":\"Closed\"}}"
    }

    @Test
    fun `playground should be disabled by default`() = testApplication {
        install(GraphQL) {
            schema {
                query("check") {
                    resolver { -> "OK" }
                }
            }
        }

        client.get("/graphql").status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `playground should work when enabled`() = testApplication {
        install(GraphQL) {
            playground = true
            schema {
                query("check") {
                    resolver { -> "OK" }
                }
            }
        }
        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val playgroundHtml =
            KtorGraphQLConfiguration::class.java.classLoader.getResource("playground.html")
                .readText()

        val response = client.get("/graphql")
        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe playgroundHtml
    }

    @Test
    fun `SDL should be provided by default`() = testApplication {
        install(GraphQL) {
            schema {
                query("check") {
                    resolver { -> "OK" }
                }
            }
        }

        val response = client.get("/graphql?schema")
        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe """
            type Query {
              check: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `SDL should not be provided when introspection is disabled`() = testApplication {
        install(GraphQL) {
            introspection = false
            schema {
                query("check") {
                    resolver { -> "OK" }
                }
            }
        }

        client.get("/graphql?schema").status shouldBe HttpStatusCode.NotFound
    }
}
