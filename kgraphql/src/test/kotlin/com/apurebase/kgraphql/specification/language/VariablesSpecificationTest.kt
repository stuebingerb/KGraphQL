package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.assertNoErrors
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.integration.BaseSchemaTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Specification("2.10 Variables")
class VariablesSpecificationTest : BaseSchemaTest() {
    @Test
    fun `query with variables`() {
        val map = execute(
            query = "mutation(\$name: String!, \$age : Int!) {createActor(name: \$name, age: \$age){name, age}}",
            variables = "{\"name\":\"Boguś Linda\", \"age\": 22}"
        )
        assertNoErrors(map)
        map.extract<Map<String, Any>>("data/createActor") shouldBe mapOf("name" to "Boguś Linda", "age" to 22)
    }

    @Test
    fun `query with int variable`() {
        val map = execute(query = "query(\$rank: Int!) {filmByRank(rank: \$rank) {title}}", variables = "{\"rank\": 1}")
        assertNoErrors(map)
        map.extract<String>("data/filmByRank/title") shouldBe "Prestige"
    }

    // Json only has one number type, so "1" and "1.0" are the same, and input coercion should be able to handle
    // the value accordingly
    @Test
    fun `query with int variable should allow whole floating point numbers`() {
        val map =
            execute(query = "query(\$rank: Int!) {filmByRank(rank: \$rank) {title}}", variables = "{\"rank\": 1.0}")
        assertNoErrors(map)
        map.extract<String>("data/filmByRank/title") shouldBe "Prestige"
    }

    @Test
    fun `query with int variable should not allow floating point numbers that are not whole`() {
        expect<InvalidInputValueException>("Cannot coerce 1.01 to numeric constant") {
            execute(query = "query(\$rank: Int!) {filmByRank(rank: \$rank) {title}}", variables = "{\"rank\": 1.01}")
        }
    }

    // Json only has one number type, so "1" and "1.0" are the same, and input coercion should be able to handle
    // the value accordingly
    @Test
    fun `query with long variable should allow whole floating point numbers`() {
        val map =
            execute(
                query = "query(\$rank: Long!) {filmByRankLong(rank: \$rank) {title}}",
                variables = "{\"rank\": 1.0}"
            )
        assertNoErrors(map)
        map.extract<String>("data/filmByRankLong/title") shouldBe "Prestige"
    }

    @Test
    fun `query with long variable should not allow floating point numbers that are not whole`() {
        expect<InvalidInputValueException>("Cannot coerce 1.01 to numeric constant") {
            execute(
                query = "query(\$rank: Long!) {filmByRankLong(rank: \$rank) {title}}",
                variables = "{\"rank\": 1.01}"
            )
        }
    }

    // Json only has one number type, so "1" and "1.0" are the same, and input coercion should be able to handle
    // the value accordingly
    @Test
    fun `query with short variable should allow whole floating point numbers`() {
        val map =
            execute(
                query = "query(\$rank: Short!) {filmByRankShort(rank: \$rank) {title}}",
                variables = "{\"rank\": 1.0}"
            )
        assertNoErrors(map)
        map.extract<String>("data/filmByRankShort/title") shouldBe "Prestige"
    }

    @Test
    fun `query with short variable should not allow floating point numbers that are not whole`() {
        expect<InvalidInputValueException>("Cannot coerce 1.01 to numeric constant") {
            execute(
                query = "query(\$rank: Short!) {filmByRankShort(rank: \$rank) {title}}",
                variables = "{\"rank\": 1.01}"
            )
        }
    }

    @Test
    fun `query with float variable`() {
        val map = execute(query = "query(\$float: Float!) {float(float: \$float)}", variables = "{\"float\": 42.3}")
        assertNoErrors(map)
        map.extract<Float>("data/float") shouldBe 42.3
    }

    @Test
    fun `query with float variable in exponential notation`() {
        val map = execute(query = "query(\$float: Float!) {float(float: \$float)}", variables = "{\"float\": 2.1e1}")
        assertNoErrors(map)
        map.extract<Float>("data/float") shouldBe 2.1e1
    }

