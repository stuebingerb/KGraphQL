---
layout: default
title: Type System
parent: Creating Schema
has_children: true
nav_order: 2
---

Type System is a fundament of GraphQL schema. It is based on Kotlin type system and type definitions provided via DSL.

## Type System creation
KGraphQL is able to inspect operations and partially infer schema type system, so schema creator does not have to explicitly declare every type (but it may if needed). Unions, Enums and Scalars require explicit definition in Schema DSL. Inferred classes are interpreted as GraphQL Object or Interface type.


## Object or Interface?
KGraphQL maps found types (implicit and explicit) to GraphQL simple inheritance model, where every type with fields is either Object or Interface. Rules are following:

* if Class in schema is superclass of another Class in schema, it is interpreted as GraphQL Interface type.
* if Class in schema is NOT superclass of any another Class in schema, it is interpreted as GraphQL Object type.
* if Interface in schema is implemented by any Class in schema, it is interpreted as GraphQL Interface type.
* if Interface in schema is NOT implemented by any Class in schema, it is interpreted as GraphQL Object type.

## Built in types
By default, every schema has following built in types:

### Scalars
* **String** - represents textual data, represented as UTF‐8 character sequences
* **Int** - represents a signed 32‐bit numeric non‐fractional value
* **Long** - represents a signed 64‐bit numeric non‐fractional value. Long type is not part of GraphQL specification, but it is built in primitive type in Kotlin language.
* **Float** - represents signed double‐precision fractional values as specified by IEEE 754. KGraphQL represents Kotlin primitive Double and Float values as GraphQL Float.
* **Boolean** - represents true or false

## Introspection types
Introspection interface aligns to [GraphQL specification](http://facebook.github.io/graphql/#sec-Schema-Introspection).
