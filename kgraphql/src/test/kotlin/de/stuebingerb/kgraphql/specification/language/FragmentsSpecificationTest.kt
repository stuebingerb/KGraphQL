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
import de.stuebingerb.kgraphql.schema.dsl.operations.subscribe
import de.stuebingerb.kgraphql.schema.dsl.operations.unsubscribe
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

@Specification("2.8 Fragments")
class FragmentsSpecificationTest : BaseSchemaTest() {

    private val age = 232
    private val actorName = "Boguś Linda"
    private val id = "BLinda"

    private val schema = defaultSchema {
        query("actor") {
            resolver { -> ActorWrapper(id, Actor(actorName, age)) }
        }
    }

    interface Interface {
        val name: String
    }

    interface Interface2 : Interface {
        val id: Int
    }

    data class Implementation1(override val name: String) : Interface
    data class Implementation2(override val name: String, override val id: Int) : Interface2

    data class ActorWrapper(val id: String, val actualActor: Actor)

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

    @Test
    fun `fragments on incorrect types should be denied`() {
        // Fragment on `Actor` is invalid because we know that `Film` is returned
        expectRequestError<ValidationException>("Invalid type 'Actor' in type condition on inline fragment of type 'Film'; must be one of '[Film]'") {
            testedSchema.executeBlocking(
                """
                {
                    film {
                        ... on Actor {
                            __typename
                        }
                    }
                }
                """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("Invalid type 'Actor' in type condition on fragment 'actorFragment' of type 'Film'; must be one of '[Film]'") {
            testedSchema.executeBlocking(
                """
                {
                    film {
                        ...actorFragment
                    }
                }
                
                fragment actorFragment on Actor {
                    name
                }
                """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("Invalid type 'Actor' in type condition on inline fragment of type 'Film'; must be one of '[Film]'") {
            testedSchema.executeBlocking(
                """
                {
                    film {
                      ...actorInFilmFragment
                    }
                }
                
                fragment actorInFilmFragment on Film {
                  ... on Actor {
                    name
                  }
                }
                """.trimIndent()
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

    @Test
    fun `fragments on impossible union types should be denied`() {
        val schema = KGraphQL.schema {
            unionType<TopUnion>()
            type<Actor>()

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

        expectRequestError<ValidationException>("Invalid type 'Actor' in type condition on inline fragment of type 'TopUnion'; must be one of '[Union1, Union2]'") {
            schema.executeBlocking(
                """
                {
                    unions(isOne: false) {
                        ...abc
                    }
                }
                fragment abc on TopUnion {
                    ... on Union1 { names }
                    ... on Actor { name }
                }
                """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("Invalid type 'Actor' in type condition on inline fragment of type 'TopUnion'; must be one of '[Union1, Union2]'") {
            schema.executeBlocking(
                """
                {
                    unions(isOne: false) {
                        ... on Union1 { names }
                        ... on Actor { name }
                    }
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `fragments on impossible interface types should be denied`() {
        val schema = KGraphQL.schema {
            type<Actor>()
            type<Implementation1>()
            type<Implementation2>()

            query("interfaces") {
                resolver { isOne: Boolean ->
                    if (isOne) {
                        Implementation1("impl1")
                    } else {
                        Implementation2("impl2", 42)
                    }
                }
            }
        }

        expectRequestError<ValidationException>("Invalid type 'Actor' in type condition on inline fragment of type 'Interface'; must be one of '[Implementation1, Implementation2]'") {
            schema.executeBlocking(
                """
                {
                    interfaces(isOne: false) {
                        ...abc
                    }
                }
                fragment abc on Interface {
                    ... on Implementation1 { name }
                    ... on Actor { name }
                }
                """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("Invalid type 'Actor' in type condition on inline fragment of type 'Interface'; must be one of '[Implementation1, Implementation2]'") {
            schema.executeBlocking(
                """
                {
                    interfaces(isOne: false) {
                        ... on Implementation1 { name }
                        ... on Actor { name }
                    }
                }
                """.trimIndent()
            )
        }
    }

    interface Animal {
        val name: String
    }

    interface Mammal : Animal {
        val order: String
    }

    sealed class SeaAnimal : Animal {
        class Dolphin(override val name: String, override val order: String) : SeaAnimal(), Mammal
        class Fish(override val name: String) : SeaAnimal()
    }

    sealed class LandAnimal : Animal {
        class Bear(override val name: String, override val order: String) : LandAnimal(), Mammal
        class Ant(override val name: String) : LandAnimal()
    }

    @Test
    fun `fragments on impossible intersection of types should be denied`() {
        val schema = KGraphQL.schema {
            unionType<LandAnimal>()
            unionType<SeaAnimal>()
            type<Mammal>()
            type<Animal>()

            query("animals") {
                resolver { ->
                    listOf(
                        SeaAnimal.Dolphin("dolphin", "order1"),
                        SeaAnimal.Fish("fish"),
                        LandAnimal.Bear("bear", "order2"),
                        LandAnimal.Ant("ant")
                    )
                }
            }
        }

        expectRequestError<ValidationException>("Invalid type 'Fish' in type condition on inline fragment of type 'Mammal'; must be one of '[Bear, Dolphin]'") {
            schema.executeBlocking(
            """
                {
                    animals {
                        ... on Mammal {
                            order
                            ... on Fish { name }
                        }
                    }
                }
                """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("Invalid type 'Bear' in type condition on inline fragment of type 'SeaAnimal'; must be one of '[Dolphin, Fish]'") {
            schema.executeBlocking(
                """
                {
                    animals {
                        ... on SeaAnimal {
                            ... on Bear { name }
                        }
                    }
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `fragments on possible intersection of types should work`() {
        val schema = KGraphQL.schema {
            unionType<LandAnimal>()
            unionType<SeaAnimal>()
            type<Mammal>()
            type<Animal>()

            query("animals") {
                resolver { ->
                    listOf(
                        SeaAnimal.Dolphin("dolphin", "order1"),
                        SeaAnimal.Fish("fish"),
                        LandAnimal.Bear("bear", "order2"),
                        LandAnimal.Ant("ant")
                    )
                }
            }
        }

        schema.executeBlocking(
            """
            {
                animals {
                    ... on Dolphin { name order }
                    ... on Fish { name }
                    ... on Bear { name order }
                    ... on Ant { name }
                }
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"animals":[{"name":"dolphin","order":"order1"},{"name":"fish"},{"name":"bear","order":"order2"},{"name":"ant"}]}}
        """.trimIndent()

        schema.executeBlocking(
            """
            {
                animals {
                    ... on Animal { name }
                    ... on Mammal { order }
                }
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"animals":[{"name":"dolphin","order":"order1"},{"name":"fish"},{"name":"bear","order":"order2"},{"name":"ant"}]}}
        """.trimIndent()

        schema.executeBlocking(
            """
            {
                animals {
                    ... on Animal {
                        name
                        ... on Mammal { order }
                    }
                }
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"animals":[{"name":"dolphin","order":"order1"},{"name":"fish"},{"name":"bear","order":"order2"},{"name":"ant"}]}}
        """.trimIndent()

        schema.executeBlocking(
            """
            {
                animals {
                    ... on LandAnimal {
                        ... on Bear { name order }
                    }
                }
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"animals":[{},{},{"name":"bear","order":"order2"},{}]}}
        """.trimIndent()

        schema.executeBlocking(
            """
            {
                animals {
                    ... on Mammal {
                        order
                        ... on Animal { name }
                    }
                }
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"animals":[{"order":"order1","name":"dolphin"},{},{"order":"order2","name":"bear"},{}]}}
        """.trimIndent()

        schema.executeBlocking(
            """
            {
                animals {
                    ...animalName
                    ...mammalOrder
                }
            }
            
            fragment animalName on Animal {
                name
            }
            
            fragment mammalOrder on Mammal {
                order
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"animals":[{"name":"dolphin","order":"order1"},{"name":"fish"},{"name":"bear","order":"order2"},{"name":"ant"}]}}
        """.trimIndent()
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
                        ... @include(if: false) {
                            testProperty3
                        }
                    }
                    ... @include(if: true) {
                        inner1 {
                            testProperty3
                        }
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
                            "testProperty3" to "test1.testProperty3",
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

    // See https://spec.graphql.org/September2025/#example-fdbb7
    @Test
    fun `fragments should work on query level`() {
        val response = testedSchema.executeBlocking(
            """
            {
                film {
                    id
                }
                ...film_title
            }

            fragment film_title on Query {
                film {
                    title
                }
            }
        """
        )

        val deserialized = deserialize(response)
        deserialized shouldBe
            mapOf(
                "data" to mapOf(
                    "film" to mapOf(
                        "id" to "Prestige:2006",
                        "title" to "Prestige"
                    )
                )
            )
    }

    @Test
    fun `fragments should work on mutation level`() {
        val response = testedSchema.executeBlocking(
            """
            mutation {
                ...CreateActor
            }

            fragment CreateActor on Mutation {
                createActor(name: "John", age: 42) {
                    name
                }
            }
        """
        )

        val deserialized = deserialize(response)
        deserialized shouldBe
            mapOf(
                "data" to mapOf(
                    "createActor" to mapOf(
                        "name" to "John"
                    )
                )
            )
    }

    // https://spec.graphql.org/September2025/#example-13061
    @Test
    fun `fragments should work on subscription level`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }

            val publisher = mutation("createSubscriptionActor") {
                resolver { name: String, age: Int -> Actor(name, age) }
            }

            subscription("subscriptionActor") {
                resolver { subscription: String ->
                    subscribe(subscription, publisher, Actor("John", 42)) {
                        // Noop
                    }
                }
            }

            subscription("unsubscriptionActor") {
                resolver { subscription: String ->
                    unsubscribe(subscription, publisher, Actor("John", 42))
                }
            }
        }

        val response = schema.executeBlocking(
            """
            subscription {
                ...ActorSubscription
            }

            fragment ActorSubscription on Subscription {
                subscriptionActor(subscription: "my-subscription") {
                    ...ActorName
                }
            }
            
            fragment ActorName on Actor {
                name
            }
        """
        )

        val deserialized = deserialize(response)
        deserialized shouldBe
            mapOf(
                "data" to mapOf(
                    "subscriptionActor" to mapOf(
                        "name" to "John"
                    )
                )
            )

        schema.executeBlocking("""
            subscription {
                unsubscriptionActor(subscription: "my-subscription") {
                    name
                }
            }
        """.trimIndent())
    }

    @Test
    fun `executor should merge several fragment declarations and field declaration on query level`() {
        val response = testedSchema.executeBlocking(
            """
            {
                film {
                    id
                }
                ...film_title
                ...film_director_name @include(if: true)
            }
            
            fragment film_director_name on Query {
                film {
                    director {
                        name
                    }
                }
            }

            fragment film_title on Query {
                film {
                    title
                    year
                    type
                }
                film {
                    title
                    director {
                        age
                    }
                }
            }
        """
        )

        val deserialized = deserialize(response)
        deserialized shouldBe
            mapOf(
                "data" to mapOf(
                    "film" to mapOf(
                        "id" to "Prestige:2006",
                        "title" to "Prestige",
                        "year" to 2006,
                        "type" to "FULL_LENGTH",
                        "director" to mapOf(
                            "name" to "Christopher Nolan",
                            "age" to 43
                        )
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
