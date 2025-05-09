# Fully functional GraphQL & Ktor server

We will be using the [ktor intellij plugin](https://plugins.jetbrains.com/plugin/10823-ktor) to get setup.

The very first thing we'll be doing is creating a new IntelliJ project and use the ktor template.

![](../assets/ktor-project-setup.png)

After this we'll press "Next" and fill out the necessary information and then press "Finish". Now we have a brand new
ktor project.

Now we can begin adding the needed dependencies.

Replace `x.x.x` with the latest
version [![Maven Central](https://img.shields.io/maven-central/v/de.stuebingerb/kgraphql.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22de.stuebingerb%22%20AND%20a:%22kgraphql%22).

=== "Kotlin Gradle Script"
    ```kotlin
    dependencies {
        implementation("de.stuebingerb:kgraphql:x.x.x")
        implementation("de.stuebingerb:kgraphql-ktor:x.x.x")
    }
    ```
=== "Gradle"
    ```groovy
    dependencies {
        implementation "de.stuebingerb:kgraphql:x.x.x"
        implementation "de.stuebingerb:kgraphql-ktor:x.x.x"
    }
    ```

The only thing left is installing the GraphQL feature onto our server by opening `src/Application.kt` and use these
lines as the `Application.module` function

=== "Application.kt"
    ```kotlin
    fun Application.module(testing: Boolean = false) {
        install(GraphQL) {
            configureRouting()
            playground = true
            schema {
                query("hello") {
                    resolver { -> "World" }
                }
            }
        }
    }
    ```

Now we have a fully functional GraphQL Server and we can startup our server by pressing the green play icon beside the
`main` function.

We can test out our server by going to [localhost:8080/graphql](http://localhost:8080/graphql) and our `hello` query
should work by providing this query to the GraphQL Playground

![](../assets/ktor-playground.png)

A great place to learn more is following the [Star Wars tutorial](./starwars.md). Everything mentioned in this tutorial
can be placed inside the `schema {}` block.
