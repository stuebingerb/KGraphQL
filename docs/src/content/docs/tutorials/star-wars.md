---
title: Star Wars
sidebar:
    order: 1
---

As an example, let us partially reproduce the Star Wars schema from the official GraphQL tutorial.

## Domain model

First, we need to define our domain model using by plain kotlin classes.

```kotlin
enum class Episode {
  NEWHOPE, EMPIRE, JEDI
}

interface Character {
  val id : String
  val name : String?
  val friends: List<Character>
  val appearsIn: Set<Episode>
}

data class Human (
  override val id: String,
  override val name: String?,
  override val friends: List<Character>,
  override val appearsIn: Set<Episode>,
  val homePlanet: String,
  val height: Double
) : Character

data class Droid (
  override val id: String,
  override val name: String?,
  override val friends: List<Character>,
  override val appearsIn: Set<Episode>,
  val primaryFunction : String
) : Character
```

## Define data

Next, we define our data

```kotlin
val luke = Human("2000", "Luke Skywalker", emptyList(), Episode.values().toSet(), "Tatooine", 1.72)
val r2d2 = Droid("2001", "R2-D2", emptyList(), Episode.values().toSet(), "Astromech")
```

## Define schema

Then, we can create the schema.

```kotlin
// KGraphQL#schema { } is entry point to create KGraphQL schema
val schema = KGraphQL.schema {
  //configure method allows you customize schema behaviour
  configure {
    useDefaultPrettyPrinter = true
  }

  // create query "hero" which returns instance of Character
  query("hero") {
    resolver { episode: Episode ->
      when (episode) {
        Episode.NEWHOPE, Episode.JEDI -> r2d2
        Episode.EMPIRE -> luke
      }
    }
  }

  // create query "heroes" which returns list of luke and r2d2
  query("heroes") {
    resolver{ -> listOf(luke, r2d2) }
  }

  // kotlin classes need to be registered with "type" method 
  // to be included in created schema type system
  // class Character is automatically included, 
  // as it is return type of both created queries  
  type<Droid>()
  type<Human>()
}
```

## Run queries against the schema

Now, we can query our schema.

### Request a single hero

```kotlin
// query for hero from episode JEDI and take id, name for any Character, and primaryFunction for Droid or height for Human
schema.execute("""
  {
    hero(episode: JEDI) {
      id
      name
      ... on Droid {
        primaryFunction
      }
      ... on Human {
        height
      }
    }
  }
""")
```

```json title="Response"
{
  "data" : {
    "hero" : {
      "id" : "2001",
      "name" : "R2-D2",
      "primaryFunction" : "Astromech"
    }
  }
}
```


### List all heroes

```kotlin
// query for all heroes and take id, name for any Character, and primaryFunction for Droid or height for Human
schema.execute("""
  {
    heroes {
      id
      name
      ... on Droid {
        primaryFunction
      }
      ... on Human {
        height
      }
    }
  }
""")
```

```json title="Response"
{
  "data" : {
    "heroes" : [
      {
        "id" : "2000",
        "name" : "Luke Skywalker",
        "height" : 1.72
      },
      {
        "id" : "2001",
        "name" : "R2-D2",
        "primaryFunction" : "Astromech"
      }
    ]
  }
}
```
