package com.apurebase.kgraphql.stitched.schema.structure

/**
 * Adds a new [fieldName] to [typeName] that is calling [remoteQueryName] with [linkArguments].
 * Usually [nullable] to deal with network errors but can be made required.
 */
data class Link(
    val typeName: String,
    val fieldName: String,
    val remoteQueryName: String,
    val nullable: Boolean,
    // TODO: rework arguments
    //  - support constant values?
    //  - support fluent API? Like link(argument).withDefault().withValue(value).fromParent(field)?
    //  - change to map? Like
    //    mapOf("outletRef" to parent("outletId"), "foo" to "bar", "foobar" to "barfoo".fromParent(), "oof" to constant("rab"),
    //      "something" to defaultValue(), "other" to skip(), "else" to explicit(), "outlet" fromParent "outletId", "bar" asConstant "bar2"
    val linkArguments: List<LinkArgument>
)

/**
 * Link argument named [name] either to be provided explicitly or by [parentFieldName].
 */
data class LinkArgument(
    val name: String,
    val parentFieldName: String? = null
)
