---
layout: default
title: Extension Properties
parent: Objects and Interfaces
nav_order: 2
---

Extension properties allow schema creator to easily attach additional field to any type. It is separately evaluated after main entity is resolved.

## property { }
`property` method accepts [resolver]({{site.baseurl}}creating-schema/resolver) and can be subject of [deprecation]({{site.baseurl}}creating-schema/deprecation).

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
