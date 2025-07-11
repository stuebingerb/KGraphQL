# Overview

The Type System is fundamental of a GraphQL schema. Types or based on Kotlin classes and own defined type definitions
provided via DSL.

## Type System Creation

KGraphQL is able to inspect operations and partially infer schema type system, so schema creator does not have to
explicitly declare every type (but it may if needed). Unions, Enums and Scalars require explicit definition in Schema
DSL. Inferred classes are interpreted as GraphQL Object or Interface type.

## Object or Interface?

KGraphQL maps found types (implicit and explicit) to GraphQL simple inheritance model, where every type with fields is
either Object or Interface. Rules are following:

* if Class in schema is superclass of another Class in schema, it is interpreted as GraphQL Interface type.
* if Class in schema is NOT superclass of any another Class in schema, it is interpreted as GraphQL Object type.
* if Interface in schema is implemented by any Class in schema, it is interpreted as GraphQL Interface type.
* if Interface in schema is NOT implemented by any Class in schema, it is interpreted as GraphQL Object type.

## Built-in Types

By default, every schema has following built-in [scalars](scalars.md):

* **String** - represents textual data, represented as UTF‐8 character sequences
* **Int** - represents a signed 32‐bit numeric non‐fractional value
* **Float** - represents signed double‐precision fractional values as specified by IEEE 754. KGraphQL represents Kotlin
  primitive Double and Float values as GraphQL Float.
* **Boolean** - represents true or false

## Introspection Types

Introspection interface aligns to [GraphQL specification](https://spec.graphql.org/October2021/#sec-Introspection) with
additions from the current working draft (deprecated input fields and repeatable directives).
