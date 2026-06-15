package de.stuebingerb.kgraphql.stitched.schema.dsl

import de.stuebingerb.kgraphql.stitched.schema.structure.StitchedInputArgument

class StitchedResolverDSL(private val target: Target) {
    fun withArgs(block: StitchedInputArgumentsDSL.() -> Unit) {
        val inputArguments = StitchedInputArgumentsDSL().apply(block)
        inputArguments.inputArguments.forEach { target.addArgument(it) }
    }

    interface Target {
        fun addArgument(inputArgument: StitchedInputArgument)
    }
}
