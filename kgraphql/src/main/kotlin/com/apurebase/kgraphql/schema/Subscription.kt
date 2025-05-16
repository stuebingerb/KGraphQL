package com.apurebase.kgraphql.schema

interface Subscription {
    fun request(n: Long)
    fun cancel()
}
