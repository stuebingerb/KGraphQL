package de.stuebingerb.kgraphql.schema.introspection

import de.stuebingerb.kgraphql.schema.model.Depreciable

interface __InputValue : Depreciable, Describable, Named {

    val type: __Type

    val defaultValue: String?
}
