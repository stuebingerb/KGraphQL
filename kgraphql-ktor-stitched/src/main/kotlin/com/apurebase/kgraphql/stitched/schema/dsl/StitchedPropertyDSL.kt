package com.apurebase.kgraphql.stitched.schema.dsl

import com.apurebase.kgraphql.stitched.schema.structure.StitchedInputArgument

class StitchedPropertyDSL : StitchedResolverDSL.Target {
    internal lateinit var remoteQuery: String
    var nullable: Boolean = true
    val inputArguments = mutableListOf<StitchedInputArgument>()

    fun remoteQuery(name: String): StitchedResolverDSL {
        remoteQuery = name
        return StitchedResolverDSL(this)
    }

    override fun addArgument(inputArgument: StitchedInputArgument) {
        inputArguments.add(inputArgument)
    }
}
