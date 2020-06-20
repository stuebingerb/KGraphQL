package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.apurebase.kgraphql.schema.dsl.SchemaConfigurationDSL
import io.ktor.application.*
import io.ktor.http.ContentType
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.routing.*
import io.ktor.util.AttributeKey
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json.Default.parse

class GraphQL {


    class Configuration: SchemaConfigurationDSL() {
        fun schema(block: SchemaBuilder.() -> Unit) {
            schemaBlock = block
        }

        /**
         * This adds support for opening the graphql route within the browser
         */
        var playground: Boolean = false

        var endpoint: String = "/graphql"

        fun context(block: ContextBuilder.(ApplicationCall) -> Unit) {
            contextSetup = block
        }

        fun wrap(block: Route.(next: Route.() -> Unit) -> Unit) {
            wrapWith = block
        }

        internal var contextSetup: (ContextBuilder.(ApplicationCall) -> Unit)? = null
        internal var wrapWith: (Route.(next: Route.() -> Unit) -> Unit)? = null
        internal lateinit var schemaBlock: SchemaBuilder.() -> Unit

    }


    companion object Feature: ApplicationFeature<Application, Configuration, GraphQL> {
        override val key = AttributeKey<GraphQL>("KGraphQL")

        @UnstableDefault
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): GraphQL {
            val config = Configuration().apply(configure)
            val schema = KGraphQL.schema {
                configure(config)
                config.schemaBlock(this)
            }

            val routing: Routing.() -> Unit = {
                val routing: Route.() -> Unit = {
                    route(config.endpoint) {
                        post {
                            val request = parse(GraphqlRequest.serializer(), call.receiveText())
                            val ctx = context {
                                config.contextSetup?.invoke(this, call)
                            }
                            val result = schema.execute(request.query, request.variables.toString(), ctx)
                            call.respond(result)
                        }
                        if (config.playground) get {
                            @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                            val playgroundHtml = KtorGraphQLConfiguration::class.java.classLoader.getResource("playground.html").readBytes()
                            call.respondBytes(playgroundHtml, contentType = ContentType.Text.Html)
                        }
                    }
                }

                config.wrapWith?.invoke(this, routing) ?: routing(this)
            }

            pipeline.featureOrNull(Routing)?.apply(routing) ?: pipeline.install(Routing, routing)

            return GraphQL()
        }
    }
}
