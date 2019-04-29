---
layout: default
title: Unions
parent: Type System
nav_order: 3
---

GraphQL Unions represent an object that could be one of a list of GraphQL Object types, but provides for no guaranteed fields between those types.

## unionType { }

union allows to define possible types of union. Union members have to be object types (Object or Interface). It returns reference to created union type, which is required to define properties with union return type.

*Example*

```kotlin
data class UnionMember1(val one : String)

data class UnionMember2(val two : String)

KgraphQL.schema {
    val unionExample = unionType("UnionExample"){
        type<UnionMember1>()
        type<UnionMember2>()
    }
}
```
