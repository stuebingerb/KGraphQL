package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.Actor
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.assertNoErrors
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.executeEqualQueries
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.integration.BaseSchemaTest
import com.apurebase.kgraphql.request.Introspection
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

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
    fun `inline fragments may also be used to apply a directive to a group of fields`() {
        val response = deserialize(
            schema.executeBlocking(
                "query (\$expandedInfo : Boolean!){actor{actualActor{name ... @include(if: \$expandedInfo){ age }}}}",
                "{\"expandedInfo\":false}"
            )
        )
        assertNoErrors(response)
        response.extract<String>("data/actor/actualActor/name") shouldBe "Boguś Linda"
        shouldThrowExactly<IllegalArgumentException> { response.extract("data/actor/actualActor/age") }
    }

    @Test
    fun `query with inline fragment with type condition`() {
        val map = baseTestSchema.execute("{people{name, age, ... on Actor {isOld} ... on Director {favActors{name}}}}")
        assertNoErrors(map)
        for (i in map.extract<List<*>>("data/people").indices) {
            val name = map.extract<String>("data/people[$i]/name")
            when (name) {
                "David Fincher" /* director */ -> {
                    map.extract<List<*>>("data/people[$i]/favActors") shouldNotBe null
                    shouldThrowExactly<IllegalArgumentException> { map.extract("data/people[$i]/isOld") }
                }

                "Brad Pitt" /* actor */ -> {
                    map.extract<Boolean>("data/people[$i]/isOld") shouldNotBe null
                    shouldThrowExactly<IllegalArgumentException> { map.extract("data/people[$i]/favActors") }
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
                    map.extract<List<*>>("data/people[$i]/favActors") shouldNotBe null
                    shouldThrowExactly<IllegalArgumentException> { map.extract("data/people[$i]/isOld") }
                }

                "Brad Pitt" /* actor */ -> {
                    map.extract<Boolean>("data/people[$i]/isOld") shouldNotBe null
                    shouldThrowExactly<IllegalArgumentException> { map.extract("data/people[$i]/favActors") }
                }
            }
        }
    }

    @Test
    fun `multiple nested fragments are handled`() {
        val map = baseTestSchema.execute(Introspection.query())
        val fields = map.extract<List<Map<String, *>>>("data/__schema/types[0]/fields")

        fields.forAll { field ->
            field["name"] shouldNotBe null
        }
    }

    @Test
    fun `queries with recursive fragments are denied`() {
        expect<ValidationException>("Fragment spread circular references are not allowed") {
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
        }
    }

    @Test
    fun `queries with duplicated fragments are denied`() {
        expect<ValidationException>("There can be only one fragment named film_title.") {
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
        }
    }

    sealed class TopUnion(val field: String) {
        class Union1(val names: List<String>) : TopUnion("union1")
        class Union2(val numbers: List<Int>, val booleans: List<Boolean>) : TopUnion("union2")
    }

    // https://github.com/aPureBase/KGraphQL/issues/141
    // https://github.com/stuebingerb/KGraphQL/issues/130
    @Test
    fun `fragments on union types should work`() {
        val schema = KGraphQL.schema {
            unionType<TopUnion>()

            query("unions") {
                resolver { isOne: Boolean ->
                    if (isOne) {
                        TopUnion.Union1(listOf("name1", "name2"))
                    } else {
                        TopUnion.Union2(listOf(1, 2), listOf(true, false))
                    }
                }
            }
        }

        val nameResult = schema.executeBlocking(
            """
            {
                unions(isOne: true) {
                    ...abc
                }
            }
            fragment abc on TopUnion {
                ... on Union1 { names }
                ... on Union2 { numbers }
                ... on Union2 { booleans }
            }
            """.trimIndent()
        ).deserialize()
        nameResult.extract<List<String>>("data/unions/names") shouldBe listOf("name1", "name2")

        val numberResult = schema.executeBlocking(
            """
            {
                unions(isOne: false) {
                    ...abc
                }
            }
            fragment abc on TopUnion {
                ... on Union1 { names }
                ... on Union2 { numbers }
                ... on Union2 { booleans }
            }
            """.trimIndent()
        ).deserialize()
        numberResult.extract<List<Int>>("data/unions/numbers") shouldBe listOf(1, 2)
        numberResult.extract<List<Boolean>>("data/unions/booleans") shouldBe listOf(true, false)
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
        deserialized shouldBe
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
    }

    // https://github.com/aPureBase/KGraphQL/issues/197
    @Test
    fun `executor should merge several fragment declarations and field declaration`() {
        val response = testedSchema.executeBlocking(
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
        deserialized shouldBe
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
    }

    // https://github.com/aPureBase/KGraphQL/issues/189
    @Test
    fun `queries with missing fragments should return proper error message`() {
        expect<ValidationException>("Fragment film_title_misspelled not found") {
            baseTestSchema.execute(
                """
            {
                film {
                    id
                    ...film_title_misspelled
                }
            }
            
            fragment film_title on Film {
                title
            }
        """
            )
        }
    }
}
