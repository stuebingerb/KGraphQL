package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.Actor
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.assertNoErrors
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@Specification("2.2 Query Document")
class QueryDocumentSpecificationTest {

    val schema = defaultSchema {
        query("fizz") {
            resolver { -> "buzz" }
        }

        mutation("createActor") {
            resolver { name: String -> Actor(name, 11) }
        }
    }

    @Test
    fun `anonymous operation must be the only defined operation`() {
        expect<ValidationException>("Anonymous operation must be the only defined operation") {
            schema.executeBlocking("query {fizz} mutation BUZZ {createActor(name : \"Kurt Russel\"){name}}")
        }
    }

    @Test
    fun `must provide operation name when multiple named operations`() {
        expect<ValidationException>("Must provide an operation name from: [FIZZ, BUZZ], found: null") {
            schema.executeBlocking("query FIZZ {fizz} mutation BUZZ {createActor(name : \"Kurt Russel\"){name}}")
        }
    }

    @Test
    fun `execute operation by name in variable`() {
        val map = deserialize(
            schema.executeBlocking(
                "query FIZZ {fizz} mutation BUZZ {createActor(name : \"Kurt Russel\"){name}}",
                "{\"operationName\":\"FIZZ\"}"
            )
        )
        assertNoErrors(map)
        map.extract<String>("data/fizz") shouldBe "buzz"
    }
}
