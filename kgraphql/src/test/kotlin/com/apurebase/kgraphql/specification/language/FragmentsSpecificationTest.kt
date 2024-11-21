package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.Actor
import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.assertNoErrors
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.executeEqualQueries
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.integration.BaseSchemaTest
import com.apurebase.kgraphql.request.Introspection
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@Specification("2.8 Fragments")
class FragmentsSpecificationTest {

    val age = 232

    private val actorName = "Boguś Linda"

    val id = "BLinda"

    data class ActorWrapper(val id: String, val actualActor: Actor)

    val schema = defaultSchema {
        query("actor") {
            resolver { -> ActorWrapper(id, Actor(actorName, age)) }
        }
    }

    private val baseTestSchema = object : BaseSchemaTest() {}

    @Test
    fun `fragment's fields are added to the query at the same level as the fragment invocation`() {
        val expected = mapOf(
            "data" to mapOf(
                "actor" to mapOf(
                    "id" to id,
                    "actualActor" to mapOf("name" to actorName, "age" to age)
                )
            )
        )
        executeEqualQueries(
            schema,
            expected,
            "{actor{id, actualActor{name, age}}}",
            "{actor{ ...actWrapper}} fragment actWrapper on ActorWrapper {id, actualActor{ name, age }}"
        )
    }

    @Test
    fun `fragments can be nested`() {
        val expected = mapOf(
            "data" to mapOf(
                "actor" to mapOf(
                    "id" to id,
                    "actualActor" to mapOf("name" to actorName, "age" to age)
                )
            )
        )
        executeEqualQueries(
            schema,
            expected,
            "{actor{id, actualActor{name, age}}}",
            "{actor{ ...actWrapper}} fragment act on Actor{name, age} fragment actWrapper on ActorWrapper {id, actualActor{ ...act }}"
        )
    }

    @Test
    fun `Inline fragments may also be used to apply a directive to a group of fields`() {
        val response = deserialize(
            schema.executeBlocking(
                "query (\$expandedInfo : Boolean!){actor{actualActor{name ... @include(if: \$expandedInfo){ age }}}}",
                "{\"expandedInfo\":false}"
            )
        )
        assertNoErrors(response)
        assertThat(response.extract("data/actor/actualActor/name"), equalTo("Boguś Linda"))
        assertThrows<IllegalArgumentException> { response.extract("data/actor/actualActor/age") }
    }

    @Test
    fun `query with inline fragment with type condition`() {
        val map = baseTestSchema.execute("{people{name, age, ... on Actor {isOld} ... on Director {favActors{name}}}}")
        assertNoErrors(map)
        for (i in map.extract<List<*>>("data/people").indices) {
            val name = map.extract<String>("data/people[$i]/name")
            when (name) {
                "David Fincher" /* director */ -> {
                    assertThat(map.extract<List<*>>("data/people[$i]/favActors"), notNullValue())
                    assertThrows<IllegalArgumentException> { map.extract("data/people[$i]/isOld") }
                }

                "Brad Pitt" /* actor */ -> {
                    assertThat(map.extract<Boolean>("data/people[$i]/isOld"), notNullValue())
                    assertThrows<IllegalArgumentException> { map.extract("data/people[$i]/favActors") }
                }
            }
        }
    }

    @Test
    fun `query with external fragment with type condition`() {
        val map =
            baseTestSchema.execute("{people{name, age ...act ...dir}} fragment act on Actor {isOld} fragment dir on Director {favActors{name}}")
        assertNoErrors(map)
        for (i in map.extract<List<*>>("data/people").indices) {
            val name = map.extract<String>("data/people[$i]/name")
            when (name) {
                "David Fincher" /* director */ -> {
                    assertThat(map.extract<List<*>>("data/people[$i]/favActors"), notNullValue())
                    assertThrows<IllegalArgumentException> { map.extract("data/people[$i]/isOld") }
                }

                "Brad Pitt" /* actor */ -> {
                    assertThat(map.extract<Boolean>("data/people[$i]/isOld"), notNullValue())
                    assertThrows<IllegalArgumentException> { map.extract("data/people[$i]/favActors") }
                }
            }
        }
    }

    @Test
    fun `multiple nested fragments are handled`() {
        val map = baseTestSchema.execute(Introspection.query())
        val fields = map.extract<List<Map<String, *>>>("data/__schema/types[0]/fields")

        fields.forEach { field ->
            assertThat(field["name"], notNullValue())
        }
    }

