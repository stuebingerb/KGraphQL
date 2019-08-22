---
title: Union Properties
weight: 3
---

As Kotlin does not support unions, union properties have to be explicitly declared in Schema DSL. Creating union property requires defining [resolver](/docs/reference/resolver) and return type. Return Type is reference returned by creation of [union type](/docs/reference/type-system/unions).

Union type resolver is not type checked. Invalid resolver implementation which would return value of type other than members of union type will fail in runtime.

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
