package de.stuebingerb.kgraphql.demo

import de.stuebingerb.kgraphql.KGraphQL

suspend fun main() {
    val schema = KGraphQL.schema {
        query("hello") {
            resolver { name: String -> "Hello, $name" }
        }
    }

    //prints '{"data":{"hello":"Hello, Ted Mosby"}}'
    println(schema.execute("{hello(name : \"Ted Mosby\")}"))
}
