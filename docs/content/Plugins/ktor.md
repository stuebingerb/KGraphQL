# Ktor

If you are running a ktor server. There is a separate package that makes it easy to set up a fully functional GraphQL
server.

You first need to add the KGraphQL-ktor package to your dependency

```kotlin tab="Kotlin Gradle Script"
implementation("com.apurebase:kgraphql-ktor:$KGraphQLVersion")
```

```groovy tab="Gradle"
implementation 'com.apurebase:kgraphql-ktor:${KGraphQLVersion}'
```

```xml tab="Maven"
<dependency>
    <groupId>com.apurebase</groupId>
    <artifactId>kgraphql-ktor</artifactId>
    <version>${KGraphQLVersion}</version>
</dependency>
```

## Initial setup

To set up KGraphQL you'll need to install the GraphQL feature like you would any
other [ktor feature](https://ktor.io/servers/features.html).

```kotlin
fun Application.module() {
  install(GraphQL) {
    playground = true 
    schema {
      query("hello") {
        resolver { -> "World!" }
      }
    }
  }
}
```

Now you have a fully working GraphQL server. We have also set `playground = true`, so when running this you will be able
to open [http://localhost:8080/graphql](http://localhost:8080/graphql) _(your port number may vary)_ in your browser and
test it out directly within the browser.

## Configuration options

The GraphQL feature is extending the standard [KGraphQL configuration](/Reference/configuration) and providing its own
set of configuration as described in the table below.

| Property   | Description                                                                                                                                                                    | Default value  |
|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| playground | Gives you out of the box access to a [GraphQL playground](https://github.com/prisma-labs/graphql-playground)                                                                   | `false`        |
| endpoint   | This specifies what route will be delivering the GraphQL endpoint. When `playground` is enabled, it will use this endpoint also.                                               | `/graphql`     |
| context    | Refer to example below                                                                                                                                                         |                |
| wrap       | If you want to wrap the route into something before KGraphQL will install the GraphQL route. You can use this wrapper. See example below for a more in depth on how to use it. |                |
| schema     | This is where you are defining your schema. Please refer to [KGraphQL References](/Reference/operations) for further documentation on this.                                    | ***required*** |

### Wrap

Sometimes you would need to wrap your route within something. A good example of this would be the `authenticate`
function provided by [ktor Authentication feature](https://ktor.io/servers/features/authentication.html).

```kotlin
wrap {
  authenticate(optional = true, build = it)
}
```

This works great alongside the [context](#context) to provide a context to the KGraphQL resolvers.

### Context

To get access to the context

```kotlin
context { call ->
  // access to authentication is only available if this is wrapped inside a `authenticate` before hand. 
  call.authentication.principal<User>()?.let {
    +it
  }
}
schema {
  query("hello") {
    resolver { ctx: Context ->
      val user = ctx.get<User>()!!
      "Hello ${user.name}"
    }
  }  
}
```
