package com.apurebase.kgraphql.schema

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.introspection.__Schema
import kotlinx.coroutines.runBlocking

interface Schema : __Schema {
    suspend fun execute(request: String, variables: String?, context: Context = Context(emptyMap())) : String

    suspend fun execute(request: String, context: Context = Context(emptyMap())) = execute(request, null, context)

    fun executeBlocking(request: String, context: Context = Context(emptyMap())) = runBlocking { execute(request, context) }
    fun executeBlocking(request: String, variables: String?) = runBlocking { execute(request, variables) }
}
