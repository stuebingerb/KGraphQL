package de.stuebingerb.kgraphql.specification.language

import de.stuebingerb.kgraphql.Actor
import de.stuebingerb.kgraphql.Specification
import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.assertNoErrors
import de.stuebingerb.kgraphql.defaultSchema
import de.stuebingerb.kgraphql.deserialize
import de.stuebingerb.kgraphql.expectRequestError
import de.stuebingerb.kgraphql.extract
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
        expectRequestError<ValidationException>("Anonymous operation must be the only defined operation") {
            schema.executeBlocking("query {fizz} mutation BUZZ {createActor(name : \"Kurt Russel\"){name}}")
        }
    }

    @Test
    fun `must provide operation name when multiple named operations`() {
        expectRequestError<ValidationException>("Must provide an operation name from: [FIZZ, BUZZ], found: null") {
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
