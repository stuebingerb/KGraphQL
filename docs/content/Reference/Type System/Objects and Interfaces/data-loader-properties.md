*This feature is still in experimental state.*

One issue that you could easily encounter when doing a GraphQL API is the N+1 Problem. You can read more about this
problem and solution in depth
here: [The GraphQL Dataloader Pattern: Visualized](https://medium.com/@__xuorig__/the-graphql-dataloader-pattern-visualized-3064a00f319f)

Imagine having this query:

```graphql
{
  people(first: 10) {
    name
    friends(first: 5) {
      name
    }
  }
}
```

Running this against a Database would execute 11 SQL queries:
First fetch the 10 people. Then loop through each of them and fetch their first 5 friends one by one.

While what we would like is to fetch the first 10 people in 1 SQL and then fetch the first 5 friends for all those 10
people in the second query. That's what the `dataProperty` will solve for you.

## Setting up

Using DataLoaders requires the `DataLoaderPrepared` executor:

```kotlin
configure {
    executor = Executor.DataLoaderPrepared
}
```

*example*

```kotlin
data class Person(val id: Int, val name: String)
val people = (1..5).map { Person(it, "Name-$it") }
...
configure {
    executor = Executor.DataLoaderPrepared
}
query("people") {
    resolver { -> people }
}
type<Person> {
    // Int - defines the key that will be sent from the [prepare] into [loader]
    // Person? - defines the return type that the [loader] is required to return.
    // the loader is then required to return it in a map format like Map<Int, Person?>
    dataProperty<Int, Person?>("nextPerson") {
        prepare { person, skipAmount: Int -> person.id + skipAmount }
        loader { ids ->
            ids.map {
                ExecutionResult.Success(people[it-1])
            }
        }
    }
}
```

*GraphQL Query example:*

```graphql
{
    people {
        name
        nextPerson(skipAmount: 2) {
          name
        }
    }
}
```

Returns:

```json
{
  "data": {
    "people": [
      {
        "name": "Name-1",
        "nextPerson": {
          "name": "Name-3"
        }
      },
      {
        "name": "Name-2",
        "nextPerson": {
          "name": "Name-4"
        }
      },
      {
        "name": "Name-3",
        "nextPerson": {
          "name": "Name-5"
        }
      },
      {
        "name": "Name-4",
        "nextPerson": null
      },
      {
        "name": "Name-5",
        "nextPerson": null
      }
    ]
  }
}
```

## Current known issues

This feature can be used in production but does currently have some issues:

1. The `useDefaultPrettyPrint` doesn't work.
1. Order of fields are not guaranteed, to match the order that was requested
1. Other than that it should work as expected.
