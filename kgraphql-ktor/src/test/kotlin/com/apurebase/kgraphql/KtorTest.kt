package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.testing.testApplication

open class KtorTest {

    fun withServer(
        ctxBuilder: ContextBuilder.(ApplicationCall) -> Unit = {},
        authHeader: String? = null,
        block: SchemaBuilder.() -> Unit
    ): (String, Kraph.() -> Unit) -> HttpResponse {
        return { type, kraph ->
            var response: HttpResponse? = null
            testApplication {
                install(Authentication) {
                    basic {
                        realm = "ktor"
                        validate {
                            KtorFeatureTest.User(4, it.name)
                        }
                    }
                }
                install(GraphQL) {
                    context(ctxBuilder)
                    wrap { next ->
                        authenticate(optional = authHeader == null) { next() }
                    }
                    schema(block)
                }

                response = client.post {
                    url("graphql")
                    authHeader?.let {
                        header(HttpHeaders.Authorization, authHeader)
                    }
                    header(HttpHeaders.ContentType, "application/json;charset=UTF-8")
                    setBody(
                        when (type.lowercase().trim()) {
                            "query" -> graphqlQuery(kraph).build()
                            "mutation" -> graphqlMutation(kraph).build()
                            else -> error("$type is not a valid graphql operation type")
                        }.also(::println)
                    )
                }
            }
            checkNotNull(response)
        }
    }
}
