# Unions

GraphQL Unions represent an object that could be one of a list of GraphQL Object types, but provides for no guaranteed
fields between those types.

## Schema

Sealed classes will automatically result in a union type.

=== "Example"
    ```kotlin
    class MyType
    
    sealed class UnionExample {
        class UnionMember1(val one: String) : UnionExample()
        class UnionMember2(val two: String) : UnionExample()
    }
    
    val schema = KGraphQL.schema {
        unionType<UnionExample>()
    
        // Query definition example:
        query("unionQuery") {
            resolver { isOne: Boolean ->
                if (isOne) {
                    UnionExample.UnionMember1(one = "Hello")
                } else {
                    UnionExample.UnionMember2(two = "World")
                }
            }
        }
    
        // Type property example:
        type<MyType> {
            property<UnionExample>("unionProperty") {
                resolver { _, isOne: Boolean ->
                    if (isOne) {
                        UnionExample.UnionMember1(one = "Hello")
                    } else {
                        UnionExample.UnionMember2(two = "World")
                    }
                }
            }
        }
    }
    ```
=== "SDL"
    ```graphql
    type MyType {
        unionProperty(isOne: Boolean!): UnionExample!
    }
    
    type Query {
      unionQuery(isOne: Boolean!): UnionExample!
    }
    
    type UnionMember1 {
      one: String!
    }
    
    type UnionMember2 {
      two: String!
    }
    
    union UnionExample = UnionMember1 | UnionMember2
    ```

## Manual Configuration

Unions can also be defined manually as part of a `unionProperty`.

=== "Example"
    ```kotlin
    class MyType
    data class UnionMember1(val one: String)
    data class UnionMember2(val two: String)
    
    val schema = KGraphQL.schema {
        val unionExample = unionType("UnionExample"){
            type<UnionMember1>()
            type<UnionMember2>()
        }
    
        query("myType") {
            resolver { -> MyType() }
        }
    
        type<MyType> {
            unionProperty("unionExample") {
                returnType = unionExample
                resolver { _, isOne: Boolean ->
                    if (isOne) {
                        UnionMember1(one = "Hello")
                    } else {
                        UnionMember2(two = "World")
                    }
                }
            }
        }
    }
    ```
=== "SDL"
    ```graphql
    type MyType {
      unionExample(isOne: Boolean!): UnionExample!
    }
    
    type Query {
      myType: MyType!
    }
    
    type UnionMember1 {
      one: String!
    }
    
    type UnionMember2 {
      two: String!
    }
    
    union UnionExample = UnionMember1 | UnionMember2
    ```

!!! note

    Currently there is a limitation on union return types for `query` definitions. This is currently only supported via
    sealed classes, see above.
