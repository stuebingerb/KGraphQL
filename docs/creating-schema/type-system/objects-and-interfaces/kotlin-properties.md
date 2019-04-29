---
layout: default
title: Kotlin Properties
grand_parent: Type System
parent: Objects and Interfaces
nav_order: 1
---

Kotlin properties are automatically inspected during schema creation. Schema DSL allows ignoring and [deprecation]({{site.baseurl}}creating-schema/deprecation) of kotlin properties

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
