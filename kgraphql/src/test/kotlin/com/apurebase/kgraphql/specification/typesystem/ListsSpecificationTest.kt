package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@Specification("3.1.7 Lists")
// See also ListInputCoercionTest
class ListsSpecificationTest {

    @Test
    fun `list arguments are valid`() = runTest {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: Iterable<String> -> list }
            }
        }

        val variables = """
            { "list": ["GAGA", "DADA", "PADA"] }
        """.trimIndent()

        val response =
            deserialize(schema.execute("query(\$list: [String!]!) { list(list: \$list) }", variables))
        response.extract<String>("data/list[0]") shouldBe "GAGA"
        response.extract<String>("data/list[1]") shouldBe "DADA"
        response.extract<String>("data/list[2]") shouldBe "PADA"
    }

    @Test
    fun `lists with nullable entries are valid`() = runTest {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: Iterable<String?> -> list }
            }
        }

        val variables = """
            { "list": ["GAGA", null, "DADA", "PADA"] }
        """.trimIndent()

        val response =
            deserialize(schema.execute("query(\$list: [String!]!) { list(list: \$list) }", variables))
        response.extract<String>("data/list[1]") shouldBe null
    }

    @Test
    fun `lists with non-nullable entries should not accept list with null element`() = runTest {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: Iterable<String> -> list }
            }
        }

        val variables = """
            { "list": ["GAGA", null, "DADA", "PADA"] }
        """.trimIndent()

        expect<InvalidInputValueException>("argument 'null' is not valid value of type String") {
            schema.execute("query(\$list: [String!]!) { list(list: \$list) }", variables)
        }
    }

    @Test
    fun `by default coerce single element input as collection`() = runTest {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: Iterable<String> -> list }
            }
        }

        val variables = """
            { "list": "GAGA" }
        """.trimIndent()

        val response =
            deserialize(schema.execute("query(\$list: [String!]!) { list(list: \$list) }", variables))
        response.extract<String>("data/list[0]") shouldBe "GAGA"
    }

    @Test
    fun `null value is not coerced as single element collection`() = runTest {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: Iterable<String>? -> list }
            }
        }


        val variables = """
            { "list": null }
        """.trimIndent()

        val response =
            deserialize(schema.execute("query(\$list: [String!]!) { list(list: \$list) }", variables))
        response.extract<String>("data/list") shouldBe null
    }

    @Test
    fun `list argument can be declared non-nullable`() = runTest {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: Iterable<String> -> list }
            }
        }

        val variables = """
            { "list": ["GAGA", "DADA", "PADA"] }
        """.trimIndent()

        val response =
            deserialize(schema.execute("query(\$list: [String!]!) { list(list: \$list) }", variables))
        response.extract<Any>("data/list") shouldNotBe null
    }

    @Test
    fun `Iterable implementations are treated as list`() = runTest {

        fun getResult(): Iterable<String> = listOf("POTATO", "BATATO", "ROTATO")

        val schema = KGraphQL.schema {
            query("list") {
                resolver { -> getResult() }
            }
        }

        val response = deserialize(schema.execute("{ list }"))
        response.extract<Iterable<String>>("data/list") shouldBe getResult()
    }

    @Test
    fun `input objects with sets should work properly with direct input`() = runTest {
        data class TestObject(val list: List<String>, val set: Set<String>)

        val schema = KGraphQL.schema {
            query("getObject") {
                resolver { -> TestObject(listOf("foo", "bar", "foo", "bar"), setOf("foo", "bar", "foo", "bar")) }
            }
            mutation("addObject") {
                resolver { input: TestObject -> input }
            }
        }
        val queryResponse = deserialize(schema.execute("{ getObject { list set } }"))
        queryResponse.toString() shouldBe "{data={getObject={list=[foo, bar, foo, bar], set=[foo, bar]}}}"
        val mutationResponse = deserialize(
            schema.execute(
                """
                mutation {
                  addObject(input: { list: ["foo", "bar", "foo", "bar"], set: ["foo", "bar", "foo", "bar"] }) {
                    list set
                  }
                }
                """.trimIndent()
            )
        )
        mutationResponse.toString() shouldBe "{data={addObject={list=[foo, bar, foo, bar], set=[foo, bar]}}}"
    }

    @Test
    fun `input objects with sets should work properly with variables`() = runTest {
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
        val queryResponse = deserialize(schema.execute("{ getObject { list set } }"))
        queryResponse.toString() shouldBe "{data={getObject={list=[foo, bar, foo, bar], set=[foo, bar]}}}"
        val mutationResponse = deserialize(
            schema.execute(
                """
                mutation(${'$'}inputData: TestObjectInput!) {
                  addObject(input: ${'$'}inputData) {
                    list set
                  }
                }
                """.trimIndent(), variables
            )
        )
        mutationResponse.toString() shouldBe "{data={addObject={list=[foo, bar, foo, bar], set=[foo, bar]}}}"
    }

    // https://github.com/stuebingerb/KGraphQL/issues/110
    @Test
    fun `queries with nested lists should work properly`() = runTest {
        val schema = KGraphQL.schema {
            query("getNestedList") {
                resolver { -> listOf(listOf("foo", "bar"), listOf("foobar")) }
            }
        }

        val response = deserialize(schema.execute("{ getNestedList }"))
        response.extract<List<List<String>>>("data/getNestedList") shouldBe listOf(
            listOf("foo", "bar"),
            listOf("foobar")
        )
    }

    @Test
    fun `mutations with nested lists should work properly`() = runTest {
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
            schema.execute(
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
        response.extract<List<*>>("data/createNestedLists/nested1") shouldBe listOf(listOf("foo", "bar", null))
        response.extract<List<*>>("data/createNestedLists/nested2") shouldBe listOf(listOf(listOf(listOf(listOf("foobar")))))
        response.extract<List<*>>("data/createNestedLists/nested3") shouldBe null
    }
}
