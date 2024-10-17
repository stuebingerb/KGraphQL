# Access Rule

It's possible to add restriction unto your resolvers by using the `accessRule`.

*Example*

```kotlin
type<MyType> {
    property<String>("secretData") {
        // Some resolver
        resolver { ctx: Context -> myService.getSecret(ctx.userId) }
        
        // Return an exception or null
        accessRule { item: MyType, ctx: Content ->
            if (item.ownerId != ctx.userId) {
              IncorrectOwnerException()
            } else {
              null
            }
        }
    }
}
```

When an exception is returned it will not call the resolver and return an error with the specified exception.
