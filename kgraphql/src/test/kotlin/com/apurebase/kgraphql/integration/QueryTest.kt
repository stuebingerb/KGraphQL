package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.Actor
import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.Director
import com.apurebase.kgraphql.ExecutionError
import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.Film
import com.apurebase.kgraphql.Id
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.RequestError
import com.apurebase.kgraphql.Scenario
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.assertNoErrors
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expectExecutionError
import com.apurebase.kgraphql.expectRequestError
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.helpers.getFields
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.scalar.ID
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test

class QueryTest : BaseSchemaTest() {
    @Test
    fun `query nested selection set`() {
        val map = execute("{film{title, director{name, age}}}")
        assertNoErrors(map)
        map.extract<String>("data/film/title") shouldBe prestige.title
        map.extract<String>("data/film/director/name") shouldBe prestige.director.name
        map.extract<Int>("data/film/director/age") shouldBe prestige.director.age
    }

    @Test
    fun `query collection field`() {
        val map = execute("{film{title, director{favActors{name, age}}}}")
        assertNoErrors(map)
        map.extract<Map<String, String>>("data/film/director/favActors[0]") shouldBe
            mapOf(
                "name" to prestige.director.favActors[0].name,
                "age" to prestige.director.favActors[0].age
            )
    }

    @Test
    fun `query scalar field`() {
        val map = execute("{film{id}}")
        assertNoErrors(map)
        map.extract<String>("data/film/id") shouldBe "${prestige.id.literal}:${prestige.id.numeric}"
    }

    @Test
    fun `query with selection set on collection`() {
        val map = execute("{film{title, director{favActors{name}}}}")
        assertNoErrors(map)
        map.extract<Map<String, String>>("data/film/director/favActors[0]") shouldBe mapOf("name" to prestige.director.favActors[0].name)
    }

    @Test
    fun `query with selection set on collection 2`() {
        val map = execute("{film{title, director{favActors{age}}}}")
        assertNoErrors(map)
        map.extract<Map<String, Int>>("data/film/director/favActors[0]") shouldBe mapOf("age" to prestige.director.favActors[0].age)
    }

    @Test
    fun `query with invalid field name`() {
        expectRequestError<ValidationException>("Property 'favDish' on 'Director' does not exist") {
            testedSchema.executeBlocking("{film{title, director{name, favDish}}}")
        }
    }

    @Test
    fun `query with argument`() {
        val map = execute("{filmByRank(rank: 1){title}}")
        assertNoErrors(map)
        map.extract<String>("data/filmByRank/title") shouldBe "Prestige"
    }

    @Test
    fun `query with argument 2`() {
        val map = execute("{filmByRank(rank: 2){title}}")
        assertNoErrors(map)
        map.extract<String>("data/filmByRank/title") shouldBe "Se7en"
    }

    @Test
    fun `query with alias`() {
        val map = execute("{bestFilm: filmByRank(rank: 1){title}}")
        assertNoErrors(map)
        map.extract<String>("data/bestFilm/title") shouldBe "Prestige"
    }

    @Test
    fun `query with field alias`() {
        val map = execute("{filmByRank(rank: 2){fullTitle: title}}")
        assertNoErrors(map)
        map.extract<String>("data/filmByRank/fullTitle") shouldBe "Se7en"
    }

    @Test
    fun `query with multiple aliases`() {
        val map = execute("{bestFilm: filmByRank(rank: 1){title}, secondBestFilm: filmByRank(rank: 2){title}}")
        assertNoErrors(map)
        map.extract<String>("data/bestFilm/title") shouldBe "Prestige"
        map.extract<String>("data/secondBestFilm/title") shouldBe "Se7en"
    }

    @Test
    fun `query with ignored property`() {
        expectRequestError<ValidationException>("Property 'author' on 'Scenario' does not exist") {
            testedSchema.executeBlocking("{scenario{author, content}}")
        }
    }

