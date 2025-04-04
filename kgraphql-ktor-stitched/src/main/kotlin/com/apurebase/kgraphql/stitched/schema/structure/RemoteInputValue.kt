package com.apurebase.kgraphql.stitched.schema.structure

import com.apurebase.kgraphql.schema.introspection.__InputValue
import com.apurebase.kgraphql.schema.structure.Type

class RemoteInputValue(
    override val name: String,
    override val type: Type,
    override val defaultValue: String?,
    override val isDeprecated: Boolean,
    override val deprecationReason: String?,
    override val description: String?
) : __InputValue
