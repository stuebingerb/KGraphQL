package com.apurebase.kgraphql.schema.stitched

data class Link(
    val typeName: String,
    val fieldName: String,
    val remoteQueryName: String,
    // TODO: local queries should work without specifying the local url
    val localUrl: String?,
    val nullable: Boolean,
    // TODO: rework arguments
    // -- arguments = mapOf("outletRef" to parent("outletId"), "foo" to "bar", "foobar" to "barfoo".fromParent(), "oof" to constant("rab"),
    //                  "something" to defaultValue(), "other" to skip(), "else" to explicit(), "outlet" fromParent "outletId", "bar" asConstant "bar2"
    val linkArguments: List<LinkArgument>
)

/**
 * Link argument named [name] of type [typeName], either to be provided explicitly or by [parentFieldName].
 */
// TODO: support constant values? support fluent API? And then link(argument).withDefault().withValue(value).fromParent(field)?
data class LinkArgument(
    val name: String,
    val parentFieldName: String? = null
)
