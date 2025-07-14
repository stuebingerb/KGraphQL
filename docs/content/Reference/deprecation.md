---
title: Deprecation
---

Schema creators are able to deprecate fields, operations, enum values, and input values. DSL builders for those schema
elements expose method `deprecate(reason: String)`. Deprecation is visible in schema introspection system with fields
`isDeprecated: Boolean` and `deprecationReason: String`.

Input values may only be deprecated if they are not required, cf. [3.13.3@deprecated](https://spec.graphql.org/draft/#sec--deprecated).

=== "Example"
    ```kotlin
    data class Sample(val content: String)

    val schema = KGraphQL.schema { 
        query("sample") {
            resolver { -> Sample("Hello world") }
        }
        type<Sample> {
            Sample::content.configure {
                deprecate("deprecated property")
            }
        }
    }
    ```
=== "SDL"
    ```graphql
    type Query {
      sample: Sample!
    }
    
    type Sample {
      content: String! @deprecated(reason: "deprecated property")
    }
    ```
