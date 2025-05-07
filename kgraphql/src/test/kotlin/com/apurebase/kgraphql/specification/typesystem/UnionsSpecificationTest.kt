package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.helpers.getFields
import com.apurebase.kgraphql.integration.BaseSchemaTest
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.execution.Execution
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.with
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@Specification("3.1.4 Unions")
class UnionsSpecificationTest : BaseSchemaTest() {

    @Test
    fun `query union property`() {
        val map = execute(
            "{actors{name, favourite{ ... on Actor {name}, ... on Director {name age}, ... on Scenario{content(uppercase: false)}}}}",
            null
        )
        for (i in 0..4) {
            val name = map.extract<String>("data/actors[$i]/name")
            val favourite = map.extract<Map<String, String>>("data/actors[$i]/favourite")
            when (name) {
                "Brad Pitt" -> MatcherAssert.assertThat(favourite, CoreMatchers.equalTo(mapOf("name" to "Tom Hardy")))
                "Tom Hardy" -> MatcherAssert.assertThat(
                    favourite,
                    CoreMatchers.equalTo(mapOf("age" to 43, "name" to "Christopher Nolan"))
                )

                "Morgan Freeman" -> MatcherAssert.assertThat(
                    favourite,
                    CoreMatchers.equalTo(mapOf("content" to "DUMB"))
                )
            }
        }
    }

    @Test
    fun `query union property with external fragment`() {
        val map = execute(
            "{actors{name, favourite{ ...actor, ...director, ...scenario }}}" +
                "fragment actor on Actor {name}" +
                "fragment director on Director {name age}" +
                "fragment scenario on Scenario{content(uppercase: false)} ", null
        )
        for (i in 0..4) {
            val name = map.extract<String>("data/actors[$i]/name")
            val favourite = map.extract<Map<String, String>>("data/actors[$i]/favourite")
            when (name) {
                "Brad Pitt" -> MatcherAssert.assertThat(favourite, CoreMatchers.equalTo(mapOf("name" to "Tom Hardy")))
                "Tom Hardy" -> MatcherAssert.assertThat(
                    favourite,
                    CoreMatchers.equalTo(mapOf("age" to 43, "name" to "Christopher Nolan"))
                )

                "Morgan Freeman" -> MatcherAssert.assertThat(
                    favourite,
                    CoreMatchers.equalTo(mapOf("content" to "DUMB"))
                )
            }
        }
    }

    @Test
    fun `query union property with invalid selection set`() {
        invoking {
            execute("{actors{name, favourite{ name }}}")
        } shouldThrow GraphQLError::class with {
            message shouldBeEqualTo "Invalid selection set with properties: [name] on union type property favourite : [Actor, Scenario, Director]"
        }
    }

    @Test
    fun `a union type should allow requesting __typename`() {
        val result = execute(
            """{
                actors {
                    name
                    favourite {
                        ... on Actor { name }
                        ... on Director { name, age }
                        ... on Scenario { content(uppercase: false) }
                        __typename
                    }
                }
            }""".trimIndent()
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[0]/name/"),
            CoreMatchers.equalTo("Brad Pitt")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[0]/favourite/__typename"),
            CoreMatchers.equalTo("Actor")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[1]/name/"),
            CoreMatchers.equalTo("Morgan Freeman")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[1]/favourite/__typename"),
            CoreMatchers.equalTo("Scenario")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[2]/name/"),
            CoreMatchers.equalTo("Kevin Spacey")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[2]/favourite/__typename"),
            CoreMatchers.equalTo("Actor")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[3]/name/"),
            CoreMatchers.equalTo("Tom Hardy")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[3]/favourite/__typename"),
            CoreMatchers.equalTo("Director")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[4]/name/"),
            CoreMatchers.equalTo("Christian Bale")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[4]/favourite/__typename"),
            CoreMatchers.equalTo("Actor")
        )
    }

    @Test
    fun `a union type should allow requesting __typename only`() {
        val result = execute(
            """{
                actors {
                    name
                    favourite {
                        __typename
                    }
                }
            }""".trimIndent()
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[0]/favourite/__typename"),
            CoreMatchers.equalTo("Actor")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[1]/favourite/__typename"),
            CoreMatchers.equalTo("Scenario")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[2]/favourite/__typename"),
            CoreMatchers.equalTo("Actor")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[3]/favourite/__typename"),
            CoreMatchers.equalTo("Director")
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[4]/favourite/__typename"),
            CoreMatchers.equalTo("Actor")
        )
    }

    @Test
    fun `a union type should require a selection for all potential types`() {
        invoking {
            execute(
                """{
                actors {
                    name
                    favourite {
                        ... on Actor { __typename }
                        ... on Director { name, age }
                    }
                }
            }""".trimIndent()
            )
        } shouldThrow ValidationException::class with {
            message shouldBeEqualTo "Missing selection set for type Scenario"
        }
    }

