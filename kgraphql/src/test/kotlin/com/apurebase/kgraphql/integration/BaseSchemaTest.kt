package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.Actor
import com.apurebase.kgraphql.ActorCalculateAgeInput
import com.apurebase.kgraphql.ActorExplicitInput
import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.Director
import com.apurebase.kgraphql.Film
import com.apurebase.kgraphql.FilmType
import com.apurebase.kgraphql.Id
import com.apurebase.kgraphql.IdScalarSupport
import com.apurebase.kgraphql.Person
import com.apurebase.kgraphql.Rank
import com.apurebase.kgraphql.Scenario
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.schema.execution.ExecutionOptions
import org.junit.jupiter.api.AfterEach

abstract class BaseSchemaTest {
    // test film 1
    val tomHardy = Actor("Tom Hardy", 232)
    val christianBale = Actor("Christian Bale", 232)
    val christopherNolan = Director("Christopher Nolan", 43, listOf(tomHardy, christianBale))
    val prestige = Film(Id("Prestige", 2006), 2006, "Prestige", christopherNolan)

    // test film 2
    val bradPitt = Actor("Brad Pitt", 763)
    val morganFreeman = Actor("Morgan Freeman", 1212)
    val kevinSpacey = Actor("Kevin Spacey", 2132)
    val davidFincher = Director("David Fincher", 43, listOf(bradPitt, morganFreeman, kevinSpacey))
    val se7en = Film(Id("Se7en", 1995), 1995, "Se7en", davidFincher)

    // test film 3
    val martinScorsese = Director("Martin Scorsese", 82, listOf())
    val theBigShave = Film(Id("The Big Shave", 1967), 1967, "The Big Shave", martinScorsese, FilmType.SHORT_LENGTH)

    val rickyGervais = Actor("Ricky Gervais", 58)

    // new actors created via mutations in schema
    val createdActors = mutableListOf<Actor>()

