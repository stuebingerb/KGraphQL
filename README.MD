# KGraphQL

[![Maven Central](https://img.shields.io/maven-central/v/de.stuebingerb/kgraphql.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22de.stuebingerb%22%20AND%20a:%22kgraphql%22)
[![CodeQL](https://github.com/stuebingerb/KGraphQL/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/stuebingerb/KGraphQL/actions/workflows/github-code-scanning/codeql)

KGraphQL is a [Kotlin](https://kotlinlang.org/) implementation of [GraphQL](http://graphql.org/) based
on [aPureBase/KGraphQL](https://github.com/aPureBase/KGraphQL). It provides a rich DSL to set up the GraphQL schema.

```kotlin
data class Article(val id: Int, val text: String)

suspend fun main() {
    val schema = KGraphQL.schema {
        query("article") {
            resolver { id: Int?, text: String ->
                Article(id ?: -1, text)
            }
        }
        type<Article> {
            property<String>("fullText") {
                resolver { article: Article ->
                    "${article.id}: ${article.text}"
                }
            }
        }
    }

    schema.execute("""
        {
            article(id: 5, text: "Hello World") {
                id
                fullText
            }
        }
    """.trimIndent()).let(::println)
}
```

## Documentation

See the [documentation](https://kgraphql.io/) for a more detailed explanation of the library.

## Contributing

See [Contributing](CONTRIBUTING.md).

## Examples

Working examples are located in the [examples](examples) folder. Every example is its own project, separated from the
library build. To build and/or run it, move into the folder of the example (e.g. [ktor](examples/ktor))
and execute Gradle tasks from there.

## Versioning

The versioning from 1.0.0 on follows [Semantic Versioning](http://semver.org/).

## Links

GraphQL specification: https://spec.graphql.org/

## License

KGraphQL is Open Source software released under the [MIT license](https://opensource.org/licenses/MIT).