    @Test
    fun `query with float variable should allow integer input`() {
        val map = execute(query = "query(\$float: Float!) {float(float: \$float)}", variables = "{\"float\": 42}")
        assertNoErrors(map)
        map.extract<Float>("data/float") shouldBe 42.0
    }

    @Test
    fun `query with boolean variable`() {
        val map = execute(query = "query(\$big: Boolean!) {number(big: \$big)}", variables = "{\"big\": true}")
        assertNoErrors(map)
        map.extract<Int>("data/number") shouldBe 10000
    }

    @Test
    fun `query with boolean variable default value`() {
        val map = execute(query = "query(\$big: Boolean = true) {number(big: \$big)}")
        assertNoErrors(map)
        map.extract<Int>("data/number") shouldBe 10000
    }

    @Test
    fun `query with enum variable`() {
        val map = execute(
            query = "query(\$type: FilmType!) {filmsByType(type: \$type) {title}}",
            variables = "{\"type\": \"FULL_LENGTH\"}"
        )
        assertNoErrors(map)
        map.extract<Map<String, String>>("data/filmsByType") shouldBe listOf(
            mapOf("title" to "Prestige"), mapOf("title" to "Se7en")
        )
    }

    @Test
    fun `query with variables and string default value`() {
        val map = execute(
            query = "mutation(\$name: String = \"Boguś Linda\", \$age : Int!) {createActor(name: \$name, age: \$age){name, age}}",
            variables = "{\"age\": 22}"
        )
        assertNoErrors(map)
        map.extract<Map<String, Any>>("data/createActor") shouldBe mapOf("name" to "Boguś Linda", "age" to 22)
    }

    @Test
    @Disabled("I don't think this should actually be supported?")
    fun `query with variables and default value pointing to another variable`() {
        val map = execute(
            query = "mutation(\$name: String = \"Boguś Linda\", \$age : Int = \$defaultAge, \$defaultAge : Int!) " +
                "{createActor(name: \$name, age: \$age){name, age}}",
            variables = "{\"defaultAge\": 22}"
        )
        assertNoErrors(map)
        map.extract<Map<String, Any>>("data/createActor") shouldBe mapOf("name" to "Boguś Linda", "age" to 22)
    }

    @Test
    fun `fragment with variable`() {
        val map = execute(
            query = "mutation(\$name: String = \"Boguś Linda\", \$age : Int!, \$big: Boolean!) {createActor(name: \$name, age: \$age){...Linda}}" +
                "fragment Linda on Actor {picture(big: \$big)}",
            variables = "{\"age\": 22, \"big\": true}"
        )
        assertNoErrors(map)
        map.extract<String>("data/createActor/picture") shouldBe "http://picture.server/pic/Boguś_Linda?big=true"
    }

    @Test
    fun `fragment with missing variable`() {
        expect<ValidationException>("Variable '\$big' was not declared for this operation") {
            execute(
                query = "mutation(\$name: String = \"Boguś Linda\", \$age : Int!) {createActor(name: \$name, age: \$age){...Linda}}" +
                    "fragment Linda on Actor {picture(big: \$big)}",
                variables = "{\"age\": 22}"
            )
        }
    }

