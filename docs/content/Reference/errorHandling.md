# Error Handling

Error handling is currently implemented in a basic way only, and does for example not support multiple errors or partial responses.

## Basics

When an exception occurs, request execution is aborted and results in an error response depending on the type of exception.

Exceptions that extend `GraphQLError` will be mapped to a response that contains an `errors` key:

=== "Example"
    ```json
    {
        "errors": [
            {
                "message": "Property nonexisting on MyType does not exist",
                "locations": [
                    {
                        "line": 2,
                        "column": 13
                    }
                ],
                "path": [],
                "extensions": {
                    "type": "GRAPHQL_VALIDATION_FAILED"
                }
            }
        ]
    }
    ```

All built-in exceptions extend `GraphQLError`.

## Exceptions From Resolvers

What happens with exceptions from resolvers depends on the [schema configuration](configuration.md).

### wrapErrors = true

With `wrapErrors = true` (which is the default), exceptions are wrapped as `ExecutionException`, which is a `GraphQLError`:

=== "Example"
    ```kotlin
    KGraphQL.schema {
        configure {
            wrapErrors = true
        }
        query("throwError") {
            resolver<String> {
                throw IllegalArgumentException("Illegal argument")
            }
        }
    }
    ```

=== "Response (HTTP 200)"
    ```json
    {
        "errors": [
            {
                "message": "Illegal argument",
                "locations": [
                    {
                        "line": 2,
                        "column": 1
                    }
                ],
                "path": [],
                "extensions": {
                    "type": "INTERNAL_SERVER_ERROR"
                }
            }
        ]
    }
    ```

### wrapErrors = false

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
