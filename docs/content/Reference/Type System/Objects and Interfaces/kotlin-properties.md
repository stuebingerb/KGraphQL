---
title: Kotlin Properties
weight: 1
---

Kotlin properties are automatically inspected during schema creation. Schema DSL allows ignoring
and [deprecation](/docs/reference/deprecation) of kotlin properties.

*Example*

```kotlin
data class Person(val name: String, val age: Int)

KGraphQL.schema {
    type<Person> {
        property(Person::age) {
            ignore = true
        }
        property(Person::name) {
            deprecate("Person needs no name")
        }
    }
}
```