    @Test
    fun `queries with recursive fragments are denied`() {
        invoking {
            baseTestSchema.execute(
                """
            query IntrospectionQuery {
                __schema {
                    types {
                        ...FullType
                    }
                }
            }

            fragment FullType on __Type {
                fields(includeDeprecated: true) {
                    name
                    type {
                        ...FullType
                    }
                }
            }
        """
            )
        } shouldThrow GraphQLError::class withMessage "Fragment spread circular references are not allowed"
    }

    @Test
    fun `queries with duplicated fragments are denied`() {
        invoking {
            baseTestSchema.execute(
                """
            {
                film {
                    ...film_title
                }
            }
            
            fragment film_title on Film {
                title
            }
            
            fragment film_title on Film {
                director {
                    name
                    age
                }
            }
        """
            )
        } shouldThrow GraphQLError::class withMessage "There can be only one fragment named film_title."
    }

    sealed class TopUnion(val field: String) {
        class Union1(val names: List<String>) : TopUnion("union1")
        class Union2(val numbers: List<Int>) : TopUnion("union2")
    }

    // https://github.com/aPureBase/KGraphQL/issues/141
    @Test
    fun `fragments on union types should work`() {
        KGraphQL.schema {
            unionType<TopUnion>()

            query("unions") {
                resolver { isOne: Boolean ->
                    if (isOne) {
                        TopUnion.Union1(listOf("name1", "name2"))
                    } else {
                        TopUnion.Union2(listOf(1, 2))
                    }
                }
            }
        }.executeBlocking(
            """
            {
                unions(isOne: true) {
                    ...abc
                }
            }
            fragment abc on TopUnion {
                ... on Union1 { names }
                ... on Union2 { numbers }
            }
            """.trimIndent()
        )
            .deserialize()
            .extract<List<String>>("data/unions/names") shouldBeEqualTo listOf("name1", "name2")
    }

    data class Outer(val inner1: Inner, val inner2: Inner)
    data class Inner(val name: String)

    private val testedSchema = defaultSchema {
        query("outer") {
            resolver { ->
                Outer(Inner("test1"), Inner("test2"))
            }
        }
        type<Inner> {
            property("testProperty1") {
                resolver { "${it.name}.testProperty1" }
            }
            property("testProperty2") {
                resolver { "${it.name}.testProperty2" }
            }
        }
    }

    // https://github.com/aPureBase/KGraphQL/issues/197
    @Test
    fun `executor should merge fragment declaration and field declaration`() {
        val response = testedSchema.executeBlocking(
            """
            { 
                outer { 
                    ...TestFragment
                    inner1 { testProperty1 } 
                    inner2 { testProperty2 } 
                } 
            } 
            fragment TestFragment on Outer {
                inner1 { name, testProperty2 }
                inner2 { name, testProperty1 }
            }
        """.trimIndent()
        )

        val deserialized = deserialize(response)
        assertThat(
            deserialized, equalTo(
                mapOf(
                    "data" to mapOf(
                        "outer" to mapOf(
                            "inner1" to mapOf(
                                "name" to "test1",
                                "testProperty2" to "test1.testProperty2",
                                "testProperty1" to "test1.testProperty1",
                            ),
                            "inner2" to mapOf(
                                "name" to "test2",
                                "testProperty1" to "test2.testProperty1",
                                "testProperty2" to "test2.testProperty2",
                            ),
                        )
                    )
                )
            )
        )
    }

    // https://github.com/aPureBase/KGraphQL/issues/197
    @Test
    fun `executor should merge several fragment declarations and field declaration`() {
        val response = testedSchema.executeBlocking(//language=graphql
            """
            { 
                outer { 
                    ...TestFragment1
                    ...TestFragment2
                    inner2 {
                        testProperty2
                    }
                } 
            } 
            fragment TestFragment1 on Outer {
                inner1 { name, testProperty2 }
            }
            fragment TestFragment2 on Outer {
                inner2 { name, testProperty1 }
            }
        """.trimIndent()
        )

        val deserialized = deserialize(response)
        assertThat(
            deserialized, equalTo(
                mapOf(
                    "data" to mapOf(
                        "outer" to mapOf(
                            "inner1" to mapOf(
                                "name" to "test1",
                                "testProperty2" to "test1.testProperty2",
                            ),
                            "inner2" to mapOf(
                                "name" to "test2",
                                "testProperty1" to "test2.testProperty1",
                                "testProperty2" to "test2.testProperty2",
                            ),
                        )
                    )
                )
            )
        )
    }
}
