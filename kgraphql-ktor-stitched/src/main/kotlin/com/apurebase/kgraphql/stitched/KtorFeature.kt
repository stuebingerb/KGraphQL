package com.apurebase.kgraphql.stitched

import com.apurebase.kgraphql.ContextBuilder
import com.apurebase.kgraphql.ExperimentalAPI
import com.apurebase.kgraphql.GraphQL
import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.GraphqlRequest
import com.apurebase.kgraphql.context
import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.stitched.schema.dsl.StitchedSchemaBuilder
import com.apurebase.kgraphql.stitched.schema.dsl.StitchedSchemaConfigurationDSL
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Plugin
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.AttributeKey
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json.Default.decodeFromString

@ExperimentalAPI
class StitchedGraphQL(val schema: Schema) {
    class Configuration : StitchedSchemaConfigurationDSL() {
        fun stitchedSchema(block: StitchedSchemaBuilder.() -> Unit) {
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
        internal var schemaBlock: (StitchedSchemaBuilder.() -> Unit)? = null
    }

    companion object Feature : Plugin<Application, Configuration, GraphQL> {
        override val key = AttributeKey<GraphQL>("StitchedKGraphQL")

        private val rootFeature = FeatureInstance("StitchedKGraphQL")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): GraphQL {
            return rootFeature.install(pipeline, configure)
        }
    }

    class FeatureInstance(featureKey: String = "StitchedKGraphQL") : Plugin<Application, Configuration, GraphQL> {
        companion object {
            private val playgroundHtml: ByteArray? by lazy {
                this::class.java.classLoader.getResource("playground.html")?.readBytes()
            }
        }

        override val key = AttributeKey<GraphQL>(featureKey)

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): GraphQL {
            val config = Configuration().apply(configure)
            val schema = StitchedKGraphQL.stitchedSchema {
                configuration = config
                config.schemaBlock?.invoke(this)
            }

            val routing: Routing.() -> Unit = {
                val routing: Route.() -> Unit = {
                    route(config.endpoint) {
                        post {
                            val bodyAsText = call.receiveText()
                            val request = decodeFromString(GraphqlRequest.serializer(), bodyAsText)
                            val ctx = context {
                                config.contextSetup?.invoke(this, call)
                            }
                            val result = schema.execute(
                                request = request.query,
                                variables = request.variables.toString(),
                                context = ctx,
                                operationName = request.operationName
                            )
                            call.respondText(result, contentType = ContentType.Application.Json)
                        }
                        get {
                            val schemaRequested = call.request.queryParameters["schema"] != null
                            if (schemaRequested && config.introspection) {
                                call.respondText(schema.printSchema())
                            } else if (config.playground) {
                                playgroundHtml?.let {
                                    call.respondBytes(it, contentType = ContentType.Text.Html)
                                }
                            }
                        }
                    }
                }

                config.wrapWith?.invoke(this, routing) ?: routing(this)
            }

            pipeline.pluginOrNull(RoutingRoot)?.apply(routing) ?: pipeline.install(RoutingRoot, routing)

            pipeline.intercept(ApplicationCallPipeline.Monitoring) {
                try {
                    coroutineScope {
                        proceed()
                    }
                } catch (e: GraphQLError) {
                    context.respondText(e.serialize(), ContentType.Application.Json, HttpStatusCode.OK)
                }
            }
            return GraphQL(schema)
        }
    }
}
