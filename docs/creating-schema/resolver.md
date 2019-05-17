---
layout: default
title: Resolver
parent: Creating Schema
nav_order: 5
---

Resolver is KGraphQL definition of piece of system logic, required to resolve response graph. Operations, Extension Properties and Union Properties accept resolver, which allows schema creator to configure schema behaviour.

Resolver clause accepts kotlin function and returns its DSL item, which is entry point for additional customization of resolver

## withArgs {}
`withArgs` closure exposes single method `arg`

### arg { }
`arg` exposes possibility to customize argument default value. Default value is automatically used if query doesn't provide any. it is matched by argument name.

*Example*

```kotlin
KGraphQL.schema {
    query("data"){
        resolver { int: Int, string: String? -> int }.withArgs {
            arg <Int> { name = "int"; defaultValue = 33 }
        }
    }
}
```