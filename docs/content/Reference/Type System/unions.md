---
title: Unions
weight: 3
---

GraphQL Unions represent an object that could be one of a list of GraphQL Object types, but provides for no guaranteed
fields between those types.

There are 2 ways of defining a union type.

### Manual

*Example*

```kotlin
data class UnionMember1(val one: String)
data class UnionMember2(val two: String)

KgraphQL.schema {
    val unionExample = unionType("UnionExample"){
        type<UnionMember1>()
        type<UnionMember2>()
    }

    type<MyType> {
        unionProperty("unionExample") {
            returnType = unionExample
            resolver { _, isOne: Boolean ->
                if (isOne) UnionMember1(one = "Hello")
                else UnionMember2(two = "World")
            }
        }
    }
}
```

*Currently there is a limitation on union return types for `query` definitions. This is currently only supported via
sealed classes. See more information below.*

### Sealed Class

*Example*

```kotlin
sealed class UnionExample {
    class UnionMember1(val one: String): UnionExample()
    class UnionMember2(val two: String): UnionExample()
}

KgraphQL.schema {
    unionType<UnionExample>()

    // Query definition example:
    query("unionQuery") {
        resolver { isOne: Boolean ->
            if (isOne) UnionMember1(one = "Hello")
            else UnionMember2(two = "World")
        }
    }

    // Type property example:
    type<MyType> {
        property<UnionExample>("unionProperty") {
            resolver { _, isOne: Boolean ->
                if (isOne) UnionMember1(one = "Hello")
                else UnionMember2(two = "World")
            }
        }
    }
}
```

