---
title: Input Objects
weight: 4
---

An GraphQL Input Object defines a set of input fields; the input fields are either scalars, enums, or other input
objects. Like Object and Interface types, Input Object types are inferred from defined operations, but can be explicitly
declared as well.

## Schema

Input Objects take their fields from the primary constructor, supporting property and non-property parameters.

*Example Definition*

```kotlin
// Non-data class with a constructor parameter that is not a property
class NonDataClass(param1: String = "Hello", val param3: Boolean?) {
    var param2: Int = param1.length
}

val schema = KGraphQL.schema {
    inputType<NonDataClass> {
        name = "NonDataClassInput"
    }
    query("test") {
        resolver { input: NonDataClass -> input }
    }
}
```

*Resulting SDL*

```graphql
type NonDataClass {
  param2: Int!
  param3: Boolean
}

type Query {
  test(input: NonDataClassInput!): NonDataClass!
}

input NonDataClassInput {
  param1: String!
  param3: Boolean
}
```

## Runtime

Input Objects are instantiated via their primary constructor. Kotlin default values are used unless a different value
is provided explicitly. Due to a limitation in Kotlin, default values are not visible in the generated schema, though.

## inputType {}

Input types can be configured via the `inputType` DSL.

*Example*

```kotlin
inputType<InputObject> {
    name = "MyInputObject"
    description = "Description for input object"
    
    InputObject::foo.configure {
        deprecate("Deprecated old input value")
    }
}
```

## Limitations

Although non-property constructor parameters are supported, it is not possible to add a description or deprecate them.
