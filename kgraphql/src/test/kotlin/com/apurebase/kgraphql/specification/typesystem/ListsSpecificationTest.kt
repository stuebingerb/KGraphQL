package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

@Specification("3.1.7 Lists")
// See also ListInputCoercionTest
class ListsSpecificationTest {

    @Test
    fun `list arguments are valid`() {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: Iterable<String> -> list }
            }
        }

        val variables = """
            { "list": ["GAGA", "DADA", "PADA"] }
        """.trimIndent()

        val response =
            deserialize(schema.executeBlocking("query(\$list: [String!]!) { list(list: \$list) }", variables))
        assertThat(response.extract<String>("data/list[0]"), equalTo("GAGA"))
        assertThat(response.extract<String>("data/list[1]"), equalTo("DADA"))
        assertThat(response.extract<String>("data/list[2]"), equalTo("PADA"))
    }

    @Test
    fun `lists with nullable entries are valid`() {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: Iterable<String?> -> list }
            }
        }

        val variables = """
            { "list": ["GAGA", null, "DADA", "PADA"] }
        """.trimIndent()

        val response =
            deserialize(schema.executeBlocking("query(\$list: [String!]!) { list(list: \$list) }", variables))
        assertThat(response.extract<String>("data/list[1]"), nullValue())
    }

    @Test
    fun `lists with non-nullable entries should not accept list with null element`() {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: Iterable<String> -> list }
            }
        }

        val variables = """
            { "list": ["GAGA", null, "DADA", "PADA"] }
        """.trimIndent()

        invoking {
            schema.executeBlocking("query(\$list: [String!]!) { list(list: \$list) }", variables)
        } shouldThrow GraphQLError::class withMessage
            "argument 'null' is not valid value of type String"
    }

    @Test
    fun `by default coerce single element input as collection`() {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: Iterable<String> -> list }
            }
        }

        val variables = """
            { "list": "GAGA" }
        """.trimIndent()

        val response =
            deserialize(schema.executeBlocking("query(\$list: [String!]!) { list(list: \$list) }", variables))
        assertThat(response.extract<String>("data/list[0]"), equalTo("GAGA"))
    }

    @Test
    fun `null value is not coerced as single element collection`() {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: Iterable<String>? -> list }
            }
        }


        val variables = """
            { "list": null }
        """.trimIndent()

        val response =
            deserialize(schema.executeBlocking("query(\$list: [String!]!) { list(list: \$list) }", variables))
        assertThat(response.extract<String>("data/list"), nullValue())
    }

    @Test
    fun `list argument can be declared non-nullable`() {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: Iterable<String> -> list }
            }
        }

        val variables = """
            { "list": ["GAGA", "DADA", "PADA"] }
        """.trimIndent()

        val response =
            deserialize(schema.executeBlocking("query(\$list: [String!]!) { list(list: \$list) }", variables))
        assertThat(response.extract<Any>("data/list"), notNullValue())
    }

    @Test
    fun `Iterable implementations are treated as list`() {

        fun getResult(): Iterable<String> = listOf("POTATO", "BATATO", "ROTATO")

        val schema = KGraphQL.schema {
            query("list") {
                resolver { -> getResult() }
            }
        }

        val response = deserialize(schema.executeBlocking("{ list }"))
        assertThat(response.extract<Iterable<String>>("data/list"), equalTo(getResult()))
    }

    @Test
    fun `input objects with sets should work properly with direct input`() {
        data class TestObject(val list: List<String>, val set: Set<String>)

        val schema = KGraphQL.schema {
            query("getObject") {
                resolver { -> TestObject(listOf("foo", "bar", "foo", "bar"), setOf("foo", "bar", "foo", "bar")) }
            }
            mutation("addObject") {
                resolver { input: TestObject -> input }
            }
        }
        val queryResponse = deserialize(schema.executeBlocking("{ getObject { list set } }"))
        assertThat(queryResponse.toString(), equalTo("{data={getObject={list=[foo, bar, foo, bar], set=[foo, bar]}}}"))
        val mutationResponse = deserialize(
            schema.executeBlocking(
                """
                mutation {
                  addObject(input: { list: ["foo", "bar", "foo", "bar"], set: ["foo", "bar", "foo", "bar"] }) {
                    list set
                  }
                }
                """.trimIndent()
            )
        )
        assertThat(
            mutationResponse.toString(),
            equalTo("{data={addObject={list=[foo, bar, foo, bar], set=[foo, bar]}}}")
        )
    }

    @Test
    fun `input objects with sets should work properly with variables`() {
        data class TestObject(val list: List<String>, val set: Set<String>)

        val schema = KGraphQL.schema {
            query("getObject") {
                resolver { -> TestObject(listOf("foo", "bar", "foo", "bar"), setOf("foo", "bar", "foo", "bar")) }
            }
            mutation("addObject") {
                resolver { input: TestObject -> input }
            }
        }
        val variables = """
            { "inputData": { "list": ["foo", "bar", "foo", "bar"], "set": ["foo", "bar", "foo", "bar"] } }
        """.trimIndent()
        val queryResponse = deserialize(schema.executeBlocking("{ getObject { list set } }"))
        assertThat(queryResponse.toString(), equalTo("{data={getObject={list=[foo, bar, foo, bar], set=[foo, bar]}}}"))
        val mutationResponse = deserialize(
            schema.executeBlocking(
                """
                mutation(${'$'}inputData: TestObjectInput!) {
                  addObject(input: ${'$'}inputData) {
                    list set
                  }
                }
                """.trimIndent(), variables
            )
        )
        assertThat(
            mutationResponse.toString(),
            equalTo("{data={addObject={list=[foo, bar, foo, bar], set=[foo, bar]}}}")
        )
    }

    // https://github.com/stuebingerb/KGraphQL/issues/110
    @Test
    fun `queries with nested lists should work properly`() {
        val schema = KGraphQL.schema {
            query("getNestedList") {
                resolver { -> listOf(listOf("foo", "bar"), listOf("foobar")) }
            }
        }

        val response = deserialize(schema.executeBlocking("{ getNestedList }"))
        assertThat(
            response.extract<List<List<String>>>("data/getNestedList"),
            equalTo(listOf(listOf("foo", "bar"), listOf("foobar")))
        )
    }

    @Test
    fun `mutations with nested lists should work properly`() {
        data class NestedLists(
            val nested1: List<List<String?>>,
            val nested2: List<List<List<List<List<String>?>>?>>,
            val nested3: List<List<List<List<List<List<List<String?>>?>>>>>?
        )

        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("createNestedLists") {
                resolver { nested1: List<List<String?>>,
                           nested2: List<List<List<List<List<String>?>>?>>,
                           nested3: List<List<List<List<List<List<List<String?>>?>>>>>? ->
                    NestedLists(nested1, nested2, nested3)
                }
            }
        }

        val response = deserialize(
            schema.executeBlocking(
                """
                    mutation {
                      createNestedLists(
                        nested1: [["foo", "bar", null]],
                        nested2: [[[[["foobar"]]]]],
                        nested3: null
                      ) {
                        nested1 nested2 nested3
                      }
                    }
                """.trimIndent()
            )
        )
        assertThat(
            response.extract<List<*>>("data/createNestedLists/nested1"),
            equalTo(listOf(listOf("foo", "bar", null)))
        )
        assertThat(
            response.extract<List<*>>("data/createNestedLists/nested2"),
            equalTo(listOf(listOf(listOf(listOf(listOf("foobar"))))))
        )
        assertThat(
            response.extract<List<*>>("data/createNestedLists/nested3"),
            nullValue()
        )
    }
}
