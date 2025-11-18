package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.assertNoErrors
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.integration.BaseSchemaTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

@Specification("2.10 Variables")
class VariablesSpecificationTest : BaseSchemaTest() {
    @Test
    fun `query with variables`() {
        val result = execute(
            query = "mutation(\$name: String!, \$age : Int!) {createActor(name: \$name, age: \$age){name, age}}",
            variables = "{\"name\":\"Boguś Linda\", \"age\": 22}"
        )
        assertNoErrors(result)
        result.extract<Map<String, Any>>("data/createActor") shouldBe mapOf("name" to "Boguś Linda", "age" to 22)
    }

    @Test
    fun `query with int variable`() {
        val result =
            execute(query = "query(\$rank: Int!) {filmByRank(rank: \$rank) {title}}", variables = "{\"rank\": 1}")
        assertNoErrors(result)
        result.extract<String>("data/filmByRank/title") shouldBe "Prestige"
    }

    // Json only has one number type, so "1" and "1.0" are the same, and input coercion should be able to handle
    // the value accordingly
    @Test
    fun `query with int variable should allow whole floating point numbers`() {
        val result =
            execute(query = "query(\$rank: Int!) {filmByRank(rank: \$rank) {title}}", variables = "{\"rank\": 1.0}")
        assertNoErrors(result)
        result.extract<String>("data/filmByRank/title") shouldBe "Prestige"
    }

    @Test
    fun `query with int variable should not allow floating point numbers that are not whole`() {
        expect<InvalidInputValueException>("Cannot coerce 1.01 to numeric constant") {
            execute(query = "query(\$rank: Int!) {filmByRank(rank: \$rank) {title}}", variables = "{\"rank\": 1.01}")
        }
    }

    @Test
    fun `query with custom int scalar variable should allow whole floating point numbers`() {
        val result = execute(
            query = "query(\$rank: Rank!) {filmByCustomRank(rank: \$rank) {title}}",
            variables = "{\"rank\": 1.0}"
        )
        assertNoErrors(result)
        result.extract<String>("data/filmByCustomRank/title") shouldBe "Prestige"
    }

    @Test
    fun `query with custom int scalar variable should not allow floating point numbers that are not whole`() {
        expect<InvalidInputValueException>("argument '1.01' is not valid value of type Rank") {
            execute(
                query = "query(\$rank: Rank!) {filmByCustomRank(rank: \$rank) {title}}",
                variables = "{\"rank\": 1.01}"
            )
        }
    }

    // Json only has one number type, so "1" and "1.0" are the same, and input coercion should be able to handle
    // the value accordingly
    @Test
    fun `query with long variable should allow whole floating point numbers`() {
        val result = execute(
            query = "query(\$rank: Long!) {filmByRankLong(rank: \$rank) {title}}",
            variables = "{\"rank\": 1.0}"
        )
        assertNoErrors(result)
        result.extract<String>("data/filmByRankLong/title") shouldBe "Prestige"
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
        val result = execute(
            query = "query(\$rank: Short!) {filmByRankShort(rank: \$rank) {title}}",
            variables = "{\"rank\": 1.0}"
        )
        assertNoErrors(result)
        result.extract<String>("data/filmByRankShort/title") shouldBe "Prestige"
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
        val result = execute(query = "query(\$float: Float!) {float(float: \$float)}", variables = "{\"float\": 42.3}")
        assertNoErrors(result)
        result.extract<Float>("data/float") shouldBe 42.3
    }

    @Test
    fun `query with float variable in exponential notation`() {
        val result = execute(query = "query(\$float: Float!) {float(float: \$float)}", variables = "{\"float\": 2.1e1}")
        assertNoErrors(result)
        result.extract<Float>("data/float") shouldBe 2.1e1
    }

    @Test
    fun `query with float variable should allow integer input`() {
        val result = execute(query = "query(\$float: Float!) {float(float: \$float)}", variables = "{\"float\": 42}")
        assertNoErrors(result)
        result.extract<Float>("data/float") shouldBe 42.0
    }

