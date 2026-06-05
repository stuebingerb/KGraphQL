---
title: Input Objects
---

A GraphQL Input Object defines a set of input fields; the input fields are either scalars, enums, or other input
objects. Like Object and Interface types, Input Object types are inferred from defined operations, but can be explicitly
declared as well.

## Schema

Input Objects take their fields from the primary constructor, supporting property and non-property parameters.

```kotlin title="Schema in code"
// Non-data class with a constructor parameter that is not a property
class NonDataClass(param1: String = "Hello", val param3: Boolean?) {
    var param2: Int = param1.length
}

val schema = KGraphQL.schema {
    query("test") {
        resolver { input: NonDataClass -> input }
    }
}
```

```graphql title="SDL"
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

## Input Type Name

To avoid equal names between input and query types, input types get an automated `"Input"` suffix unless their name
already ends with `"Input"` or they have explicit configuration in the DSL via `inputType {}`.

In this case, the input type `Type` will automatically be renamed to `TypeInput` but `FooInput` will not become
`FooInputInput`.

```kotlin title="Schema in code"
class Type(val name: String)
data class FooInput(val name: String)
data class Foo(val name: String)

val schema = KGraphQL.schema {
    query("getFoo") {
        resolver { -> Foo("bar") }
    }
    mutation("addType") {
        resolver { input: Type -> input }
    }
    mutation("addFoo") {
        resolver { input: FooInput -> Foo(input.name) }
    }
}
```

```graphql title="SDL"
type Foo {
  name: String!
}

type Mutation {
  addFoo(input: FooInput!): Foo!
  addType(input: TypeInput!): Type!
}

type Query {
  getFoo: Foo!
}

type Type {
  name: String!
}

input FooInput {
  name: String!
}

input TypeInput {
  name: String!
}
```

This behavior applies recursively to nested types as well. The nested `ChildType` will also be renamed to `ChildTypeInput`.

```kotlin title="Schema in code"
class ChildType(val childName: String)
class ParentType(val parentName: String, val child: ChildType)

val schema = KGraphQL.schema {
    query("getParent") {
        resolver { -> ParentType("parent", ChildType("child")) }
    }
    mutation("addParent") {
        resolver { input: ParentType -> input }
    }
}
```

```graphql title="SDL"
type ChildType {
  childName: String!
}

type Mutation {
  addParent(input: ParentTypeInput!): ParentType!
}

type ParentType {
  child: ChildType!
  parentName: String!
}

type Query {
  getParent: ParentType!
}

input ChildTypeInput {
  childName: String!
}

input ParentTypeInput {
  child: ChildTypeInput!
  parentName: String!
}
```

## Runtime

Input Objects are instantiated via their primary constructor. Kotlin default values are used unless a different value
is provided explicitly. Due to a limitation in Kotlin, default values are not visible in the generated schema, though.

## DSL

Input types can be configured via the `inputType` DSL.

```kotlin title="Schema in code"
inputType<InputObject> {
    name = "MyInputObject"
    description = "Description for input object"
    
    InputObject::foo.configure {
        deprecate("Deprecated old input value")
    }
}
```

:::note
Whenever an input type is configured via DSL the automatic suffix will *not* be appended, regardless of the name.
:::

Here, both `InputType` and `ParentType` are explicitly configured, and therefore will keep their original name. A custom
`name` is not required.

```kotlin title="Schema in code"
class InputType(val name: String)
class Type(val name: String)
class ChildType(val childName: String)
class ParentType(val parentName: String, val child: ChildType)

val schema = KGraphQL.schema {
    query("getType") {
        resolver { -> Type("type") }
    }
    mutation("addType") {
        resolver { input: InputType -> Type(input.name) }
    }
    mutation("addParent") {
        resolver { input: ParentType -> input }
    }
    inputType<InputType>()
    inputType<ParentType> {
        name = "MyParentInputType"
    }
}
```

```graphql title="SDL"
type ChildType {
  childName: String!
}

type Mutation {
  addParent(input: MyParentInputType!): ParentType!
  addType(input: InputType!): Type!
}

type ParentType {
  child: ChildType!
  parentName: String!
}

type Query {
  getType: Type!
}

type Type {
  name: String!
}

input ChildTypeInput {
  childName: String!
}

input InputType {
  name: String!
}

input MyParentInputType {
  child: ChildTypeInput!
  parentName: String!
}
```

## Limitations

:::caution
Although non-property constructor parameters are supported, it is not possible to add a description or deprecate them.
:::