    @Test
    fun `query with interface`() {
        val map = execute("{randomPerson{name \n age}}")
        map.extract<Map<String, String>>("data/randomPerson") shouldBe
            mapOf(
                "name" to davidFincher.name,
                "age" to davidFincher.age
            )
    }

    @Test
    fun `query with collection elements interface`() {
        val map = execute("{people{name, age}}")
        map.extract<Map<String, String>>("data/people[0]") shouldBe
            mapOf(
                "name" to davidFincher.name,
                "age" to davidFincher.age
            )
    }

    @Test
    fun `query extension property`() {
        val map = execute("{actors{name, age, isOld}}")
        for (i in 0..4) {
            val isOld = map.extract<Boolean>("data/actors[$i]/isOld")
            val age = map.extract<Int>("data/actors[$i]/age")
            isOld shouldBe (age > 500)
        }
    }

    @Test
    fun `query extension property with arguments`() {
        val map = execute("{actors{name, picture(big: true)}}")
        for (i in 0..4) {
            val name = map.extract<String>("data/actors[$i]/name").replace(' ', '_')
            map.extract<String>("data/actors[$i]/picture") shouldBe "http://picture.server/pic/$name?big=true"
        }
    }

    @Test
    fun `query extension property with optional argument`() {
        val map = execute("{actors{name, picture}}")
        for (i in 0..4) {
            val name = map.extract<String>("data/actors[$i]/name").replace(' ', '_')
            map.extract<String>("data/actors[$i]/picture") shouldBe "http://picture.server/pic/$name?big=false"
        }
    }

    @Test
    fun `query extension property with optional annotated argument`() {
        val map = execute("{actors{name, pictureWithArgs}}")
        for (i in 0..4) {
            val name = map.extract<String>("data/actors[$i]/name").replace(' ', '_')
            map.extract<String>("data/actors[$i]/pictureWithArgs") shouldBe "http://picture.server/pic/$name?big=false"
        }
    }

    @Test
    fun `query with mandatory generic input type`() {
        val map = execute("""{actorsByTags(tags: ["1", "2", "3"]){name}}""")
        assertNoErrors(map)
    }

    @Test
    fun `query with optional generic input type`() {
        val map = execute("{actorsByTagsOptional{name}}")
        assertNoErrors(map)
    }

    @Test
    fun `query with generic input typ and default`() {
        val map = execute("{actorsByTagsWithDefault{name}}")
        assertNoErrors(map)
    }

    @Test
    fun `query with nullable generic input type`() {
        val map = execute("{actorsByTagsNullable{name}}")
        assertNoErrors(map)
    }

    @Test
    fun `query with transformed property`() {
        val map = execute("{scenario{id, content(uppercase: false)}}")
        map.extract<String>("data/scenario/content") shouldBe "Very long scenario"

        val map2 = execute("{scenario{id, content(uppercase: true)}}")
        map2.extract<String>("data/scenario/content") shouldBe "VERY LONG SCENARIO"
    }

    @Test
    fun `query with invalid field arguments`() {
        expectRequestError<ValidationException>("Property 'id' on type 'Scenario' has no arguments, found: [uppercase]") {
            testedSchema.executeBlocking("{scenario{id(uppercase: true), content}}")
        }
    }

    @Test
    fun `query with external fragment`() {
        val map = execute("{film{title, ...dir }} fragment dir on Film {director{name, age}}")
        assertNoErrors(map)
        map.extract<String>("data/film/title") shouldBe prestige.title
        map.extract<String>("data/film/director/name") shouldBe prestige.director.name
        map.extract<Int>("data/film/director/age") shouldBe prestige.director.age
    }

    @Test
    fun `query with nested external fragment`() {
        val map = execute(
            """
            {
                film {
                    title
                    ...dir
                }
            }

            fragment dir on Film {
                director {
                    name
                }
                ...dirIntermediate
            }

            fragment dirIntermediate on Film {
                ...dirAge
            }

            fragment dirAge on Film {
                director {
                    age
                }
            }
            """.trimIndent()
        )
        assertNoErrors(map)
        map.extract<String>("data/film/title") shouldBe prestige.title
        map.extract<String>("data/film/director/name") shouldBe prestige.director.name
        map.extract<Int>("data/film/director/age") shouldBe prestige.director.age
    }