    @Test
    fun `query with boolean variable`() {
        val result = execute(query = "query(\$big: Boolean!) {number(big: \$big)}", variables = "{\"big\": true}")
        assertNoErrors(result)
        result.extract<Int>("data/number") shouldBe 10000
    }

    @Test
    fun `query with boolean variable and variable default value`() {
        val result = execute(query = "query(\$big: Boolean = true) {number(big: \$big)}")
        assertNoErrors(result)
        result.extract<Int>("data/number") shouldBe 10000
    }

    // https://spec.graphql.org/September2025/#sec-All-Variable-Usages-Are-Allowed.Allowing-Optional-Variables-When-Default-Values-Exist
    // "A notable exception to typical variable type compatibility is allowing a variable definition with a nullable type to be provided to a non-null location as long as either that variable or that location provides a default value."
    @Test
    fun `query with boolean variable and location default value`() {
        val result = execute(query = "query(\$big: Boolean) {bigNumber(big: \$big)}")
        assertNoErrors(result)
        result.extract<Int>("data/bigNumber") shouldBe 10000
    }

    @Test
    fun `query with boolean variable and variable default value but explicit null provided`() {
        expect<InvalidInputValueException>("argument 'null' is not valid value of type Boolean") {
            execute(query = "query(\$big: Boolean = true) {number(big: \$big)}", variables = "{\"big\": null}")
        }
    }

    @Test
    fun `query with boolean variable and location default value but explicit null provided`() {
        expect<InvalidInputValueException>("argument 'null' is not valid value of type Boolean") {
            execute(query = "query(\$big: Boolean) {bigNumber(big: \$big)}", variables = "{\"big\": null}")
        }
    }

    @Test
    fun `query with list variable and variable default value`() {
        val result = execute(query = "query(\$tags: [String] = []) {actorsByTags(tags: \$tags) { name }}")
        assertNoErrors(result)
        result.extract<List<String>>("data/actorsByTags") shouldNotBe null
    }

    @Test
    fun `query with list variable and location default value`() {
        val result = execute(query = "query(\$tags: [String]) {actorsByTagsWithDefault(tags: \$tags) { name }}")
        assertNoErrors(result)
        result.extract<List<String>>("data/actorsByTagsWithDefault") shouldNotBe null
    }

    @Test
    fun `query with list variable and variable default value but explicit null list provided`() {
        expect<InvalidInputValueException>("argument 'null' is not valid value of type String") {
            execute(
                query = "query(\$tags: [String] = []) {actorsByTags(tags: \$tags) { name }}",
                variables = "{\"tags\": null}"
            )
        }
    }

    @Test
    fun `query with list variable and variable default value but explicit null element provided`() {
        expect<InvalidInputValueException>("argument 'null' is not valid value of type String") {
            execute(
                query = "query(\$tags: [String] = []) {actorsByTags(tags: \$tags) { name }}",
                variables = "{\"tags\": [null]}"
            )
        }
    }

    @Test
    fun `query with list variable and location default value but explicit null list provided`() {
        expect<InvalidInputValueException>("argument 'null' is not valid value of type String") {
            execute(query = "query(\$tags: [String] = null) {actorsByTags(tags: \$tags) { name }}")
        }
    }

    @Test
    fun `query with list variable and location default value but explicit null element provided`() {
        expect<InvalidInputValueException>("argument 'null' is not valid value of type String") {
            execute(query = "query(\$tags: [String] = [null]) {actorsByTags(tags: \$tags) { name }}")
        }
    }

    @Test
    fun `query with enum variable`() {
        val result = execute(
            query = "query(\$type: FilmType!) {filmsByType(type: \$type) {title}}",
            variables = "{\"type\": \"FULL_LENGTH\"}"
        )
        assertNoErrors(result)
        result.extract<Map<String, String>>("data/filmsByType") shouldBe listOf(
            mapOf("title" to "Prestige"),
            mapOf("title" to "Se7en")
        )
    }

