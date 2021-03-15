package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.util.*
import me.lazmaid.kraph.Kraph

open class KtorTest {

    fun withServer(ctxBuilder: ContextBuilder.(ApplicationCall) -> Unit = {}, block: SchemaBuilder.() -> Unit): (Kraph.() -> Unit) -> String {
        return {
            withTestApplication({
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
            }) {
                handleRequest {
                    uri = "graphql"
                    method = HttpMethod.Post
                    addHeader(HttpHeaders.ContentType, "application/json;charset=UTF-8")
                    setBody(Kraph { it(this) }.toRequestString())
                }.response.content!!
            }
        }
    }
}
