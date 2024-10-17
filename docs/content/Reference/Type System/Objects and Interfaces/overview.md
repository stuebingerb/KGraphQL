GraphQL Objects and Interfaces represent a list of named fields, each of which yield a value of a specific type.
KGraphQL inspects defined operations to create type system, but schema creator is able to explicitly declare and
customize types. Besides, only member properties are inspected.

## type { }

`type` method is entry point to Type DSL.
See [Extension Properties](/docs/reference/type-system/objects-and-interfaces/extension-properties), [Kotlin Properties](/docs/reference/type-system/objects-and-interfaces/kotlin-properties), [Union Properties](/docs/reference/type-system/objects-and-interfaces/union-properties).

*Example*

```kotlin
data class Person(val name: String, val age: Int)

KGraphQL.schema {
    type<Person> {
        description = "Human"
    }
}
```

## KProperty1<T, R>.ignore

extension function `ignore()` makes KGraphQL ignore its receiver property.

*Example*

```kotlin
data class Person(val name: String, val age: Int)

KGraphQL.schema {
    type<Person> {
        Person::age.ignore()
    }
}
```

## transformation(KProperty1<T, R>) {}

`transformation` method allows schema creator to attach data transformation on any kotlin property of type. Like
operations, `transformation` has property `resolver` which is used to declare data transformation.

*Example*

```kotlin
data class Person(val name: String, val age: Int)

KGraphQL.schema {
    type<Person> {
        transformation(Person::age) {
            // inMonths is nullable, so client can fetch age property without passing any value to this argument
            // if(null == true) evaluates to false, if(null) is invalid kotlin code
            age: Int, inMonths: Boolean? -> if (inMonths == true) { age * 12 } else { age }
        }
    }
}
```
