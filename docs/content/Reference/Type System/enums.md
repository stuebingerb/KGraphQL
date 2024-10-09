# Enums

GraphQL Enums are a variant on the Scalar type, which represents one of a finite set of possible values. They directly
map to Kotlin enums

*Example*

```kotlin
enum class Coolness {
    NOT_COOL, COOL, TOTALLY_COOL
}

val schema = KGraphQL.schema {
    enum<Coolness>{
        description = "State of coolness"
        value(Coolness.COOL){
            description = "really cool"
        }
    }
    
    query("cool"){
        resolver{ cool: Coolness -> cool.toString() }
    }
}
```

Enum values can be [deprecated](/Reference/deprecation).
