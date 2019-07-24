---
title: Extension Properties
weight: 2
---

Extension properties allow schema creator to easily attach additional field to any type. It is separately evaluated after main entity is resolved.

## property { }
`property` method accepts [resolver](/docs/reference/resolver) and can be subject of [deprecation](/docs/reference/deprecation).

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
