# Ktor

If you are running a ktor server, there is a separate package that makes it easy to set up a fully functional GraphQL
server.

You first need to add the KGraphQL-ktor package to your dependency

=== "Kotlin Gradle Script"
    ```kotlin
    implementation("de.stuebingerb:kgraphql-ktor:$KGraphQLVersion")
    ```
=== "Gradle"
    ```groovy
    implementation 'de.stuebingerb:kgraphql-ktor:${KGraphQLVersion}'
    ```
=== "Maven"
    ```xml
    <dependency>
        <groupId>de.stuebingerb</groupId>
        <artifactId>kgraphql-ktor</artifactId>
        <version>${KGraphQLVersion}</version>
    </dependency>
    ```

## Initial setup

To set up KGraphQL you'll need to install the GraphQL feature like you would any
other [ktor feature](https://ktor.io/servers/features.html).

=== "Example"
    ```kotlin
    fun Application.module() {
      install(GraphQL) {
        playground = true 
        schema {
          query("hello") {
            resolver { -> "World!" }
          }
        }
      }
    }
    ```

Now you have a fully working GraphQL server. We have also set `playground = true`, so when running this you will be able
to open [http://localhost:8080/graphql](http://localhost:8080/graphql) _(your port number may vary)_ in your browser and
test it out directly within the browser.

## Configuration options

The GraphQL feature is extending the standard [KGraphQL configuration](../Reference/configuration.md) and providing its own
set of configuration as described in the table below.

| Property     | Description                                                                                                                                                                         | Default value  |
|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| endpoint     | This specifies what route will be delivering the GraphQL endpoint. When `playground` is enabled, it will use this endpoint also.                                                    | `/graphql`     |
| context      | Refer to example below                                                                                                                                                              |                |
| wrap         | If you want to wrap the route into something before KGraphQL will install the GraphQL route. You can use this wrapper. See example below for a more in depth on how to use it.      |                |
| errorHandler | Allows interaction with exceptions thrown during GraphQL execution and optional mapping to another one — in particular mapping to `GraphQLError` for serialization in the response. |                |
| schema       | This is where you are defining your schema. Please refer to [KGraphQL References](../Reference/operations.md) for further documentation on this.                                    | ***required*** |

### Wrap

Sometimes you would need to wrap your route within something. A good example of this would be the `authenticate`
function provided by [ktor Authentication feature](https://ktor.io/docs/server-auth.html).

=== "Example"
    ```kotlin
    wrap {
      authenticate(optional = true, build = it)
    }
    ```

This works great alongside the [context](#context) to provide a context to the KGraphQL resolvers.

### Context

To get access to the context

=== "Example"
    ```kotlin
    context { call ->
      // access to authentication is only available if this is wrapped inside a `authenticate` before hand. 
      call.authentication.principal<User>()?.let {
        +it
      }
    }
    schema {
      query("hello") {
        resolver { ctx: Context ->
          val user = ctx.get<User>()!!
          "Hello ${user.name}"
        }
      }  
    }
    ```

### Error Handler

By default, KGraphQL will wrap non-`GraphQLError` exceptions into an `ExecutionException` (when `wrapErrors = true`)
or rethrow them to be handled by Ktor (when `wrapErrors = false`).

The `errorHandler` provides a way to **intercept and transform exceptions** before they are serialized.  
It is always defined — by default it simply returns the same exception instance (`{ e -> e }`),  
but you can override it to map specific exception types to `GraphQLError` or other `Throwable` instances.

=== "Example"
    ```kotlin
    errorHandler { e ->
        when (e) {
            is ValidationException -> GraphQLError(e.message, extensions = mapOf("type" to "VALIDATION_ERROR"))
            is DomainException -> GraphQLError(e.message, extensions = mapOf("type" to "DOMAIN_ERROR"))
            is GraphQLError -> e
            else -> ExecutionException(e.message ?: "Unknown execution error", cause = e)
        }
    }
    schema {
        query("hello") {
            resolver { ctx: Context ->
                val user = ctx.get<User>()!!
                "Hello ${user.name}"
            }
        }
    }
    ```

## Schema Definition Language (SDL)

The [Schema Definition Language](https://graphql.org/learn/schema/#type-language) (or Type System Definition Language) is a human-readable, language-agnostic
representation of a GraphQL schema.

=== "Example"
    ```kotlin
    schema {
        data class SampleData(
            val id: Int,
            val stringData: String,
            val optionalList: List<String>?
        )
        
        query("getSampleData") {
            resolver { quantity: Int ->
                (1..quantity).map { SampleData(it, "sample-$it", emptyList()) }
            }.withArgs {
                arg<Int> { name = "quantity"; defaultValue = 10 }
            }
        }
    }
    ```
=== "SDL"
    ```graphql
    type Query {
        getSampleData(quantity: Int! = 10): [SampleData!]!
    }
    
    type SampleData {
        id: Int!
        optionalList: [String!]
        stringData: String!
    }
    ```

If schema introspection is enabled, the ktor feature will expose the current schema in Schema Definition
Language under [http://localhost:8080/graphql?schema](http://localhost:8080/graphql?schema).
