# Schema Stitching

*This feature is still in experimental state.*

## Overview

Schema stitching is a method to take multiple GraphQL schemas and combine them into a single, unified schema. This can
be useful when implementing an integration layer for a frontend that orchestrates multiple backend APIs to provide your
UI with all the required data in a single request, without having to think about CORS.

By linking properties to remote queries, one can also enhance individual schemas by e.g. automatically resolving
identifiers. 

In KGraphQL, schema stitching is configured via the `stitchedSchema` DSL. Each stitched schema has 1-n *remote* schemas,
and up to one *local* schema.

*Example*:

```kotlin
stitchedSchema {
    configure {
        remoteExecutor = TestRemoteRequestExecutor(client, objectMapper)
    }
    localSchema {
        query("local") {
            resolver { -> "local" }
        }
    }
    remoteSchema("remote1") {
        ...
    }
    remoteSchema("remote2") {
        ...
    }
}
```

### Remote Schema Fetching

Remote schemas are usually fetched via introspection query.

*Example*:

```kotlin
remoteSchema(url) {
    runBlocking {
        val responseText = httpClient.post(url) {
            setBody(
                TextContent(
                    text = graphQLJson.toJson(mapOf("query" to Introspection.query())),
                    contentType = ContentType.Application.Json
                )
            )
        }.bodyAsText()
        IntrospectedSchema.fromIntrospectionResponse(responseText)
    }
}
```

### Duplicate Types

Currently, if multiple schemas define types with the same name, the local type wins. For identical types from remote
schemas there is no guaranteed order of precedence. Future versions may provide better tools to deal with such
situations.

### Remote Execution

To execute remote queries, consumers need to provide a `RemoteRequestExecutor` that receives an `Execution.Remote` node
and the current `Context`, and has to return the result as `JsonNode?`:

```kotlin
interface RemoteRequestExecutor {
    // ParallelRequestExecutor expects a JsonNode as result of any execution
    suspend fun execute(node: Execution.Remote, ctx: Context): JsonNode?
}
```

To simplify implementation, consumers can extend the `AbstractRemoteRequestExecutor` and only provide the implementation
for actually executing the HTTP request itself.

### Fragments

Fragments based on remote types work but cannot use Kotlin's type system to determine the correct condition type.
Therefore, queries including fragments must also request the `__typename`. Future implementation might automatically
include this.

*Example*:

```graphql
query {
    getRemote {
        __typename
        foo
        child {
            __typename
            ...on RemoteA { specialA }
            ...on RemoteB { specialB }
        }
    }
}
```

### Local "Remote" Execution

Due to current implementation details, properties stitched to a *local* query will also be handled by the
`RemoteRequestExecutor`, and therefore the schema has to provide a `localUrl`. Future implementation will likely support
actual local execution.

*Example:*

```kotlin
stitchedSchema {
    configure {
        localUrl = "/graphql"
    }
}
```

### Linking Properties

All (local and remote) types of a schema can be extended via stitched properties that are translated into remote query
calls during execution. The following example adds two fields to the `Type1` type:

- `stitched1`, which executes the remote query `getStitched1` and is marked as required
- `stitched2`, which executes the remote query `getStitched2` and provides the value of the property `foo` from the
  parent type as argument named `fooValue`

*Example:*

```kotlin
type("Type1") {
    stitchedProperty("stitched1") {
        nullable = false
        remoteQuery("getStitched1")
    }
    stitchedProperty("stitched2") {
        remoteQuery("getStitched2").withArgs {
            arg { name = "fooValue"; parentFieldName = "foo" }
        }
    }
}
```

Stitched properties are nullable by default, and if a parent property is `null`, the remote execution is skipped and
results in a value of `null` for the stitched property itself.

See `StitchedSchemaExecutionTest.kt` for an extensive list of different examples.
