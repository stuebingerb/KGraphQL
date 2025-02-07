# Overview

GraphQL Objects and Interfaces represent a list of named fields, each of which yield a value of a specific type.
KGraphQL inspects defined operations to create type system, but schema creator is able to explicitly declare and
customize types. Besides, only member properties are inspected.

**type { }**
`type` method is entry point to Type DSL.
See [Extension Properties](#extension-properties), [Kotlin Properties](#kotlin-properties), [Union Properties](#union-properties).

*Example*

```kotlin
data class Person(val name: String, val age: Int)

KGraphQL.schema {
    type<Person>{
        description = "Human"
    }
}
```

**KProperty1<T, R>.ignore**
extension function `ignore()` makes KGraphQL ignore its receiver property.

*Example*

```kotlin
data class Person(val name: String, val age: Int)

KGraphQL.schema {
    type<Person>{
        Person::age.ignore()
    }
}
```

**transformation(KProperty1<T, R>) {}**

`transformation` method allows schema creator to attach data transformation on any kotlin property of type. Like
operations, `transformation` has property `resolver` which is used to declare data transformation.

*Example*

```kotlin
data class Person(val name: String, val age: Int)

KGraphQL.schema {
    type<Person>{
        transformation(Person::age) {
            //inMonths is nullable, so client can fetch age property without passing any value to this argument
            //if(null == true) evaluates to false, if(null) is invalid kotlin code
            age: Int , inMonths : Boolean? -> if(inMonths == true) age * 12 else age
        }
    }
}
```

## Kotlin Properties

Kotlin properties are automatically inspected during schema creation. Schema DSL allows ignoring
and [deprecation](../deprecation.md) of kotlin properties

*Example*

```kotlin
data class Person(val name: String, val age: Int)

KGraphQL.schema {
    type<Person>{
        property(Person::age){
            ignore = true
        }
        property(Person::name){
            deprecate("Person need no name")
        }
    }
}
```

## Extension Properties

Extension properties allow schema creator to easily attach additional field to any type. It is separately evaluated
after main entity is resolved.

## property { }

`property` method accepts [resolver](../resolver.md) and can be subject of [deprecation](../deprecation.md).

*Example*

```kotlin
data class Person(val name: String, val age: Int)

KGraphQL.schema {
    type<Person>{
        property<Boolean>("isChild"){
            resolver { person -> (person.age <= 18) }
        }
    }
}
```

## Union Properties

As Kotlin does not support unions, union properties have to be explicitly declared in Schema DSL. Creating union
property requires defining [resolver](../resolver.md) and return type. Return Type is reference returned by
creation of [union type](unions.md).

Union type resolver is not type checked. Invalid resolver implementation which would return value of type other than
members of union type will fail in runtime.

*Example*

```kotlin
data class UnionMember1(val one : String)

data class UnionMember2(val two : String)

data class Person(val name: String, val age: Int)

KGraphQL.schema {
    type<Person>{
        unionProperty("union"){
            nullable = true // Defaults to false
            returnType = unionType("UnionExample"){
                 type<UnionMember1>()
                 type<UnionMember2>()
            }
            resolver { person -> if (person.age <= 18) UnionMember1("one") else UnionMember2("two") }
        }
    }
}
```

## Data Loaded Properties

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

### Setting up

Using DataLoaders requires the `DataLoaderPrepared` executor:

```kotlin
configure {
    executor = Executor.DataLoaderPrepared
}
```

*Example*

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

### Current known issues

This feature can be used in production but does currently have some issues:

1. The `useDefaultPrettyPrint` doesn't work
1. Order of fields are not guaranteed to match the order that was requested
1. Custom generic type resolvers are not supported
1. Other than that it should work as expected
1. Schema stitching is not supported
