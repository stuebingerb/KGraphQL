package com.apurebase.kgraphql.demo

import com.apurebase.kgraphql.integration.QueryTest
import com.apurebase.kgraphql.server.NettyServer

/**
 * Demo application showing of tested schema, by default runs on localhost:8080
 */
fun main() {
    val schema = QueryTest().testedSchema
    NettyServer.run(schema, 8080)
}
