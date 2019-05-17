package com.apurebase.kgraphql.request.graph

import com.apurebase.kgraphql.request.Arguments


data class DirectiveInvocation(val key : String, val arguments: Arguments? = null)