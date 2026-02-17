---
title: Scalars
weight: 1
---

As defined by specification, scalar represents a primitive value in GraphQL. In KGraphQL, besides built-in scalar types,
client code can declare custom scalar type, which can coerce to `String`, `Boolean`, `Int`, `Long` or `Float` (`kotlin.Double`).

KGraphQL provides a group of DSL methods to define scalars:

* `stringScalar { }`
* `booleanScalar { }`
* `intScalar { }`
* `longScalar { }`
* `floatScalar { }`

They differ only by the Kotlin primitive type they coerce to.

Every scalar has to define its coercion functions `deserialize` and `serialize`, or a coercion object that implements the
correct subtype of `com.apurebase.kgraphql.schema.scalar.ScalarCoercion`:

=== "Example (direct coercion function)"
    ```kotlin
    stringScalar<UUID> {
      deserialize = { uuid: String -> UUID.fromString(uuid) }
      serialize = UUID::toString
    }
    ```
=== "Example (coercion object)"
    ```kotlin
    stringScalar<UUID> {
      coercion = object : StringScalarCoercion<UUID> {
        override fun serialize(instance: UUID): String = instance.toString()
        override fun deserialize(raw: String, valueNode: ValueNode?): UUID = UUID.fromString(raw)
      }
    }
    ```

In addition to the built-in scalars, KGraphQL provides support for `Long`, `Short`, and `Char` which can be added to
a schema using `extendedScalars()`.

=== "Example"
    ```kotlin
    val schema = KGraphQL.schema {
        extendedScalars()
    
        query("getLong") {
            resolver { -> 3L }
        }
    }
    ```
