package de.stuebingerb.kgraphql.specification.language

import de.stuebingerb.kgraphql.Actor
import de.stuebingerb.kgraphql.Specification
import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.defaultSchema
import de.stuebingerb.kgraphql.expectRequestError
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
            schema.executeBlocking("{fizz} {fizz}")
        }
    }

    @Test
    fun `must provide operation name with multiple named operations`() {
        expectRequestError<ValidationException>("Must provide an operation name from: [FIZZ, BUZZ]") {
            schema.executeBlocking("query FIZZ {fizz} mutation BUZZ {createActor(name : \"Kurt Russel\"){name}}")
        }
    }

    @Test
    fun `must provide existing operation name with a single named operation`() {
        expectRequestError<ValidationException>("Must provide an operation name from: [FIZZ], found: foo") {
            schema.executeBlocking("query FIZZ {fizz}", operationName = "foo")
        }
    }
}
