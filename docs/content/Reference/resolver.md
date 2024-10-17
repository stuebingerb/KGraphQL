# Resolver

## Basics

In GraphQL every property needs a resolver. The resolver is the piece of system logic, required to resolve the response
graph. [Operations](/Reference/operations), [](/Reference/)
Resolver is KGraphQL definition of piece of system logic, required to resolve response graph. Operations, Extension
Properties and Union Properties accept resolver, which allows schema creator to configure schema behaviour.

Resolver clause accepts kotlin function and returns its DSL item, which is entry point for additional customization of
resolver

```kotlin
query("item") {
    resolver { -> Item(id, "Item Name") }
}
```

## Arguments

`withArgs` closure exposes single method `arg`

**arg { }**
`arg` exposes possibility to customize argument default value. Default value is automatically used if query doesn't
provide any. it is matched by argument name.

*Example*

```kotlin
KGraphQL.schema {
    query("data") {
        resolver { int: Int, string: String? -> int }.withArgs {
            arg<Int> { name = "int"; defaultValue = 33 }
        }
    }
}
```

## Context

To get access to the context object, you can just request for the `Context` object within your resolver.

When providing `Context` as an argument for your resolver, it will be skipped and not published to your API, but
KGraphQL will make sure to provide it to the resolver, so you can use it like the following example:

```kotlin
query("hello") {
	resolver { country: String, ctx: Context ->
		val user = ctx.get<User>()
		Hello(label = "Hello ${user?.name ?: "unknown"}")
	}
}
```

Then in your query execution process you will provide a `Context` like shown here:

```kotlin
val query = """
	query fetchHelloLabel($country: String!) {
		hello(country: $country) {
			label
		}
	}
"""
val variables = """
	{"country": "English"}
"""
val user = User(id = 1, name = "Username")
val ctx = context {
	+user
}
schema.execute(query, variables, ctx)
```
