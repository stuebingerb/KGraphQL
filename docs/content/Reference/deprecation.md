---
title: Deprecation
---

Schema creator is able to deprecate fields, operations and enum values. DSL builders for those schema elements expose
method `deprecate(reason: String)`. Deprecation is visible in schema introspection system with fields
`isDeprecated : Boolean` and `deprecationReason: String`.
