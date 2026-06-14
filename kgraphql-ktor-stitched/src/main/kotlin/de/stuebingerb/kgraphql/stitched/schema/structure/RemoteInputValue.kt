package de.stuebingerb.kgraphql.stitched.schema.structure

import de.stuebingerb.kgraphql.schema.introspection.__InputValue
import de.stuebingerb.kgraphql.schema.structure.Type

class RemoteInputValue(
    override val name: String,
    override val type: Type,
    override val defaultValue: String?,
    override val isDeprecated: Boolean,
    override val deprecationReason: String?,
    override val description: String?
) : __InputValue
