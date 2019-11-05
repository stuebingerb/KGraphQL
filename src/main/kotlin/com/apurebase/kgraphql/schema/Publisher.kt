package com.apurebase.kgraphql.schema

interface Publisher {
    fun subscribe(subscription: String, subscriber: Subscriber)
    fun unsubscribe(subscription: String)
}