# KGraphQL

[![Maven Central](https://img.shields.io/maven-central/v/de.stuebingerb/kgraphql.svg?label=Maven%20Central&logo=apachemaven)](https://search.maven.org/search?q=g:%22de.stuebingerb%22%20AND%20a:%22kgraphql%22)
[![codecov](https://codecov.io/gh/stuebingerb/KGraphQL/graph/badge.svg)](https://codecov.io/gh/stuebingerb/KGraphQL)
[![Slack](https://img.shields.io/badge/%23KGraphQL%20-%20%23KGraphQL?logo=slack&label=Slack)](https://kotlinlang.slack.com/messages/kgraphql)
[![Awesome Kotlin](https://img.shields.io/badge/Kotlin%20-%20Kotlin?logo=kotlin&label=Awesome)](https://github.com/KotlinBy/awesome-kotlin)

KGraphQL is a pure [Kotlin](https://kotlinlang.org/) implementation of a code-first [GraphQL](http://graphql.org/) server
with focus on a rich and easy-to-use DSL that leverages existing code to set up the schema.

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
            property("fullText") {
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

    // {"data":{"article":{"id":5,"fullText":"5: Hello World"}}}
}
```

KGraphQL has built-in support for [Ktor](https://ktor.io/) but works with any underlying server.
As of now, it has been used in production at Europe's leading consumer electronics retailer since 2019,
stitching together multiple backend services with about 100 types combined.

<details>
  <summary>Maven Dependency</summary>

  ```xml
<dependency>
    <groupId>de.stuebingerb</groupId>
    <artifactId>kgraphql</artifactId>
    <version>${kgraphql.version}</version>
</dependency>
  ```
</details>

<details>
  <summary>Gradle Dependency</summary>

  ```kts
repositories {
    mavenCentral()
}

dependencies {
    implementation("de.stuebingerb:kgraphql:$kgraphql_version")
}
  ```
</details>

## History

KGraphQL was initially created by [Paweł Gutkowski](https://github.com/pgutkowski) and then continued by [Jógvan Olsen](https://github.com/jeggy).
Huge thanks to both of them for starting this amazing library!

## Documentation

See the [documentation](https://stuebingerb.github.io/KGraphQL/) for a more detailed explanation of the library.

## Contributing

See [Contributing](CONTRIBUTING.md).

## Examples

Working examples are located in the [examples](examples) folder. Every example is its own project, separated from the
library build. To build and/or run it, move into the folder of the example (e.g. [ktor](examples/ktor))
and execute Gradle tasks from there.

## Versioning

The versioning from 1.0.0 on will follow [Semantic Versioning](http://semver.org/). Until then, expect breaking changes as required,
although the general vision is to [suck less](https://www.codesimplicity.com/post/suck-less/) with each release.

## Spec Support

KGraphQL aims to provide full support for the [September 2025 Edition](https://spec.graphql.org/September2025/) of the
[GraphQL specification](https://spec.graphql.org/) with some additions for [schema stitching](https://www.apollographql.com/blog/graphql-schema-stitching).

See [issues](https://github.com/stuebingerb/KGraphQL/issues) for where support is currently lacking.

## License

KGraphQL is Open Source software released under the [MIT license](https://opensource.org/licenses/MIT).
