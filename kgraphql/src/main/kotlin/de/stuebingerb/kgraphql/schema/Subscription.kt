package de.stuebingerb.kgraphql.schema

interface Subscription {
    fun request(n: Long)
    fun cancel()
}
