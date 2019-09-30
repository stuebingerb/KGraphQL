package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail


@Specification("2.3 Operations")
class OperationsSpecificationTest {

    val schema = defaultSchema {
        query("fizz") {
            resolver{ -> "buzz"}
        }

        mutation("createActor") {
            resolver { name : String -> Actor(name, 11) }
        }
    }

    @Test
    fun `unnamed and named queries are equivalent`(){
        executeEqualQueries(schema,
                mapOf("data" to mapOf("fizz" to "buzz")),
                "{fizz}",
                "query {fizz}",
                "query BUZZ {fizz}"
        )
    }

    @Test
    fun `unnamed and named mutations are equivalent`(){
        executeEqualQueries(schema,
                mapOf("data" to mapOf("createActor" to mapOf("name" to "Kurt Russel"))),
                "mutation {createActor(name : \"Kurt Russel\"){name}}",
                "mutation KURT {createActor(name : \"Kurt Russel\"){name}}"
        )
    }

    @Test
    @Disabled("Feature not supported yet")
    fun `handle subscription`(){
        fail("Feature not supported yet")
    }
}
