---
layout: default
title: Union Properties
grand_parent: Type System
parent: Objects and Interfaces
nav_order: 3
---

As Kotlin does not support unions, union properties have to be explicitly declared in Schema DSL. Creating union property requires defining [resolver]({{site.baseurl}}creating-schema/resolver) and return type. Return Type is reference returned by creation of [union type]({{site.baseurl}}creating-schema/type-system/unions).

Union type resolver is not type checked. Invalid resolver implementation which would return value of type other than members of union type will fail in runtime.

*Example*

```kotlin
data class UnionMember1(val one : String)

data class UnionMember2(val two : String)

data class Person(val name: String, val age: Int)

KGraphQL.schema {
    type<Person>{
        unionProperty("union"){
            returnType = unionType("UnionExample"){
                 type<UnionMember1>()
                 type<UnionMember2>()
            }
            resolver { person -> if (person.age <= 18) UnionMember1("one") else UnionMember2("two") }
        }
    }
}
```