    @Test
    fun `Nullable union types should be valid`() {
        val result = execute(
            """{
              actors(all: true) {
                name
                nullableFavourite {
                  ... on Actor { name }
                  ... on Director { name, age }
                  ... on Scenario { content(uppercase: false) }
                }
              }
            }""".trimIndent()
        )

        MatcherAssert.assertThat(
            result.extract<String>("data/actors[5]/name"),
            CoreMatchers.equalTo(rickyGervais.name)
        )
        MatcherAssert.assertThat(
            result.extract("data/actors[5]/nullableFavourite"),
            CoreMatchers.equalTo(null)
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[3]/name"),
            CoreMatchers.equalTo(tomHardy.name)
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[3]/nullableFavourite/name"),
            CoreMatchers.equalTo(christopherNolan.name)
        )
    }

    @Test
    fun `Non nullable union types should fail`() {
        invoking {
            execute(
                """{
                    actors(all: true) {
                        name
                        favourite {
                            ... on Actor { name }
                            ... on Director { name, age }
                            ... on Scenario { content(uppercase: false) }
                        }
                    }
                }""".trimIndent()
            )
        } shouldThrow ExecutionException::class with {
            message shouldBeEqualTo "Unexpected type of union property value, expected one of [Actor, Scenario, Director] but was null"
        }
    }

    @Test
    fun `The member types of a Union type must all be Object base types`() {
        expect<SchemaException>("The member types of a Union type must all be Object base types; Scalar, Interface and Union types may not be member types of a Union") {
            KGraphQL.schema {
                unionType("invalid") {
                    type<String>()
                }
            }
        }
    }

    @Test
    fun `A Union type must define one or more unique member types`() {
        expect<SchemaException>("The union type 'invalid' has no possible types defined, requires at least one. Please refer to https://stuebingerb.github.io/KGraphQL/Reference/Type%20System/unions/") {
            KGraphQL.schema {
                unionType("invalid") {}
            }
        }
    }

    /**
     * Kotlin is non-nullable by default (T!), so test covers only case for collections
     */
    @Test
    fun `List may not be member type of a Union`() {
        expect<SchemaException>("Collection may not be member type of a Union 'Invalid'") {
            KGraphQL.schema {
                unionType("Invalid") {
                    type<Collection<*>>()
                }
            }
        }

        expect<SchemaException>("Map may not be member type of a Union 'Invalid'") {
            KGraphQL.schema {
                unionType("Invalid") {
                    type<Map<String, String>>()
                }
            }
        }
    }

    @Test
    fun `Function type may not be member types of a Union`() {
        expect<SchemaException>("Cannot handle function class kotlin.Function as Object type") {
            KGraphQL.schema {
                unionType("invalid") {
                    type<Function<*>>()
                }
            }
        }
    }

    @Suppress("unused")
    sealed class AAA {
        data class BBB(val i: Int) : AAA()
        class CCC(val s: String) : AAA()
    }

    @Suppress("UNUSED_ANONYMOUS_PARAMETER") // "ctx" must stay as-is because resolver cannot handle unnamed parameter
    @Test
    fun `automatic unions out of sealed classes`() {
        defaultSchema {
            unionType<AAA>()

            query("returnUnion") {
                resolver { ctx: Context, isB: Boolean ->
                    if (isB) {
                        AAA.BBB(1)
                    } else {
                        AAA.CCC("String")
                    }
                }
            }
        }
            .executeBlocking(
                """
            {
                f: returnUnion(isB: false) {
                    ... on BBB { i }
                    ... on CCC { s }
                }
                t: returnUnion(isB: true) {
                    ... on BBB { i }
                    ... on CCC { s }                
                }
            }
        """.trimIndent()
            ).deserialize().run {
                extract<String>("data/f/s") shouldBeEqualTo "String"
                extract<Int>("data/t/i") shouldBeEqualTo 1
            }
    }

    @Suppress("unused")
    sealed class WithFields {
        data class Value1(val i: Int, val fields: List<String>) : WithFields()
        data class Value2(val s: String, val fields: List<String>) : WithFields()
    }

    @Test
    fun `union types in lists`() {
        defaultSchema {
            unionType<WithFields>()

            query("returnUnion") {
                resolver { node: Execution.Node ->
                    WithFields.Value1(1, node.getFields())
                }
            }
        }.executeBlocking(
            """
            {
                returnUnion {
                    ... on Value1 { i, fields }
                    ... on Value2 { s, fields }
                }
            }
        """.trimIndent()
        ).deserialize().run {
            extract<Int>("data/returnUnion/i") shouldBeEqualTo 1
            assertThrows<IllegalArgumentException> { extract("data/returnUnion/s") }
            extract<List<String>>("data/returnUnion/fields") shouldBeEqualTo listOf("i", "fields", "s")
        }
    }

    @Test
    fun `union types with custom name def resolver`() {
        defaultSchema {
            unionType<WithFields> {
                subTypeBlock = {
                    name = "Prefix$name"
                }
            }

            query("returnUnion") {
                resolver { node: Execution.Node ->
                    listOf(
                        WithFields.Value1(1, node.getFields()),
                        WithFields.Value2("key", node.getFields()),
                    )
                }
            }
        }.executeBlocking(
            """
            {
                returnUnion {
                    __typename
                }
            }
        """.trimIndent()
        ).deserialize().run {
            extract<String>("data/returnUnion[0]/__typename") shouldBeEqualTo "PrefixValue1"
            extract<String>("data/returnUnion[1]/__typename") shouldBeEqualTo "PrefixValue2"
        }
    }
}
