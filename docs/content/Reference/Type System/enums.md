# Enums

GraphQL Enums are a variant on the Scalar type, which represents one of a finite set of possible values. They directly
map to Kotlin enums:

=== "Example"
    ```kotlin
    enum class Coolness {
        NOT_COOL, COOL, TOTALLY_COOL
    }
    
    val schema = KGraphQL.schema {
        enum<Coolness> {
            description = "State of coolness"
            value(Coolness.COOL) {
                description = "really cool"
            }
        }
        
        query("cool") {
            resolver { cool: Coolness -> cool.toString() }
        }
    }
    ```
=== "SDL"
    ```graphql
    type Query {
      cool(cool: Coolness!): String!
    }

    "State of coolness"
    enum Coolness {
      "really cool"
      COOL
      NOT_COOL
      TOTALLY_COOL
    }
    ```

Enum values can be [deprecated](../deprecation.md):

=== "Example"
    ```kotlin
    enum class Coolness {
        NOT_COOL, COOL, TOTALLY_COOL
    }

    val schema = KGraphQL.schema {
        enum<Coolness> {
            value(Coolness.NOT_COOL) {
                deprecate("Be cool!")
            }
        }

        query("cool") {
            resolver { cool: Coolness -> cool.toString() }
        }
    }
    ```
=== "SDL"
    ```graphql
    type Query {
      cool(cool: Coolness!): String!
    }
    
    enum Coolness {
      COOL
      NOT_COOL @deprecated(reason: "Be cool!")
      TOTALLY_COOL
    }
    ```