    @Test
    fun `advanced variables`() {
        val d = "$"
        val request = """
            mutation MultipleCreate(
                ${d}name1: String!,
                ${d}age1: Int!,
                ${d}name2: String!,
                ${d}age2: Int!,
                ${d}input1: ActorExplicitInput!,
                ${d}input2: ActorExplicitInput!,
                ${d}agesName1: String!,
                ${d}ages1: [Int!]!,
                ${d}agesName2: String!,
                ${d}ages2: [Int!]!,
                ${d}agesInput1: ActorCalculateAgeInput!,
                ${d}agesInput2: ActorCalculateAgeInput!
            ) {
                createFirst: createActor(name: ${d}name1, age: ${d}age1) { name, age }
                createSecond: createActor(name: ${d}name2, age: ${d}age2) { name, age }
                inputFirst: createActorWithInput(input: ${d}input1) { name, age }
                inputSecond: createActorWithInput(input: ${d}input2) { name, age }
                agesFirst: createActorWithAges(name: ${d}agesName1, ages: ${d}ages1) { name, age }
                agesSecond: createActorWithAges(name: ${d}agesName2, ages: ${d}ages2) { name, age }
                inputAgesFirst: createActorWithAgesInput(input: ${d}agesInput1) { name, age }
                inputAgesSecond: createActorWithAgesInput(input: ${d}agesInput2) { name, age }
            }
        """.trimEnd()
        val variables = """
            {
                "name1": "Jógvan",
                "age1": 1,
                "name2": "Paweł",
                "age2": 2,
                "input1": {"name": "Olsen", "age": 3},
                "input2": {"name": "Gutkowski", "age": 4},
                "agesName1": "Someone",
                "ages1": [10,50],
                "agesName2": "Some other",
                "ages2": [5, 10],
                "agesInput1": {"name": "Jógvan Olsen", "ages": [3]},
                "agesInput2": {"name": "Paweł Gutkowski", "ages": [4,5,6,7]}
            }
        """.trimIndent()

        val result = execute(request, variables)

        assertNoErrors(result)
        result.extract<String>("data/createFirst/name") shouldBe "Jógvan"
        result.extract<Int>("data/createSecond/age") shouldBe 2
        result.extract<String>("data/inputFirst/name") shouldBe "Olsen"
        result.extract<Int>("data/inputSecond/age") shouldBe 4
        result.extract<String>("data/agesFirst/name") shouldBe "Someone"
        result.extract<Int>("data/agesSecond/age") shouldBe 15
        result.extract<String>("data/inputAgesFirst/name") shouldBe "Jógvan Olsen"
        result.extract<Int>("data/inputAgesSecond/age") shouldBe 22
    }

    @Test
    fun `required variable arrays`() {
        val d = "$"
        val request = """
            mutation MultipleCreate(
                ${d}agesName1: String!,
                ${d}ages1: [Int!]!,
                ${d}agesName2: String!,
                ${d}ages2: [Int!]!
            ) {
                agesFirst: createActorWithAges(name: ${d}agesName1, ages: ${d}ages1) { name, age }
                agesSecond: createActorWithAges(name: ${d}agesName2, ages: ${d}ages2) { name, age }
            }
        """.trimIndent()
        val variables = """
            {
                "agesName1": "Someone",
                "ages1": [10,50],
                "agesName2": "Some other",
                "ages2": [5, 10]
            }
        """.trimIndent()

        val result = execute(request, variables)

        assertNoErrors(result)
        result.extract<String>("data/agesFirst/name") shouldBe "Someone"
        result.extract<Int>("data/agesSecond/age") shouldBe 15
    }

    @Test
    fun `invalid properties should result in appropriate errors`() {
        data class SampleObject(val id: String, val name: String)

        val schema = KGraphQL.schema {
            type<SampleObject> {
                property("readonlyExtension") {
                    resolver { obj: SampleObject -> "${obj.id}-${obj.name}" }
                }
            }
            query("getSample") {
                resolver { id: String -> SampleObject(id, "$id-name") }
            }
            query("validateSample") {
                resolver { sample: SampleObject -> sample.id == "valid" }
            }
        }
        schema.printSchema() shouldBe """
            type Query {
              getSample(id: String!): SampleObject!
              validateSample(sample: SampleObjectInput!): Boolean!
            }
            
            type SampleObject {
              id: String!
              name: String!
              readonlyExtension: String!
            }
            
            input SampleObjectInput {
              id: String!
              name: String!
            }
            
        """.trimIndent()

        expect<InvalidInputValueException>("Property 'readonlyExtension' on 'SampleObjectInput' does not exist") {
            schema.executeBlocking(
                """
                {
                    validateSample(sample: {id: "valid", name: "name", readonlyExtension: "readonlyExtension"})
                }
                """.trimIndent()
            )
        }

        // It should not matter if SampleObjectInput is provided directly or via variables, the error message should be equal
        expect<InvalidInputValueException>("Property 'readonlyExtension' on 'SampleObjectInput' does not exist") {
            schema.executeBlocking(
                """
                query (${'$'}sample: SampleObjectInput!) {
                    validateSample(sample: ${'$'}sample)
                }
                """.trimIndent(),
                variables = "{ \"sample\": {\"id\": \"valid\", \"name\": \"name\", \"readonlyExtension\": \"readonlyExtension\"}}"
            )
        }
    }
}