    @Test
    fun `query with variables and string default value`() {
        val result = execute(
            query = "mutation(\$name: String = \"Boguś Linda\", \$age : Int!) {createActor(name: \$name, age: \$age){name, age}}",
            variables = "{\"age\": 22}"
        )
        assertNoErrors(result)
        result.extract<Map<String, Any>>("data/createActor") shouldBe mapOf("name" to "Boguś Linda", "age" to 22)
    }

    @Test
    fun `fragment with variable`() {
        val result = execute(
            query = "mutation(\$name: String = \"Boguś Linda\", \$age : Int!, \$big: Boolean!) {createActor(name: \$name, age: \$age){...Linda}}" +
                "fragment Linda on Actor {picture(big: \$big)}",
            variables = "{\"age\": 22, \"big\": true}"
        )
        assertNoErrors(result)
        result.extract<String>("data/createActor/picture") shouldBe "http://picture.server/pic/Boguś_Linda?big=true"
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
        val request = """
            mutation MultipleCreate(
                ${'$'}name1: String!,
                ${'$'}age1: Int!,
                ${'$'}name2: String!,
                ${'$'}age2: Int!,
                ${'$'}input1: ActorExplicitInput!,
                ${'$'}input2: ActorExplicitInput!,
                ${'$'}agesName1: String!,
                ${'$'}ages1: [Int!]!,
                ${'$'}agesName2: String!,
                ${'$'}ages2: [Int!]!,
                ${'$'}agesInput1: ActorCalculateAgeInput!,
                ${'$'}agesInput2: ActorCalculateAgeInput!
            ) {
                createFirst: createActor(name: ${'$'}name1, age: ${'$'}age1) { name, age }
                createSecond: createActor(name: ${'$'}name2, age: ${'$'}age2) { name, age }
                inputFirst: createActorWithInput(input: ${'$'}input1) { name, age }
                inputSecond: createActorWithInput(input: ${'$'}input2) { name, age }
                agesFirst: createActorWithAges(name: ${'$'}agesName1, ages: ${'$'}ages1) { name, age }
                agesSecond: createActorWithAges(name: ${'$'}agesName2, ages: ${'$'}ages2) { name, age }
                inputAgesFirst: createActorWithAgesInput(input: ${'$'}agesInput1) { name, age }
                inputAgesSecond: createActorWithAgesInput(input: ${'$'}agesInput2) { name, age }
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
        val request = """
            mutation MultipleCreate(
                ${'$'}agesName1: String!,
                ${'$'}ages1: [Int!]!,
                ${'$'}agesName2: String!,
                ${'$'}ages2: [Int!]!
            ) {
                agesFirst: createActorWithAges(name: ${'$'}agesName1, ages: ${'$'}ages1) { name, age }
                agesSecond: createActorWithAges(name: ${'$'}agesName2, ages: ${'$'}ages2) { name, age }
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

    data class InputType(val id: Int, val id2: Int, val value: String)
    data class Criteria(val inputs: List<InputType>)

    // https://github.com/aPureBase/KGraphQL/issues/137
    @Test
    fun `complex variable arrays`() {
        val schema = KGraphQL.schema {
            configure { wrapErrors = false }
            inputType<InputType>()
            query("search") {
                resolver { criteria: Criteria ->
                    criteria.inputs.joinToString { "${it.id}_${it.id2}: ${it.value}" }
                }
            }
        }

        val result = schema.executeBlocking(
            """
                query Query(${'$'}searches: [InputType!]!) {
                    search(criteria: { inputs: ${'$'}searches })
                }
            """,
            """
                {
                    "searches": [
                        {"id":1, "id2": 2, "value": "Search"}
                    ]
                }
            """
        ).deserialize()

        assertNoErrors(result)
        result.extract<String>("data/search") shouldBe "1_2: Search"
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
