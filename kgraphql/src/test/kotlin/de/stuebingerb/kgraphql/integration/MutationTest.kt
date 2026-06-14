package de.stuebingerb.kgraphql.integration

import de.stuebingerb.kgraphql.Actor
import de.stuebingerb.kgraphql.InvalidInputValueException
import de.stuebingerb.kgraphql.KGraphQL
import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.assertNoErrors
import de.stuebingerb.kgraphql.deserialize
import de.stuebingerb.kgraphql.expectExecutionError
import de.stuebingerb.kgraphql.expectRequestError
import de.stuebingerb.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MutationTest : BaseSchemaTest() {

    private val testActor = Actor("Michael Caine", 72)

    @Test
    fun `simple mutation multiple fields`() {
        val map = execute("mutation {createActor(name: \"${testActor.name}\", age: ${testActor.age}){name, age}}")
        assertNoErrors(map)
        map.extract<Map<String, Any>>("data/createActor") shouldBe mapOf(
            "name" to testActor.name,
            "age" to testActor.age
        )
    }

    @Test
    fun `simple mutation single field`() {
        val map = execute("mutation {createActor(name: \"${testActor.name}\", age: ${testActor.age}){name}}")
        assertNoErrors(map)
        map.extract<Map<String, Any>>("data/createActor") shouldBe mapOf<String, Any>("name" to testActor.name)
    }

    @Test
    fun `simple mutation single field 2`() {
        val map = execute("mutation {createActor(name: \"${testActor.name}\", age: ${testActor.age}){age}}")
        assertNoErrors(map)
        map.extract<Map<String, Any>>("data/createActor") shouldBe mapOf<String, Any>("age" to testActor.age)
    }

    @Test
    fun `invalid mutation name`() {
        expectRequestError<ValidationException>("Property 'createBanana' on 'Mutation' does not exist") {
            testedSchema.executeBlocking("mutation {createBanana(name: \"${testActor.name}\", age: ${testActor.age}){age}}")
        }
    }

    @Test
    fun `invalid argument type`() {
        expectExecutionError<InvalidInputValueException>("Cannot coerce '\"fwfwf\"' to Int") {
            testedSchema.executeBlocking("mutation {createActor(name: \"${testActor.name}\", age: \"fwfwf\"){age}}")
        }
    }

    @Test
    fun `invalid arguments number`() {
        expectRequestError<ValidationException>("'createActor' does support arguments: [name, age], found: [name, age, bananan]") {
            testedSchema.executeBlocking("mutation {createActor(name: \"${testActor.name}\", age: ${testActor.age}, bananan: \"fwfwf\"){age}}")
        }
    }

    @Test
    fun `invalid arguments number with NotIntrospected class`() {
        expectRequestError<ValidationException>("'createActorWithContext' does support arguments: [name, age], found: [name, age, bananan]") {
            testedSchema.executeBlocking("mutation {createActorWithContext(name: \"${testActor.name}\", age: ${testActor.age}, bananan: \"fwfwf\"){age}}")
        }
    }

    @Test
    fun `mutation with alias`() {
        val map = execute("mutation {caine : createActor(name: \"${testActor.name}\", age: ${testActor.age}){age}}")
        assertNoErrors(map)
        map.extract<Map<String, Any>>("data/caine") shouldBe mapOf<String, Any>("age" to testActor.age)
    }

    @Test
    fun `mutation with field alias`() {
        val map = execute("mutation {createActor(name: \"${testActor.name}\", age: ${testActor.age}){howOld: age}}")
        assertNoErrors(map)
        map.extract<Map<String, Any>>("data/createActor") shouldBe mapOf<String, Any>("howOld" to testActor.age)
    }

    @Test
    fun `simple mutation with aliased input type`() {
        val map = execute(
            "mutation(\$newActor: ActorInput!) { createActorWithAliasedInputType(newActor: \$newActor) {name}}",
            variables = "{\"newActor\": {\"name\": \"${testActor.name}\", \"age\": ${testActor.age}}}"
        )
        assertNoErrors(map)
        map.extract<Map<String, Any>>("data/createActorWithAliasedInputType") shouldBe mapOf<String, Any>("name" to testActor.name)
    }

    @Test
    fun `multiple mutations should use serial execution`() {
        data class Node(val id: Int, val currentCount: Int)

        var nodeCount = 0
        val schema = KGraphQL.schema {
            query("nodeCount") {
                resolver { -> nodeCount }
            }
            mutation("createNode") {
                resolver { id: Int -> Node(id, ++nodeCount) }
            }
        }
        val request = (1..1_000).joinToString(
            separator = System.lineSeparator(),
            prefix = "mutation {",
            postfix = "}"
        ) { """createNode$it: createNode(id: $it) { id currentCount }""" }

        val responseMap = schema.executeBlocking(request).deserialize()
        (1..1_000).forEach {
            responseMap.extract<Map<String, Any>>("data/createNode$it") shouldBe mapOf("id" to it, "currentCount" to it)
        }
    }
}
