package de.stuebingerb.kgraphql.stitched.schema.dsl

import de.stuebingerb.kgraphql.stitched.schema.structure.StitchedInputArgument

class StitchedInputArgumentsDSL {
    val inputArguments = mutableListOf<StitchedInputArgument>()

    fun arg(block: StitchedInputArgumentDSL.() -> Unit) {
        inputArguments.add(StitchedInputArgumentDSL().apply(block).run {
            StitchedInputArgument(name, parentFieldName)
        })
    }
}
