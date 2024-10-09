# Operations

## Overview

There are three types of operations that GraphQL models:

* [Query](#query) – a read‐only fetch.
* [Mutation](#mutation) – a write followed by a fetch.
* [Subscription](#subscription) – a long‐lived request that fetches data in response to source events.

Each operation is represented by an operation name and a selection set.

In KGraphQL, operation is declared in `SchemaBuilder` block. Every operation has 2 properties:

| name     | description                     |
|----------|---------------------------------|
| name     | name of operation               |
| resolver | [Resolver](/Reference/resolver) |

Selection set is automatically created based on resolver return type. By default, selection set for specific class
contains all its member properties (without extension properties), but it can be customized (TBD Type wiki page).
Operations can be [deprecated](/Reference/deprecation)

Subscription is not supported yet.

### Query

`query` allows to create resolver for query operation.

*Example*

```kotlin
data class Hero(val name : String, val age : Int)

query("hero"){
    description = "returns formatted name of R2-D2"
    resolver { -> Hero("R2-D2", 40) } 
}
```

This example adds query with name hero, which returns new instance of R2-D2 Hero. It can be queried with selection set
for name or age, example query: `{hero{name, age}}`

### Mutation

`mutation` allows to create resolver for mutation operation.

*Example*

```kotlin
mutation("createHero"){
    description = "Creates hero with specified name"
    resolver { name : String -> name } 
}
```

This example adds mutation with name `createHero`, which returns passed name.

### Subscription

`subscription` operations are not supported yet.
