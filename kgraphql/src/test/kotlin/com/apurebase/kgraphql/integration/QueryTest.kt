package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.assertNoErrors
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.helpers.getFields
import com.apurebase.kgraphql.schema.execution.Execution
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
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
        val exception = shouldThrowExactly<ValidationException> {
            execute("{film{title, director{name, favDish}}}")
        }
        exception shouldHaveMessage "Property 'favDish' on 'Director' does not exist"
        exception.extensions shouldBe mapOf(
            "type" to "GRAPHQL_VALIDATION_FAILED"
        )
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
        val exception = shouldThrowExactly<ValidationException> {
            execute("{scenario{author, content}}")
        }
        exception shouldHaveMessage "Property 'author' on 'Scenario' does not exist"
        exception.extensions shouldBe mapOf(
            "type" to "GRAPHQL_VALIDATION_FAILED"
        )
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
        val exception = shouldThrowExactly<ValidationException> {
            execute("{scenario{id(uppercase: true), content}}")
        }
        exception shouldHaveMessage "Property 'id' on type 'Scenario' has no arguments, found: [uppercase]"
        exception.extensions shouldBe mapOf(
            "type" to "GRAPHQL_VALIDATION_FAILED"
        )
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
        val exception = shouldThrowExactly<ValidationException> {
            execute(
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
        exception shouldHaveMessage "Unknown type 'MissingType' in type condition on fragment"
        exception.extensions shouldBe mapOf(
            "type" to "GRAPHQL_VALIDATION_FAILED"
        )
    }

    @Test
    fun `query with missing named fragment type`() {
        val exception = shouldThrowExactly<ValidationException> {
            execute(
                """
                {
                    film {
                        ...film_title
                    }
                }
                """.trimIndent()
            )
        }
        exception shouldHaveMessage "Fragment 'film_title' not found"
        exception.extensions shouldBe mapOf(
            "type" to "GRAPHQL_VALIDATION_FAILED"
        )
    }

    @Test
    fun `query with missing selection set`() {
        val exception = shouldThrowExactly<ValidationException> {
            execute("{film}")
        }
        exception shouldHaveMessage "Missing selection set on property 'film' of type 'Film'"
        exception.extensions shouldBe mapOf(
            "type" to "GRAPHQL_VALIDATION_FAILED"
        )
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
                resolver { -> Person("John", "Doe", "1.1.1970", Address("12345", "Main Street", "SomeCity", "SomeCountry")) }
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
}
