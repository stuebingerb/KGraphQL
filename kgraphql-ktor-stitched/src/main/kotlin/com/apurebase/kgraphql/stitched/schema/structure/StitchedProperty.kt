package com.apurebase.kgraphql.stitched.schema.structure

/**
 * Adds a new [fieldName] to [typeName] that is calling [remoteQueryName] with [inputArguments].
 * Usually [nullable] to deal with network errors but can be made required.
 */
data class StitchedProperty(
    val typeName: String,
    val fieldName: String,
    val remoteQueryName: String,
    val nullable: Boolean,
    val inputArguments: List<StitchedInputArgument>
)

/**
 * Input argument for a [StitchedProperty] named [name] either to be provided explicitly
 * or by [parentFieldName] from the parent type.
 */
data class StitchedInputArgument(
    val name: String,
    val parentFieldName: String? = null
)
