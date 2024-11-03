package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.apurebase.kgraphql.schema.dsl.SchemaConfigurationDSL
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json.Default.decodeFromString

class Configuration : SchemaConfigurationDSL() {
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
    internal var schemaBlock: (SchemaBuilder.() -> Unit)? = null
}

internal fun createPlugin(name: String) = createApplicationPlugin(createConfiguration = ::Configuration, name = name) {
    val schema = KGraphQL.schema {
        configuration = pluginConfig
        pluginConfig.schemaBlock?.invoke(this)
    }
    application.routing {
        val routing: Route.() -> Unit = {
            post {
                val bodyAsText = call.receiveText()
                val request = decodeFromString(GraphqlRequest.serializer(), bodyAsText)
                val ctx = context {
                    this@createApplicationPlugin.pluginConfig.contextSetup?.invoke(this, call)
                }
                val result = schema.execute(
                    request = request.query,
                    variables = request.variables.toString(),
                    context = ctx,
                    operationName = request.operationName
                )
                call.respondText(result, contentType = ContentType.Application.Json)
            }
            if (this@createApplicationPlugin.pluginConfig.playground) {
                get {
                    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    val playgroundHtml =
                        KtorGraphQLConfiguration::class.java.classLoader.getResource("playground.html")
                            .readBytes()
                    call.respondBytes(playgroundHtml, contentType = ContentType.Text.Html)
                }
            }
        }
        val wrapped: Route.() -> Unit = {
            this@createApplicationPlugin.pluginConfig.wrapWith?.invoke(this, routing) ?: routing(this)
        }
        route(this@createApplicationPlugin.pluginConfig.endpoint, wrapped)
    }
    on(CallFailed) { call, cause ->
        if (cause is GraphQLError) {
            call.respondText(cause.serialize(), ContentType.Application.Json, HttpStatusCode.OK)
        } else {
            throw cause
        }
    }
}

val GraphQL = createPlugin("KGraphQL")
