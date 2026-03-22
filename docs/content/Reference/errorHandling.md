# Error Handling

When `wrapErrors` is `true` (which is the default), exceptions encountered during execution of a request will be wrapped, and are returned as part of a well-formed response.

## Error Format

Every error has an entry with the key `message` that contains a human-readable description of the problem.

If an error can be associated to a point in the GraphQL document, it will contain an entry with the key `locations` that lists all lines and columns this error is referring to.

If an error can be associated to a particular field, it will contain an entry with the key `path`, containing all segments up to the field where it occurred. This helps clients to distinguish genuine `null` responses from missing values due to errors.

Additionally, every error can contain an entry with the key `extensions` that is a map of server-specific additions outside of the schema. All built-in errors will be mapped with a `type` extension according to their class.

Depending on the type of error, the response may also contain a `data` key and partial response data.

## Request Errors

_Request errors_ are errors that result in no response data. They are typically raised before execution begins, and may be caused by parsing errors in the request document, invalid syntax or invalid input values for variables.

Request errors are typically the fault of the requesting client.

If a request error is raised, the response will only contain an `errors` key with corresponding details.

=== "Example"
    ```json
    {
      "errors": [
        {
          "message": "Missing selection set on property 'film' of type 'Film'",
          "extensions": {
            "type": "GRAPHQL_VALIDATION_FAILED"
          }
        }
      ]
    }
    ```

## Execution Errors

_Execution errors_ (previously called _field errors_ in the spec) are errors raised during execution of a particular field, and result in partial response data. They may be caused by coercion failures or internal errors during function invocation.

Execution errors are typically the fault of the GraphQL server.

When an execution error occurs, it is added to the list of errors in the response, and the value of its field is coerced to `null`. If that is a valid value for the field, execution continues with the next sibling. Otherwise, when the field is a non-null type, the error is propagated to the parent field, until a nullable type or the root type is reached.

Execution errors can lead to partial responses, where some fields can still return proper data. To make partial responses more easily identifiable, the `errors` key will be serialized as first entry in the response JSON. 

Given the [Star Wars schema](../Tutorials/starwars.md), if fetching one of the friends' names fails in the following operation, the response might contain a friend without a name:

=== "SDL"
    ```graphql
    type Hero {
      friends: [Hero]!
      id: ID!
      name: String
    }

    type Query {
      hero: Hero!
    }
    ```
=== "Request"
    ```graphql
    {
      hero {
        name
        heroFriends: friends {
          id
          name
        }
      }
    }
    ```
=== "Response"
    ```json
    {
      "errors": [
        {
          "message": "Name for character with ID 1002 could not be fetched.",
          "locations": [{ "line": 6, "column": 7 }],
          "path": ["hero", "heroFriends", 1, "name"]
        }
      ],
      "data": {
        "hero": {
          "name": "R2-D2",
          "heroFriends": [
            {
              "id": "1000",
              "name": "Luke Skywalker"
            },
            {
              "id": "1002",
              "name": null
            },
            {
              "id": "1003",
              "name": "Leia Organa"
            }
          ]
        }
      }
    }
    ```

If the field `name` was declared as non-null, the whole list entry would be missing instead. However, the error itself would still be the same:

=== "SDL"
    ```graphql
    type Hero {
      friends: [Hero]!
      id: ID!
      name: String!
    }

    type Query {
      hero: Hero!
    }
    ```
=== "Request"
    ```graphql
    {
      hero {
        name
        heroFriends: friends {
          id
          name
        }
      }
    }
    ```
=== "Response"
    ```json
    {
      "errors": [
        {
          "message": "Name for character with ID 1002 could not be fetched.",
          "locations": [{ "line": 6, "column": 7 }],
          "path": ["hero", "heroFriends", 1, "name"]
        }
      ],
      "data": {
        "hero": {
          "name": "R2-D2",
          "heroFriends": [
            {
              "id": "1000",
              "name": "Luke Skywalker"
            },
            null,
            {
              "id": "1003",
              "name": "Leia Organa"
            }
          ]
        }
      }
    }
    ```

## Raising Errors From Resolvers

In addition to returning (partial) data, resolvers can also add execution errors to the response via `Context.raiseError`:

=== "Example"
    ```kotlin
    query("items") {
        resolver { node: Execution.Node, ctx: Context ->
            ctx.raiseError(MissingItemError("Cannot get item 'missing'", node))
            listOf(Item("Existing 1"), Item("Existing 2"))
        }
    }
    ```

## wrapErrors = false

With `wrapErrors = false`, exceptions are re-thrown:

=== "Example"
    ```kotlin
    KGraphQL.schema {
        configure {
            wrapErrors = false
        }
        query("throwError") {
            resolver<String> {
                throw IllegalArgumentException("Illegal argument")
            }
        }
    }
    ```
=== "Response (HTTP 500)"
    ```html
    <html>
    <body>
        <h1>Internal Server Error</h1>
        <h2>Request Information:</h2>
        <pre>Method: POST</pre>
        <h2>Stack Trace:</h2>
        <pre>...</pre>
    </body>
    </html>
    ```

Those re-thrown exceptions could then be handled with the [`StatusPages` Ktor plugin](https://ktor.io/docs/server-status-pages.html):

=== "Example"
    ```kotlin
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respondText(
                text = "Invalid input: $cause",
                status = HttpStatusCode.BadRequest
            )
        }
    }
    ```
=== "Response (HTTP 400)"
    ```text
    Invalid input: java.lang.IllegalArgumentException: Illegal argument
    ```

Because exceptions are re-thrown, `wrapErrors = false` can never result in partial responses. `wrapErrors = false` will
also not invoke a custom error handler. If you want to throw exceptions with custom mapping, use `wrapErrors = true` and
re-throw mapped exceptions from the error handler. 

## Error Handler

In KGraphQL, the schema can [configure a custom _error handler_](configuration.md) that is called for each exception
encountered during execution. It can be used to customize default error mapping, and to add additional extensions to
the response.

The error handler is supposed to return a subclass of `GraphQLError`, which is either a `RequestError` or an `ExecutionError`
that will be handled according to the schema. When subclassing from the default `ErrorHandler`, mapping can be delegated
to the standard implementation, completely replaced, or a mixture of both.

=== "Example"
    ```kotlin
    val customErrorHandler = object : ErrorHandler() {
        override suspend fun handleException(ctx: Context, node: Execution.Node, exception: Throwable): GraphQLError {
            return when (exception) {
                is IllegalArgumentException -> ExecutionError(
                    message = exception.message ?: "",
                    node = node,
                    extensions = mapOf("type" to "CUSTOM_ERROR_TYPE")
                )

                is IllegalAccessException -> RequestError(
                    message = "You shall not pass!",
                    node = node.selectionNode,
                    extensions = mapOf("required_role" to "ADMIN", "reason" to "Gandalf")
                )

                else -> super.handleException(ctx, node, exception)
            }
        }
    }
    ```

(!) Exceptions from the error handler itself are *not* wrapped, regardless of the `wrapErrors` configuration. 
