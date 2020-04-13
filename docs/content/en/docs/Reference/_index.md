---
title: Creating Schema
linkTitle: Reference
weight: 5
---


`SchemaBuilder` exposes ability to create schema and configure it. Its methods accept closures to configure contained DSL items.

Schema creation is performed by invoking `KGraphQL.schema { ... }`

Every DSL receiver exposes `description: String?` property. Description is visible in introspection system. This property is omitted in documentation for each schema element.