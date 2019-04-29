package com.apurebase.kgraphql.schema

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.introspection.__Schema


interface Schema : __Schema {
    fun execute(request: String, variables: String?, context: Context = Context(emptyMap())) : String

    fun execute(request: String, context: Context = Context(emptyMap())) = execute(request, null, context)
}