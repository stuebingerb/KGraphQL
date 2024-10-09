package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.http.*
import io.ktor.server.testing.*


open class KtorTest {

    fun withServer(
        ctxBuilder: ContextBuilder.(ApplicationCall) -> Unit = {},
        block: SchemaBuilder.() -> Unit
    ): (String, Kraph.() -> Unit) -> String {
        return { type, kraph ->
            var str = ""
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
                        authenticate(optional = true) { next() }
                    }
                    schema(block)
                }

                str = client.post {
                    url("graphql")
                    header(HttpHeaders.ContentType, "application/json;charset=UTF-8")
                    setBody(
                        when (type.lowercase().trim()) {
                            "query" -> graphqlQuery(kraph).build()
                            "mutation" -> graphqlMutation(kraph).build()
                            else -> error("$type is not a valid graphql operation type")
                        }.also(::println)
                    )
                }.bodyAsText()
            }
            str
        }
    }
}
