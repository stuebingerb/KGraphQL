# KGraphQL

[![Maven Central](https://img.shields.io/maven-central/v/de.stuebingerb/kgraphql.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22de.stuebingerb%22%20AND%20a:%22kgraphql%22)

KGraphQL is a [Kotlin](https://kotlinlang.org/) implementation of [GraphQL](http://graphql.org/). It provides a rich DSL
to set up the GraphQL schema.

```kotlin
data class Article(val id: Int, val text: String)

fun main() {
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

All contributions are welcome. Feel free to open issues and PRs!

## Building

To build KGraphQL you only need to have JDK8 installed. invoke

```bash
./gradlew build
```

To perform local build.

## Versioning

The versioning is following [Semantic Versioning](http://semver.org/)

## Links

Specification : http://facebook.github.io/graphql/

## License

KGraphQL is Open Source software released under the [MIT license](https://opensource.org/licenses/MIT)
