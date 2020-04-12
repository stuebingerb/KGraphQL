---
title: Unions
weight: 3
---

GraphQL Unions represent an object that could be one of a list of GraphQL Object types, but provides for no guaranteed fields between those types.

There are 2 ways og defining a union type.

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
}
```

### Sealed Class

*Example*

```kotlin
sealed class UnionExample {
    class UnionMember1(val one: String): UnionExample()
    class UnionMember2(val two: String): UnionExample()
}

KgraphQL.schema {
    unionType<UnionExample>()
}
```

