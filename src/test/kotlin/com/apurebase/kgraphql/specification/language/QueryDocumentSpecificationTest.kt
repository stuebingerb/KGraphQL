package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.Actor
import com.apurebase.kgraphql.RequestException
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.assertNoErrors
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

@Specification("2.2 Query Document")
class QueryDocumentSpecificationTest {

    val schema = defaultSchema {
        query("fizz") {
            resolver{ -> "buzz"}
        }

        mutation("createActor") {
            resolver { name : String -> Actor(name, 11) }
        }
    }

    @Test
    fun `anonymous operation must be the only defined operation`(){
        expect<RequestException>("anonymous operation must be the only defined operation"){
            deserialize(schema.executeBlocking("query {fizz} mutation BUZZ {createActor(name : \"Kurt Russel\"){name}}"))
        }
    }

    @Test
    fun `must provide operation name when multiple named operations`(){
        expect<RequestException>("Must provide an operation name from: [FIZZ, BUZZ]"){
            deserialize(schema.executeBlocking("query FIZZ {fizz} mutation BUZZ {createActor(name : \"Kurt Russel\"){name}}"))
        }
    }

    @Test
    fun `execute operation by name in variable`(){
        val map = deserialize(schema.executeBlocking (
                "query FIZZ {fizz} mutation BUZZ {createActor(name : \"Kurt Russel\"){name}}",
                "{\"operationName\":\"FIZZ\"}"
        ))
        assertNoErrors(map)
        assertThat(map.extract<String>("data/fizz"), equalTo("buzz"))
    }
}
