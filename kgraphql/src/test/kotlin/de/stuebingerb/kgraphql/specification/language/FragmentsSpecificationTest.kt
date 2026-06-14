package de.stuebingerb.kgraphql.specification.language

import de.stuebingerb.kgraphql.Actor
import de.stuebingerb.kgraphql.KGraphQL
import de.stuebingerb.kgraphql.Specification
import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.assertNoErrors
import de.stuebingerb.kgraphql.defaultSchema
import de.stuebingerb.kgraphql.deserialize
import de.stuebingerb.kgraphql.executeEqualQueries
import de.stuebingerb.kgraphql.expectRequestError
import de.stuebingerb.kgraphql.extract
import de.stuebingerb.kgraphql.integration.BaseSchemaTest
import de.stuebingerb.kgraphql.request.Introspection
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

@Specification("2.8 Fragments")
class FragmentsSpecificationTest : BaseSchemaTest() {

    val age = 232

    private val actorName = "Boguś Linda"

    val id = "BLinda"

    data class ActorWrapper(val id: String, val actualActor: Actor)

    val schema = defaultSchema {
        query("actor") {
            resolver { -> ActorWrapper(id, Actor(actorName, age)) }
        }
    }

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
        val map =
            testedSchema.executeBlocking("{people{name, age, ... on Actor {isOld} ... on Director {favActors{name}}}}")
                .deserialize()
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
            testedSchema.executeBlocking("{people{name, age ...act ...dir}} fragment act on Actor {isOld} fragment dir on Director {favActors{name}}")
                .deserialize()
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
    fun `multiple nested fragments should be handled`() {
        val map = testedSchema.executeBlocking(Introspection.query()).deserialize()
        val fields = map.extract<List<Map<String, *>>>("data/__schema/types[0]/fields")

        fields.forAll { field ->
            field["name"] shouldNotBe null
        }
    }

    @Test
    fun `queries with recursive fragments should be denied`() {
        expectRequestError<ValidationException>("Fragment spread circular references are not allowed") {
            testedSchema.executeBlocking(
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
    fun `queries with duplicated fragments should be denied`() {
        expectRequestError<ValidationException>("There can be only one fragment named 'film_title'") {
            testedSchema.executeBlocking(
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

    @Test
    fun `queries with unused fragments should be denied`() {
        expectRequestError<ValidationException>("Found unused fragments: [unused_film_title]") {
            testedSchema.executeBlocking(
                """
            {
                film {
                    ...film_title
                }
            }
            
            fragment film_title on Film {
                title
                director {
                    ...director_name
                }
            }
            
            fragment director_name on Director {
                name
            }
            
            fragment unused_film_title on Film {
                director {
                    name
                    age
                }
            }
        """
            )
        }
    }

    @Test
    fun `fragments on enum types should be denied`() {
        testedSchema.executeBlocking("""
            {
                film {
                    ... on FilmType {
                        __typename
                    }
                }
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'FilmType' is ENUM on inline fragment","locations":[{"line":3,"column":9}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()

        testedSchema.executeBlocking("""
            {
                film {
                    ...EnumFragment
                }
            }
            
            fragment EnumFragment on FilmType {
                __typename
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'FilmType' is ENUM on fragment 'EnumFragment'","locations":[{"line":7,"column":1}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()

        testedSchema.executeBlocking("""
            {
                film {
                    type {
                        ... @include(if: true) {
                            __typename
                        }
                    }
                }
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'FilmType' is ENUM on inline fragment","locations":[{"line":4,"column":13}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()

        testedSchema.executeBlocking("""
            {
                film {
                    type {
                        ... {
                            __typename
                        }
                    }
                }
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'FilmType' is ENUM on inline fragment","locations":[{"line":4,"column":13}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()

        testedSchema.executeBlocking("""
            {
                film {
                    ... on FilmType @include(if: true) {
                        __typename
                    }
                }
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'FilmType' is ENUM on inline fragment","locations":[{"line":3,"column":9}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()
    }

    @Test
    fun `fragments on scalar types should be denied`() {
        testedSchema.executeBlocking("""
            {
                film {
                    ... on Int {
                        __typename
                    }
                }
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'Int' is SCALAR on inline fragment","locations":[{"line":3,"column":9}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()

        testedSchema.executeBlocking("""
            {
                film {
                    ...IntFragment
                }
            }
            
            fragment IntFragment on Int {
                __typename
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'Int' is SCALAR on fragment 'IntFragment'","locations":[{"line":7,"column":1}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()

        testedSchema.executeBlocking("""
            {
                film {
                    year {
                        ... @include(if: true) {
                            __typename
                        }
                    }
                }
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'Int' is SCALAR on inline fragment","locations":[{"line":4,"column":13}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()

        testedSchema.executeBlocking("""
            {
                film {
                    year {
                        ... {
                            __typename
                        }
                    }
                }
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'Int' is SCALAR on inline fragment","locations":[{"line":4,"column":13}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()

        testedSchema.executeBlocking("""
            {
                film {
                    ... on Int @include(if: true) {
                        __typename
                    }
                }
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'Int' is SCALAR on inline fragment","locations":[{"line":3,"column":9}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()
    }

    @Test
    fun `fragments on input types should be denied`() {
        testedSchema.executeBlocking(
            """
            {
                film {
                    ... on ActorExplicitInput {
                        __typename
                    }
                }
            }
        """.trimIndent()
        ) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'ActorExplicitInput' is INPUT_OBJECT on inline fragment","locations":[{"line":3,"column":9}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()

        testedSchema.executeBlocking(
            """
            {
                film {
                    ...ActorFragment
                }
            }
            
            fragment ActorFragment on ActorExplicitInput {
                __typename
            }
        """.trimIndent()
        ) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'ActorExplicitInput' is INPUT_OBJECT on fragment 'ActorFragment'","locations":[{"line":7,"column":1}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()

        testedSchema.executeBlocking("""
            {
                film {
                    ... on ActorExplicitInput @include(if: true) {
                        __typename
                    }
                }
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Fragments can only be specified on object types, interfaces, and unions but 'ActorExplicitInput' is INPUT_OBJECT on inline fragment","locations":[{"line":3,"column":9}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()
    }

    @Test
    fun `fragments on unknown types should be denied`() {
        testedSchema.executeBlocking("""
            {
                film {
                    ... on MissingType {
                        __typename
                    }
                }
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Unknown type 'MissingType' in type condition on inline fragment","locations":[{"line":3,"column":9}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()

        testedSchema.executeBlocking("""
            {
                film {
                    ...invalid
                }
            }
                            
            fragment invalid on UnknownType {
                __typename
            }
        """.trimIndent()) shouldBe """
            {"errors":[{"message":"Unknown type 'UnknownType' in type condition on fragment 'invalid'","locations":[{"line":7,"column":1}],"extensions":{"type":"GRAPHQL_VALIDATION_FAILED"}}]}
        """.trimIndent()
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

    @Test
    fun `query with missing named fragment type should be denied`() {
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
        expectRequestError<ValidationException>("Fragment 'film_title_misspelled' not found") {
            testedSchema.executeBlocking(
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