    @Test
    fun `query with two nested external fragments`() {
        val map = execute(
            """
            {
                film {
                    ...film_All
                }
            }
            
            fragment film_All on Film {
                ...film_title
                ...film_director
            }
            
            fragment film_title on Film {
                title
            }

            fragment film_director on Film {
                director {
                    name
                }
                ...film_director_age
            }

            fragment film_director_age on Film {
                director {
                    age
                }
            }
            """.trimIndent()
        )
        assertNoErrors(map)
        map.extract<String>("data/film/title") shouldBe prestige.title
        map.extract<String>("data/film/director/name") shouldBe prestige.director.name
        map.extract<Int>("data/film/director/age") shouldBe prestige.director.age
    }

    @Test
    fun `query with two fragments`() {
        val map = execute(
            """
            {
                film {
                    ...film_title
                    ...film_director_All
                }
            }
            
            fragment film_title on Film {
                title
            }

            fragment film_director_All on Film {
                director {
                    name
                    age
                }
            }
            """.trimIndent()
        )
        assertNoErrors(map)
        map.extract<String>("data/film/title") shouldBe prestige.title
        map.extract<String>("data/film/director/name") shouldBe prestige.director.name
        map.extract<Int>("data/film/director/age") shouldBe prestige.director.age
    }

    @Test
    fun `query with two inline fragments`() {
        val map = execute(
            """
            {
                film {
                    ...on Film { title }
                    ...on Film { director { age name } }
                }
            }
            """.trimIndent()
        )
        assertNoErrors(map)
        map.extract<String>("data/film/title") shouldBe prestige.title
        map.extract<String>("data/film/director/name") shouldBe prestige.director.name
        map.extract<Int>("data/film/director/age") shouldBe prestige.director.age
    }

