package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

@Specification("3.1.7 Lists")
// See also ListInputCoercionTest
class ListsSpecificationTest {

    @Test
    fun `list arguments are valid`() {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: List<String> -> list }
            }
        }

        val variables = """
            { "list": ["GAGA", "DADA", "PADA"] }
        """.trimIndent()

        val response =
            deserialize(schema.executeBlocking("query(\$list: [String!]!) { list(list: \$list) }", variables))
        response.extract<String>("data/list[0]") shouldBe "GAGA"
        response.extract<String>("data/list[1]") shouldBe "DADA"
        response.extract<String>("data/list[2]") shouldBe "PADA"
    }

    @Test
    fun `lists with nullable entries are valid`() {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: List<String?> -> list }
            }
        }

        val variables = """
            { "list": ["GAGA", null, "DADA", "PADA"] }
        """.trimIndent()

        val response =
            deserialize(schema.executeBlocking("query(\$list: [String!]!) { list(list: \$list) }", variables))
        response.extract<String>("data/list[1]") shouldBe null
    }

    @Test
    fun `lists with non-nullable entries should not accept list with null element`() {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: List<String> -> list }
            }
        }

        val variables = """
            { "list": ["GAGA", null, "DADA", "PADA"] }
        """.trimIndent()

        expect<InvalidInputValueException>("Cannot coerce 'null' to String") {
            schema.executeBlocking("query(\$list: [String!]!) { list(list: \$list) }", variables)
        }
    }

    @Test
    fun `by default coerce single element input as collection`() {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: List<String> -> list }
            }
        }

        val variables = """
            { "list": "GAGA" }
        """.trimIndent()

        val response =
            deserialize(schema.executeBlocking("query(\$list: [String!]!) { list(list: \$list) }", variables))
        response.extract<String>("data/list[0]") shouldBe "GAGA"
    }

    @Test
    fun `null value is not coerced as single element collection`() {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: List<String>? -> list }
            }
        }

        val variables = """
            { "list": null }
        """.trimIndent()

        val response =
            deserialize(schema.executeBlocking("query(\$list: [String!]!) { list(list: \$list) }", variables))
        response.extract<String>("data/list") shouldBe null
    }

    @Test
    fun `list argument can be declared non-nullable`() {
        val schema = KGraphQL.schema {
            query("list") {
                resolver { list: List<String> -> list }
            }
        }

        val variables = """
            { "list": ["GAGA", "DADA", "PADA"] }
        """.trimIndent()

        val response =
            deserialize(schema.executeBlocking("query(\$list: [String!]!) { list(list: \$list) }", variables))
        response.extract<Any>("data/list") shouldNotBe null
    }

    @Test
    fun `Collection implementations are treated as list`() {

        fun getResult(): Collection<String> = listOf("POTATO", "BATATO", "ROTATO")

        val schema = KGraphQL.schema {
            query("list") {
                resolver { -> getResult() }
            }
        }

        val response = deserialize(schema.executeBlocking("{ list }"))
        response.extract<Collection<String>>("data/list") shouldBe getResult()
    }

    @Test
    fun `Set implementations are treated as list`() {

        fun getResult(): Set<String> = setOf("POTATO", "BATATO", "ROTATO")

        val schema = KGraphQL.schema {
            query("list") {
                resolver { -> getResult() }
            }
        }

        val response = deserialize(schema.executeBlocking("{ list }"))
        response.extract<Set<String>>("data/list") shouldBe getResult()
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
        queryResponse.toString() shouldBe "{data={getObject={list=[foo, bar, foo, bar], set=[foo, bar]}}}"
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
        mutationResponse.toString() shouldBe "{data={addObject={list=[foo, bar, foo, bar], set=[foo, bar]}}}"
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
        queryResponse.toString() shouldBe "{data={getObject={list=[foo, bar, foo, bar], set=[foo, bar]}}}"
        val mutationResponse = deserialize(
            schema.executeBlocking(
                """
                mutation(${'$'}inputData: TestObjectInput!) {
                  addObject(input: ${'$'}inputData) {
                    list set
                  }
                }
                """.trimIndent(),
                variables
            )
        )
        mutationResponse.toString() shouldBe "{data={addObject={list=[foo, bar, foo, bar], set=[foo, bar]}}}"
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
        response.extract<List<List<String>>>("data/getNestedList") shouldBe listOf(
            listOf("foo", "bar"),
            listOf("foobar")
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
        response.extract<List<*>>("data/createNestedLists/nested1") shouldBe listOf(listOf("foo", "bar", null))
        response.extract<List<*>>("data/createNestedLists/nested2") shouldBe listOf(listOf(listOf(listOf(listOf("foobar")))))
        response.extract<List<*>>("data/createNestedLists/nested3") shouldBe null
    }

    @Test
    fun `primitive arrays can be used`() {
        class Model(
            val intArray: IntArray,
            val shortArray: ShortArray,
            val longArray: LongArray,
            val floatArray: FloatArray,
            val doubleArray: DoubleArray,
            val charArray: CharArray,
            val booleanArray: BooleanArray
        )

        val schema = KGraphQL.schema {
            extendedScalars()
            query("model") {
                resolver { ->
                    Model(
                        intArrayOf(1, 2, 3),
                        shortArrayOf(5, 6),
                        longArrayOf(7, 8),
                        floatArrayOf(0.0f, 1.0f),
                        doubleArrayOf(2.0, 3.0),
                        charArrayOf('a', 'b'),
                        booleanArrayOf(true, false)
                    )
                }
            }
        }

        schema.printSchema() shouldBe """
            scalar Char

            scalar Long
            
            scalar Short
            
            type Model {
              booleanArray: [Boolean!]!
              charArray: [Char!]!
              doubleArray: [Float!]!
              floatArray: [Float!]!
              intArray: [Int!]!
              longArray: [Long!]!
              shortArray: [Short!]!
            }
            
            type Query {
              model: Model!
            }

        """.trimIndent()

        schema.executeBlocking("{ model { intArray shortArray longArray floatArray doubleArray charArray booleanArray } }") shouldBe """
            {"data":{"model":{"intArray":[1,2,3],"shortArray":[5,6],"longArray":[7,8],"floatArray":[0.0,1.0],"doubleArray":[2.0,3.0],"charArray":["a","b"],"booleanArray":[true,false]}}}
        """.trimIndent()
    }
}
