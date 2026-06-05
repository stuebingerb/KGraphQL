---
title: Enums
---

GraphQL Enums are a variant on the Scalar type, which represents one of a finite set of possible values. 

## Definition

They directly map to Kotlin enums.

```kotlin title="Schema in code"
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

```graphql title="SDL"
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

## Deprecation

Enum values can be [deprecated](/reference/deprecation/):

```kotlin title="Schema in code"
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

```graphql title="SDL"
type Query {
    cool(cool: Coolness!): String!
}

enum Coolness {
    COOL
    NOT_COOL @deprecated(reason: "Be cool!")
    TOTALLY_COOL
}
```