    private val testedSchema = defaultSchema {
        configure {
            useDefaultPrettyPrinter = true
        }

        extendedScalars()

        intScalar<Rank> {
            deserialize = ::Rank
            serialize = Rank::value
        }

        query("number") {
            description = "returns little of big number"
            resolver { big: Boolean -> if (big) 10000 else 0 }
        }
        query("float") {
            description = "returns the given float"
            resolver { float: Float -> float }
        }
        query("film") {
            description = "mock film"
            resolver { -> prestige }
        }
        query("actors") {
            description = "all actors"
            resolver { all: Boolean? ->
                mutableListOf(bradPitt, morganFreeman, kevinSpacey, tomHardy, christianBale).also {
                    if (all == true) it.add(rickyGervais)
                }
            }
        }
        query("actorsByTags") {
            description = "testing ktype & jvm erasure problems"
            resolver { tags: List<String> ->
                mutableListOf(bradPitt, morganFreeman, kevinSpacey, tomHardy)
            }
        }
        query("actorsByTagsOptional") {
            description = "testing ktype & jvm erasure problems"
            resolver { tags: List<String>? ->
                mutableListOf(bradPitt, morganFreeman, kevinSpacey, tomHardy)
            }.withArgs {
                arg<List<String>>(optional = true) { name = "tags"; defaultValue = emptyList() }
            }
        }
        query("actorsByTagsNullable") {
            description = "testing ktype & jvm erasure problems"
            resolver { tags: List<String>? ->
                mutableListOf(bradPitt, morganFreeman, kevinSpacey, tomHardy)
            }.withArgs {
                arg<List<String>>(optional = true) { name = "tags"; defaultValue = null }
            }
        }
        query("filmByRank") {
            description = "ranked films"
            resolver { rank: Int ->
                when (rank) {
                    1 -> prestige
                    2 -> se7en
                    3 -> theBigShave
                    else -> null
                }
            }
        }
        query("filmByRankLong") {
            description = "ranked films"
            resolver { rank: Long ->
                when (rank) {
                    1L -> prestige
                    2L -> se7en
                    3L -> theBigShave
                    else -> null
                }
            }
        }
        query("filmByRankShort") {
            description = "ranked films"
            resolver { rank: Short ->
                when (rank.toInt()) {
                    1 -> prestige
                    2 -> se7en
                    3 -> theBigShave
                    else -> null
                }
            }
        }
        query("filmByCustomRank") {
            description = "ranked films"
            resolver { rank: Rank ->
                when (rank.value) {
                    1 -> prestige
                    2 -> se7en
                    3 -> theBigShave
                    else -> null
                }
            }
        }
        query("filmsByType") {
            description = "film categorized by type"
            resolver { type: FilmType -> listOf(prestige, se7en, theBigShave).filter { it.type == type } }
        }
        query("people") {
            description = "List of all people"
            resolver { ->
                listOf(
                    davidFincher,
                    bradPitt,
                    morganFreeman,
                    christianBale,
                    christopherNolan,
                    martinScorsese
                )
            }
        }
        query("randomPerson") {
            description = "not really random person"
            resolver { -> davidFincher /*not really random*/ }
        }
        mutation("createActor") {
            description = "create new actor"
            resolver { name: String, age: Int ->
                val actor = Actor(name, age)
                createdActors.add(actor)
                actor
            }
        }
        mutation("createActorWithContext") {
            description = "create new actor with context"
            resolver { name: String, age: Int, ctx: Context ->
                val actor = Actor(name, age)
                createdActors.add(actor)
                actor
            }
        }
        mutation("createActorWithInput") {
            description = "create new actor"
            resolver { input: ActorExplicitInput ->
                val actor = Actor(input.name, input.age)
                createdActors.add(actor)
                actor
            }
        }
        mutation("createActorWithAges") {
            description = "create new actor"
            resolver { name: String, ages: List<Int> ->
                val actor = Actor(name, ages.reduce { sum, age -> sum + age })
                createdActors.add(actor)
                actor
            }
        }
        mutation("createActorWithAgesInput") {
            description = "create new actor"
            resolver { input: ActorCalculateAgeInput ->
                val actor = Actor(input.name, input.ages.reduce { sum, age -> sum + age })
                createdActors.add(actor)
                actor
            }
        }
        query("scenario") {
            resolver { -> Scenario(Id("GKalus", 234234), "Gamil Kalus", "Very long scenario") }
        }
        stringScalar<Id> {
            description = "unique, concise representation of film"
            coercion = IdScalarSupport()
        }
        enum<FilmType> { description = "type of film, base on its length" }
        type<Person> { description = "Common data for any person" }
        type<Scenario> {
            property(Scenario::author) {
                ignore = true
            }
            transformation(Scenario::content) { content: String, uppercase: Boolean? ->
                if (uppercase == true) {
                    content.uppercase()
                } else {
                    content
                }
            }
        }
        val favouriteID = unionType("Favourite") {
            type<Actor>()
            type<Scenario>()
            type<Director>()
        }

        type<Actor> {
            description = "An actor is a person who portrays a character in a performance"
            property<Boolean?>("isOld") {
                resolver { actor -> actor.age > 500 }
            }
            property("picture") {
                resolver { actor, big: Boolean? ->
                    val actorName = actor.name.replace(' ', '_')
                    if (big == true) {
                        "http://picture.server/pic/$actorName?big=true"
                    } else {
                        "http://picture.server/pic/$actorName?big=false"
                    }
                }
            }

            property("pictureWithArgs") {
                resolver { actor, big: Boolean? ->
                    val actorName = actor.name.replace(' ', '_')
                    if (big == true) {
                        "http://picture.server/pic/$actorName?big=true"
                    } else {
                        "http://picture.server/pic/$actorName?big=false"
                    }
                }.withArgs {
                    arg<Boolean>(optional = true) { name = "big"; description = "big or small picture" }
                }
            }

            unionProperty("favourite") {
                returnType = favouriteID
                resolver { actor ->
                    when (actor) {
                        bradPitt -> tomHardy
                        tomHardy -> christopherNolan
                        morganFreeman -> Scenario(Id("234", 33), "Paulo Coelho", "DUMB")
                        rickyGervais -> null
                        else -> christianBale
                    }
                }
            }
            unionProperty("nullableFavourite") {
                returnType = favouriteID
                nullable = true
                resolver { actor ->
                    when (actor) {
                        bradPitt -> tomHardy
                        tomHardy -> christopherNolan
                        morganFreeman -> Scenario(Id("234", 33), "Paulo Coelho", "DUMB")
                        rickyGervais -> null
                        else -> christianBale
                    }
                }
            }
        }

        mutation("createActorWithAliasedInputType") {
            description = "create new actor from full fledged ActorInput as input type"
            resolver { newActor: Actor ->
                createdActors.add(newActor)
                newActor
            }
        }
    }

    @AfterEach
    fun cleanup() = createdActors.clear()

    fun execute(
        query: String,
        variables: String? = null,
        context: Context = Context(emptyMap()),
        options: ExecutionOptions = ExecutionOptions(),
        operationName: String? = null,
    ) = testedSchema
        .executeBlocking(query, variables, context, options, operationName)
        .deserialize()
}
