# Overview

GraphQL Objects and Interfaces represent a list of named fields, each of which yield a value of a specific type.
KGraphQL inspects defined operations to create type system, but schema creator is able to explicitly declare and
customize types. Besides, only member properties are inspected.

**type { }**
`type` method is entry point to Type DSL.
See [Extension Properties](#extension-properties), [Kotlin Properties](#kotlin-properties), [Union Properties](#union-properties).

=== "Example"
    ```kotlin
    data class Person(val name: String, val age: Int)

    val schema = KGraphQL.schema {
        query("person") {
            resolver { -> Person("John Smith", 42) }
        }
        type<Person>{
            description = "Human"
        }
    }
    ```
=== "SDL"
    ```graphql
    type Person {
      age: Int!
      name: String!
    }
    
    type Query {
      person: Person!
    }
    ```

## Kotlin Properties

Kotlin properties are automatically inspected during schema creation. Schema DSL allows ignoring
and [deprecation](../deprecation.md) of kotlin properties as well as renaming or transforming.

=== "Example"
    ```kotlin
    data class Person(val firstName: String, val lastName: String, val name: String, val age: Int)

    val schema = KGraphQL.schema {
        query("person") {
            resolver { -> Person("John", "Smith", "John Smith", 42) }
        }
        type<Person>{
            property(Person::age){
                ignore = true
            }
            property(Person::name){
                name = "fullName"
                deprecate("Use firstName and lastName")
            }
        }
    }
    ```
=== "SDL"
    ```graphql
    type Person {
      firstName: String!
      fullName: String! @deprecated(reason: "Use firstName and lastName")
      lastName: String!
    }
    
    type Query {
      person: Person!
    }
    ```

**KProperty1<T, R>.ignore**

The extension function `ignore()` makes KGraphQL ignore its receiver property.

=== "Example"
    ```kotlin
    data class Person(val name: String, val age: Int)
    
    val schema = KGraphQL.schema {
        query("person") {
            resolver { -> Person("John Smith", 42) }
        }
        type<Person>{
            Person::age.ignore()
        }
    }
    ```
=== "SDL"
    ```graphql
    type Person {
      name: String!
    }
    
    type Query {
      person: Person!
    }
    ```

**transformation(KProperty1<T, R>) {}**

The `transformation` function allows to attach data transformation on any existing Kotlin property.

=== "Example"
    ```kotlin
    data class Person(val name: String, val age: Int)
    
    val schema = KGraphQL.schema {
        query("person") {
            resolver { -> Person("John Smith", 42) }
        }
        type<Person> {
            transformation(Person::age) { age: Int, inMonths: Boolean? ->
                if (inMonths == true) {
                    age * 12
                } else {
                    age
                }
            }
        }
    }
    ```
=== "Query"
    ```graphql
    {
      person {
        years: age
        months: age(inMonths: true)
      }
    }
    ```
=== "Response"
    ```json
    {
      "data": {
        "person": {
          "years": 42,
          "months": 504
        }
      }
    }
    ```

Transformations can also be used to change the return type of a property, for example to make nullable
properties non-nullable:

=== "Example"
    ```kotlin
    data class Person(val name: String?, val age: Int)
    
    val schema = KGraphQL.schema {
        query("person") {
            resolver { -> Person(null, 42) }
        }
        type<Person> {
            transformation(Person::name) { name: String? ->
                name ?: "(no name)"
            }
        }
    }
    ```
=== "Query"
    ```graphql
    {
      person {
        name
      }
    }
    ```
=== "Response"
    ```json
    {
      "data": {
        "person": {
          "name": "(no name)"
        }
      }
    }
    ```
=== "SDL"
    ```graphql
    type Person {
      age: Int!
      name: String!
    }
    
    type Query {
      person: Person!
    }
    ```

Transformations can even change the type to a completely different class:

=== "Example"
    ```kotlin
    data class Person(val name: String, val age: Int)
    
    val schema = KGraphQL.schema {
        query("person") {
            resolver { -> Person("John Smith", 42) }
        }
        type<Person> {
            transformation(Person::age) { age: Int ->
                if (age == 42) {
                    "fourty-two"
                } else {
                    age.toString()
                }
            }
        }
    }
    ```
=== "Query"
    ```graphql
    {
      person {
        age
      }
    }
    ```
=== "Response"
    ```json
    {
      "data": {
        "person": {
          "age": "fourty-two"
        }
      }
    }
    ```
=== "SDL"
    ```graphql
    type Person {
      age: String!
      name: String!
    }
    
    type Query {
      person: Person!
    }
    ```

## Extension Properties

Extension properties allow schema creator to easily attach additional field to any type. It is separately evaluated
after main entity is resolved.

## property { }

`property` method accepts [resolver](../resolver.md) and can be subject of [deprecation](../deprecation.md).

=== "Example"
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

=== "Example"
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

=== "Query"
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

=== "Example"
    ```kotlin
    data class Person(val id: Int, val name: String)
    val people = (1..5).map { Person(it, "Name-$it") }
    ...
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
=== "Query"
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
=== "Response"
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
