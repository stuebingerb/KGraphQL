package com.apurebase.kgraphql.schema.jol

fun main() {

    val query = """
        {
            helloWorld(start: [25, 30]) {
                stuff
            
            }
        
        }
    """.trimIndent()

    val doc = Parser(query).parseDocument()

    println(doc)

}