    @Test
    fun `query with typename and other property`() {
        data class FooChild(val barChild: String)
        data class Foo(val bar: String, val child: FooChild?)

        val schema = KGraphQL.schema {
            query("foo") {
                resolver { -> Foo("bar", FooChild("barChild")) }
            }
        }

        schema.executeBlocking(
            """
            {
                foo { bar __typename }
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"foo":{"bar":"bar","__typename":"Foo"}}}
        """.trimIndent()

        schema.executeBlocking(
            """
            {
                foo { __typename bar }
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"foo":{"__typename":"Foo","bar":"bar"}}}
        """.trimIndent()

        schema.executeBlocking(
            """
            {
                foo { child { __typename barChild } }
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"foo":{"child":{"__typename":"FooChild","barChild":"barChild"}}}}
        """.trimIndent()

        schema.executeBlocking(
            """
            {
                foo { child { barChild __typename } }
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"foo":{"child":{"barChild":"barChild","__typename":"FooChild"}}}}
        """.trimIndent()

        schema.executeBlocking(
            """
            {
                foo { __typename child { barChild __typename } }
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"foo":{"__typename":"Foo","child":{"barChild":"barChild","__typename":"FooChild"}}}}
        """.trimIndent()

        schema.executeBlocking(
            """
            {
                foo { child { barChild } __typename }
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"foo":{"child":{"barChild":"barChild"},"__typename":"Foo"}}}
        """.trimIndent()
    }

    @Test
    fun `query with mixed selections`() {
        val map = execute(
            """
            {
                film {
                    __typename
                    ...film_title
                    ...on Film { director { age name } }
                }
            }
            
            fragment film_title on Film {
                title
            }
            """.trimIndent()
        )
        assertNoErrors(map)
        map.extract<String>("data/film/title") shouldBe prestige.title
        map.extract<String>("data/film/director/name") shouldBe prestige.director.name
        map.extract<Int>("data/film/director/age") shouldBe prestige.director.age
        map.extract<String>("data/film/__typename") shouldBe "Film"
    }

    @Test
    fun `query with missing fragment type`() {
        expectRequestError<ValidationException>("Unknown type 'MissingType' in type condition on fragment") {
            testedSchema.executeBlocking(
                """
                {
                    film {
                        ...on MissingType {
                            title
                        }
                    }
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `query with missing named fragment type`() {
        expectRequestError<ValidationException>("Fragment 'film_title' not found") {
            testedSchema.executeBlocking(
                """
                {
                    film {
                        ...film_title
                    }
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `query with missing selection set`() {
        expectRequestError<ValidationException>("Missing selection set on property 'film' of type 'Film'") {
            testedSchema.executeBlocking("{film}")
        }
    }

    data class SampleNode(val id: Int, val name: String, val fields: List<String>? = null)

    @Test
    fun `access to execution node`() {
        val result = defaultSchema {
            query("root") {
                resolver { node: Execution.Node ->
                    SampleNode(0, "Root", fields = node.getFields())
                }
            }
            type<SampleNode> {
                property("children") {
                    resolver { _, amount: Int, node: Execution.Node ->
                        (1..amount).map {
                            SampleNode(it, "${node.aliasOrKey}-Testing", fields = node.getFields())
                        }
                    }
                }
            }
        }.executeBlocking(
            """
            {
                root {
                    fields
                    kids: children(amount: 1) {
                        id
                        fields
                        ...aFragment
                    }
                }
            }
            fragment aFragment on SampleNode {
                id
                name
            }
            """.trimIndent()
        ).deserialize()

        result.extract<List<String>>("data/root/fields") shouldBe listOf("fields")
        result.extract<Int>("data/root/kids[0]/id") shouldBe 1
        result.extract<String>("data/root/kids[0]/name") shouldBe "kids-Testing"
        result.extract<List<String>>("data/root/kids[0]/fields") shouldBe listOf("id", "fields", "name")
    }

    // cf. https://spec.graphql.org/October2021/#example-77852
    @Test
    fun `multiple selection sets for the same object should be merged on top level`() {
        data class Person(val firstName: String, val lastName: String)

        val response = defaultSchema {
            query("me") {
                resolver { -> Person("John", "Doe") }
            }
        }.executeBlocking(
            """
            {
                me {
                    firstName
                }
                me {
                    lastName
                }
            }
        """
        )
        response shouldBe """
            {"data":{"me":{"firstName":"John","lastName":"Doe"}}}
        """.trimIndent()
    }

    @Test
    fun `multiple selection sets for the same object should be merged on object level`() {
        data class Person(val firstName: String, val lastName: String)
        data class PersonWrapper(val person: Person)

        val response = defaultSchema {
            query("me") {
                resolver { -> PersonWrapper(Person("John", "Doe")) }
            }
        }.executeBlocking(
            """
            {
                me {
                    person {
                        firstName
                    }
                    person {
                        lastName
                    }
                }
            }
        """
        )
        response shouldBe """
            {"data":{"me":{"person":{"firstName":"John","lastName":"Doe"}}}}
        """.trimIndent()
    }

    @Test
    fun `multiple complex selection sets for the same object should be merged on top level`() {
        data class Address(val zipCode: String, val street: String, val city: String, val country: String)
        data class Person(val firstName: String, val lastName: String, val birthDate: String, val address: Address)

        val response = defaultSchema {
            query("me") {
                resolver { ->
                    Person(
                        "John",
                        "Doe",
                        "1.1.1970",
                        Address("12345", "Main Street", "SomeCity", "SomeCountry")
                    )
                }
            }
        }.executeBlocking(
            """
            {
                me {
                    firstName
                    birthDate
                    address {
                        city
                    }
                }
                me {
                    lastName
                    address {
                        ...CityFragment
                        street
                    }
                }
                anotherMe: me {
                    firstName
                    lastName
                }
            }
            
            fragment CityFragment on Address {
                zipCode
                city
            }
        """
        )
        response shouldBe """
            {"data":{"me":{"firstName":"John","birthDate":"1.1.1970","address":{"city":"SomeCity","zipCode":"12345","street":"Main Street"},"lastName":"Doe"},"anotherMe":{"firstName":"John","lastName":"Doe"}}}
        """.trimIndent()
    }

    @Suppress("unused")
    sealed class UnionExample {
        class UnionMember1(val one: String) : UnionExample()
        class UnionMember2(val two: String) : UnionExample()
    }

    @Test
    fun `nodes should have correct path`() {
        val schema = defaultSchema {
            configure {
                useDefaultPrettyPrinter = true
            }
            val favouriteID = unionType("Favourite") {
                type<Actor>()
                type<Scenario>()
                type<Director>()
            }
            query("film") {
                description = "mock film"
                resolver { -> prestige }
            }
            type<Film> {
                property("fullPath") {
                    resolver { _: Film, node: Execution.Node -> node.fullPath.joinToString(".") }
                }
            }
            type<Director> {
                property("fullPath") {
                    resolver { _: Director, node: Execution.Node -> node.fullPath.joinToString(".") }
                }
            }
            type<Scenario> {
                property("fullPath") {
                    resolver { _: Scenario, node: Execution.Node -> node.fullPath.joinToString(".") }
                }
            }
            type<Actor> {
                property("fullPath") {
                    resolver { _: Actor, node: Execution.Node -> node.fullPath.joinToString(".") }
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
                property("sealed") {
                    resolver { actor ->
                        when (actor) {
                            bradPitt -> listOf(UnionExample.UnionMember1("one"))
                            tomHardy -> listOf(UnionExample.UnionMember1("one"), UnionExample.UnionMember2("two"))
                            morganFreeman -> listOf(UnionExample.UnionMember1("one"))
                            rickyGervais -> null
                            else -> listOf(UnionExample.UnionMember2("two"))
                        }
                    }
                }
            }
            type<UnionExample.UnionMember1> {
                property("fullPath") {
                    resolver { _: UnionExample.UnionMember1, node: Execution.Node -> node.fullPath.joinToString(".") }
                }
            }
            type<UnionExample.UnionMember2> {
                property("fullPath") {
                    resolver { _: UnionExample.UnionMember2, node: Execution.Node -> node.fullPath.joinToString(".") }
                }
            }
            query("directors") {
                resolver { -> listOf(christopherNolan, davidFincher, martinScorsese) }
            }
            query("actors") {
                resolver { -> (0..1000).map { Actor(name = "Actor$it", age = it) } }
            }
        }

        // Nested structures with lists and unions from DSL
        schema.executeBlocking(
            """
            {
                film {
                    fullPath
                    ...on Film {
                        director {
                            fullPath
                            favActors {
                                fullPath
                                favourite {
                                    ...ActorPath
                                    ...on Director {
                                        fullPath
                                        favActors {
                                            fullPath
                                            favourite {
                                                ...ActorPath
                                                ...on Director {
                                                    fullPath
                                                    favActors {
                                                        ...ActorPath
                                                    }
                                                }
                                                ...ScenarioPath
                                            }
                                        }
                                    }
                                    ...ScenarioPath
                                }
                            }
                        }
                    }
                }
            }
            
            fragment ActorPath on Actor {
                fullPath
            }
            
            fragment ScenarioPath on Scenario {
                fullPath
            }
            """.trimIndent()
        ) shouldBe """
            {
              "data" : {
                "film" : {
                  "fullPath" : "film.fullPath",
                  "director" : {
                    "fullPath" : "film.director.fullPath",
                    "favActors" : [ {
                      "fullPath" : "film.director.favActors.0.fullPath",
                      "favourite" : {
                        "fullPath" : "film.director.favActors.0.favourite.fullPath",
                        "favActors" : [ {
                          "fullPath" : "film.director.favActors.0.favourite.favActors.0.fullPath",
                          "favourite" : {
                            "fullPath" : "film.director.favActors.0.favourite.favActors.0.favourite.fullPath",
                            "favActors" : [ {
                              "fullPath" : "film.director.favActors.0.favourite.favActors.0.favourite.favActors.0.fullPath"
                            }, {
                              "fullPath" : "film.director.favActors.0.favourite.favActors.0.favourite.favActors.1.fullPath"
                            } ]
                          }
                        }, {
                          "fullPath" : "film.director.favActors.0.favourite.favActors.1.fullPath",
                          "favourite" : {
                            "fullPath" : "film.director.favActors.0.favourite.favActors.1.favourite.fullPath"
                          }
                        } ]
                      }
                    }, {
                      "fullPath" : "film.director.favActors.1.fullPath",
                      "favourite" : {
                        "fullPath" : "film.director.favActors.1.favourite.fullPath"
                      }
                    } ]
                  }
                }
              }
            }
            """.trimIndent()

        // Aliased nodes
        schema.executeBlocking("{ directors { path: fullPath fullPath } }") shouldBe """
            {
              "data" : {
                "directors" : [ {
                  "path" : "directors.0.path",
                  "fullPath" : "directors.0.fullPath"
                }, {
                  "path" : "directors.1.path",
                  "fullPath" : "directors.1.fullPath"
                }, {
                  "path" : "directors.2.path",
                  "fullPath" : "directors.2.fullPath"
                } ]
              }
            }
            """.trimIndent()

        // Union from sealed class
        schema.executeBlocking(
            """
            {
              directors {
                favActors {
                  sealed {
                    ...on UnionMember1 { fullPath }
                    ...on UnionMember2 { fullPath }
                  }
                }
              }
            }
        """.trimIndent()
        ) shouldBe """
            {
              "data" : {
                "directors" : [ {
                  "favActors" : [ {
                    "sealed" : [ {
                      "fullPath" : "directors.0.favActors.0.sealed.0.fullPath"
                    }, {
                      "fullPath" : "directors.0.favActors.0.sealed.1.fullPath"
                    } ]
                  }, {
                    "sealed" : [ {
                      "fullPath" : "directors.0.favActors.1.sealed.0.fullPath"
                    } ]
                  } ]
                }, {
                  "favActors" : [ {
                    "sealed" : [ {
                      "fullPath" : "directors.1.favActors.0.sealed.0.fullPath"
                    } ]
                  }, {
                    "sealed" : [ {
                      "fullPath" : "directors.1.favActors.1.sealed.0.fullPath"
                    } ]
                  }, {
                    "sealed" : [ {
                      "fullPath" : "directors.1.favActors.2.sealed.0.fullPath"
                    } ]
                  } ]
                }, {
                  "favActors" : [ ]
                } ]
              }
            }
            """.trimIndent()

        // Big list
        val bigListResponse = schema.executeBlocking(
            """
            {
              actors {
                age
                fullPath
              }
            }
        """.trimIndent()
        )
        // Every actor has an age according to its list index, and that should match the path
        (0..1000).forEach {
            bigListResponse shouldContainOnlyOnce
"""
      "age" : $it,
      "fullPath" : "actors.$it.fullPath"
"""
        }
    }

    // https://spec.graphql.org/September2025/#example-072c4
    @Test
    fun partialResponseNullableField() {
        data class Hero(val id: ID, val friends: List<Hero?> = emptyList())

        val schema = KGraphQL.schema {
            configure {
                useDefaultPrettyPrinter = true
            }
            query("hero") {
                resolver { -> Hero(ID("1"), listOf(Hero(ID("1000")), Hero(ID("1002")), Hero(ID("1003")))) }
            }
            type<Hero> {
                property<String?>("name") {
                    resolver { hero ->
                        when (hero.id.value) {
                            "1" -> "R2-D2"
                            "1000" -> "Luke Skywalker"
                            "1003" -> "Leia Organa"
                            else -> throw Exception("Name for character with ID ${hero.id} could not be fetched.")
                        }
                    }
                }
            }
        }

        schema.printSchema() shouldBe """
            type Hero {
              friends: [Hero]!
              id: ID!
              name: String
            }

            type Query {
              hero: Hero!
            }

        """.trimIndent()

        schema.executeBlocking("""
            {
              hero {
                name
                heroFriends: friends {
                  id
                  name
                }
              }
            }
        """.trimIndent()) shouldBe """
            {
              "errors" : [ {
                "message" : "Name for character with ID ID(value=1002) could not be fetched.",
                "locations" : [ {
                  "line" : 6,
                  "column" : 7
                } ],
                "path" : [ "hero", "heroFriends", 1, "name" ],
                "extensions" : {
                  "type" : "INTERNAL_SERVER_ERROR"
                }
              } ],
              "data" : {
                "hero" : {
                  "name" : "R2-D2",
                  "heroFriends" : [ {
                    "id" : "1000",
                    "name" : "Luke Skywalker"
                  }, {
                    "id" : "1002",
                    "name" : null
                  }, {
                    "id" : "1003",
                    "name" : "Leia Organa"
                  } ]
                }
              }
            }
        """.trimIndent()
    }

    // https://spec.graphql.org/September2025/#example-c18ef
    @Test
    fun partialResponseNonNullableField() {
        data class Hero(val id: ID, val friends: List<Hero?> = emptyList())

        val schema = KGraphQL.schema {
            configure {
                useDefaultPrettyPrinter = true
            }
            query("hero") {
                resolver { -> Hero(ID("1"), listOf(Hero(ID("1000")), Hero(ID("1002")), Hero(ID("1003")))) }
            }
            type<Hero> {
                property("name") {
                    resolver { hero ->
                        when (hero.id.value) {
                            "1" -> "R2-D2"
                            "1000" -> "Luke Skywalker"
                            "1003" -> "Leia Organa"
                            else -> throw Exception("Name for character with ID ${hero.id} could not be fetched.")
                        }
                    }
                }
            }
        }

        schema.printSchema() shouldBe """
            type Hero {
              friends: [Hero]!
              id: ID!
              name: String!
            }

            type Query {
              hero: Hero!
            }

        """.trimIndent()

        schema.executeBlocking("""
            {
              hero {
                name
                heroFriends: friends {
                  id
                  name
                }
              }
            }
        """.trimIndent()) shouldBe """
            {
              "errors" : [ {
                "message" : "Name for character with ID ID(value=1002) could not be fetched.",
                "locations" : [ {
                  "line" : 6,
                  "column" : 7
                } ],
                "path" : [ "hero", "heroFriends", 1, "name" ],
                "extensions" : {
                  "type" : "INTERNAL_SERVER_ERROR"
                }
              } ],
              "data" : {
                "hero" : {
                  "name" : "R2-D2",
                  "heroFriends" : [ {
                    "id" : "1000",
                    "name" : "Luke Skywalker"
                  }, null, {
                    "id" : "1003",
                    "name" : "Leia Organa"
                  } ]
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun partialResponseNullableList() {
        data class Hero(val id: ID, val friends: List<Hero?> = emptyList())

        val schema = KGraphQL.schema {
            configure {
                useDefaultPrettyPrinter = true
            }
            query("heroes") {
                resolver<List<Hero>?> { throw IllegalStateException("There are no heroes anymore") }
            }
        }

        schema.printSchema() shouldBe """
            type Hero {
              friends: [Hero]!
              id: ID!
            }

            type Query {
              heroes: [Hero!]
            }

        """.trimIndent()

        schema.executeBlocking("""
            {
              heroes {
                id
              }
            }
        """.trimIndent()) shouldBe """
            {
              "errors" : [ {
                "message" : "There are no heroes anymore",
                "locations" : [ {
                  "line" : 2,
                  "column" : 3
                } ],
                "path" : [ "heroes" ],
                "extensions" : {
                  "type" : "INTERNAL_SERVER_ERROR"
                }
              } ],
              "data" : {
                "heroes" : null
              }
            }
        """.trimIndent()
    }

    @Test
    fun `errors during object field execution should not stop other fields`() {
        data class HighlanderMovie(val highlander1: String?)
        data class Person(val firstName: String, val lastName: String)

        val schema = KGraphQL.schema {
            query("movie") {
                resolver { -> HighlanderMovie("Connor MacLeod") }
            }
            type<HighlanderMovie> {
                property<String?>("highlander2") {
                    resolver { throw Exception("There can only be one!") }
                }
                property("director") {
                    resolver { Person("Russel", "Mulcahy") }
                }
            }
        }
        schema.executeBlocking("""
            { movie { highlander1 highlander2 director { firstName lastName } } }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"There can only be one!","locations":[{"line":1,"column":23}],"path":["movie","highlander2"],"extensions":{"type":"INTERNAL_SERVER_ERROR"}}],"data":{"movie":{"highlander1":"Connor MacLeod","highlander2":null,"director":{"firstName":"Russel","lastName":"Mulcahy"}}}}
        """.trimIndent()

        schema.executeBlocking("""
            {
              movie { ...MovieFragment }
            }
            
            fragment MovieFragment on HighlanderMovie {
              highlander1
              highlander2
              director { firstName lastName }
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"There can only be one!","locations":[{"line":7,"column":3}],"path":["movie","highlander2"],"extensions":{"type":"INTERNAL_SERVER_ERROR"}}],"data":{"movie":{"highlander1":"Connor MacLeod","highlander2":null,"director":{"firstName":"Russel","lastName":"Mulcahy"}}}}
        """.trimIndent()
    }

    @Test
    fun `invalid collection values should result in an error`() {
        val schema = KGraphQL.schema {
            query("invalidList") {
                // Schema defines a String property but resolver actually returns a List<String>
                resolver<List<String>> { listOf("foo", "bar") }.returns<String>()
            }
        }

        schema.printSchema() shouldBe """
            type Query {
              invalidList: String!
            }
            
        """.trimIndent()

        expectExecutionError<ExecutionException>("Invalid collection value for non-collection property 'invalidList'") {
            schema.executeBlocking("{ invalidList }")
        }
    }

    @Test
    fun `resolvers should be able to throw custom request errors`() {
        data class Item(val data: String)
        class ForbiddenError(node: Execution.Node, message: String) :
            RequestError(message, node = node.selectionNode, extensions = mapOf("type" to "FORBIDDEN"))

        val schema = KGraphQL.schema {
            query("items") {
                resolver<Item, Execution.Node> { node: Execution.Node ->
                    throw ForbiddenError(node, "Not allowed")
                }
            }
        }
        schema.executeBlocking("{ items { data }}") shouldBe """
            {"errors":[{"message":"Not allowed","locations":[{"line":1,"column":3}],"extensions":{"type":"FORBIDDEN"}}]}
        """.trimIndent()
    }

    @Test
    fun `resolvers should be able to return data and raise execution errors`() {
        data class Item(val data: String)
        class MissingItemError(message: String, node: Execution.Node) :
            ExecutionError(message, node, extensions = mapOf("type" to "NOT_FOUND", "reason" to "Item is missing"))

        val schema = KGraphQL.schema {
            query("items") {
                resolver { node: Execution.Node, ctx: Context ->
                    ctx.raiseError(MissingItemError("Cannot get item 'missing'", node))
                    listOf(Item("Existing 1"), Item("Existing 2"))
                }
            }
        }
        schema.executeBlocking("{ items { data }}") shouldBe """
            {"errors":[{"message":"Cannot get item 'missing'","locations":[{"line":1,"column":3}],"path":["items"],"extensions":{"type":"NOT_FOUND","reason":"Item is missing"}}],"data":{"items":[{"data":"Existing 1"},{"data":"Existing 2"}]}}
        """.trimIndent()
    }
}
