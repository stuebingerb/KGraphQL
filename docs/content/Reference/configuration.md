KGraphQL schema allows configuration of the following properties:

| Property                       | Description                                                                                                           | Default value                                                                                                                                                            |
|--------------------------------|-----------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| useDefaultPrettyPrinter        | Schema pretty prints JSON responses                                                                                   | `false`                                                                                                                                                                  |
| useCachingDocumentParser       | Schema caches parsed query documents                                                                                  | `true`                                                                                                                                                                   |
| documentParserCacheMaximumSize | Schema document cache maximum size                                                                                    | `1000`                                                                                                                                                                   |
| objectMapper                   | Schema is using Jackson ObjectMapper from this property                                                               | result of `jacksonObjectMapper()` from [jackson-kotlin-module](https://github.com/FasterXML/jackson-module-kotlin)                                                       |
| acceptSingleValueAsArray       | Schema accepts single argument values as singleton list                                                               | `true`                                                                                                                                                                   |
| coroutineDispatcher            | Schema is using CoroutineDispatcher from this property                                                                | [CommonPool](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/src/main/kotlin/kotlinx/coroutines/experimental/CommonPool.kt)             |
| genericTypeResolver            | Schema is using generic type resolver from this property                                                              | [GenericTypeResolver.DEFAULT](https://github.com/aPureBase/KGraphQL/blob/master/kgraphql/src/main/kotlin/com/apurebase/kgraphql/schema/execution/GenericTypeResolver.kt) |
| wrapErrors                     |                                                                                                                       | `true`                                                                                                                                                                   |
| introspection                  | Schema allows introspection (also affects SDL). Introspection can be disabled in production to reduce attack surface. | `true`                                                                                                                                                                   |

=== "Example"
    ```kotlin
    KGraphQL.schema {
        configure {
            useDefaultPrettyPrinter = true
            objectMapper = jacksonObjectMapper()
            useCachingDocumentParser = false
        }
    }
    ```
