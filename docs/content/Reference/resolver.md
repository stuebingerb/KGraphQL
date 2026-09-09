# Resolver

In GraphQL every property needs a resolver. The resolver is the piece of system logic, required to resolve the response
graph. [Operations](operations.md), [Extension Properties](Type%20System/objects-and-interfaces.md/#extension-properties) and [Union Properties](Type%20System/unions.md) accept resolver, which allows
schema creators to configure schema behaviour.

## Schema

Resolver clause accepts kotlin function and returns its DSL item, which is entry point for additional customization of
resolver:

=== "Example"
    ```kotlin
    query("item") {
        resolver { -> Item(id, "Item Name") }
    }
    ```

## Arguments

The `withArgs` closure has a method `arg` that exposes the possibility to customize argument default values. The default value is automatically used if query doesn't
provide any, and is matched by argument name.

=== "Example"
    ```kotlin
    KGraphQL.schema {
        query("data") {
            resolver { int: Int, string: String? -> int }.withArgs {
                arg<Int> { name = "int"; defaultValue = 33 }
            }
        }
    }
    ```

## Context

To get access to the context object, you can request for the `Context` object within your resolver.

When providing `Context` as an argument for your resolver, it will be skipped and not published to your API, but
KGraphQL will make sure to provide it to the resolver, so you can use it like the following example:

=== "Example"
    ```kotlin
    query("hello") {
        resolver { country: String, ctx: Context ->
            val user = ctx.get<User>()
            Hello(label = "Hello ${user?.name ?: "unknown"}")
        }
    }
    ```

Then in your query execution process you will provide a `Context` like shown here:

=== "Example"
    ```kotlin
    val query = """
        query fetchHelloLabel($country: String!) {
            hello(country: $country) {
                label
            }
        }
    """
    val variables = """
        {"country": "English"}
    """
    val user = User(id = 1, name = "Username")
    val ctx = context {
        +user
    }
    schema.execute(query, variables, ctx)
    ```

## Node

Similar to `Context`, resolvers can also request for the `Node` object, which is a representation of the current node
in the response graph.

=== "Example"
    ```kotlin
    data class Director(val firstName: String, val lastName: String)
    data class Film(val title: String, val director: Director)
    
    val schema = KGraphQL.schema {
        configure { useDefaultPrettyPrinter = true }

        query("films") {
            resolver { ->
                listOf(
                    Film("Prestige", Director("Christopher", "Nolan")),
                    Film("Se7en", Director("David", "Fincher"))
                )
            }
        }
        type<Film> {
            property("fullPath") {
                resolver { _: Film, node: Execution.Node -> node.fullPath.joinToString(".") }
            }
        }
        type<Director> {
            property("fullPath") {
                resolver { _: Director, node: Execution.Node -> node.fullPath.joinToString(".") }
            }
        }
    }
    ```
=== "Query"
    ```graphql
    query {
        films {
            title
            fullPath
            director {
                firstName
                lastName
                fullPath
            }
        }
    }
    ```
=== "Response"
    ```json
    {
      "data" : {
        "films" : [ {
          "title" : "Prestige",
          "fullPath" : "films.0.fullPath",
          "director" : {
            "firstName" : "Christopher",
            "lastName" : "Nolan",
            "fullPath" : "films.0.director.fullPath"
          }
        }, {
          "title" : "Se7en",
          "fullPath" : "films.1.fullPath",
          "director" : {
            "firstName" : "David",
            "lastName" : "Fincher",
            "fullPath" : "films.1.director.fullPath"
          }
        } ]
      }
    }
    ```
