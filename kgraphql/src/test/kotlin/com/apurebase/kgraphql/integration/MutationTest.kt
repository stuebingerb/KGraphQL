package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.Actor
import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.assertNoErrors
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
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
        expect<ValidationException>("Property createBanana on Mutation does not exist") {
            execute("mutation {createBanana(name: \"${testActor.name}\", age: ${testActor.age}){age}}")
        }
    }

    @Test
    fun `invalid argument type`() {
        expect<InvalidInputValueException>("Cannot coerce \"fwfwf\" to numeric constant") {
            execute("mutation {createActor(name: \"${testActor.name}\", age: \"fwfwf\"){age}}")
        }
    }

    @Test
    fun `invalid arguments number`() {
        expect<ValidationException>("createActor does support arguments [name, age]. Found arguments [name, age, bananan]") {
            execute("mutation {createActor(name: \"${testActor.name}\", age: ${testActor.age}, bananan: \"fwfwf\"){age}}")
        }
    }

    @Test
    fun `invalid arguments number with NotIntrospected class`() {
        expect<ValidationException>("createActorWithContext does support arguments [name, age]. Found arguments [name, age, bananan]") {
            execute("mutation {createActorWithContext(name: \"${testActor.name}\", age: ${testActor.age}, bananan: \"fwfwf\"){age}}")
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
}
