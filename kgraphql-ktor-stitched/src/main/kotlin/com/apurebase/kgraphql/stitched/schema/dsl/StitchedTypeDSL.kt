package com.apurebase.kgraphql.stitched.schema.dsl

import com.apurebase.kgraphql.stitched.schema.structure.StitchedProperty

class StitchedTypeDSL(private val typeName: String) {
    internal val stitchedProperties = mutableSetOf<StitchedProperty>()

    fun stitchedProperty(name: String, block: StitchedPropertyDSL.() -> Unit) {
        val property = StitchedPropertyDSL().apply(block)
        stitchedProperties.add(
            StitchedProperty(
                typeName,
                name,
                property.remoteQuery,
                property.nullable,
                property.inputArguments
            )
        )
    }
}
