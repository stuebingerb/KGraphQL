package de.stuebingerb.kgraphql.schema.introspection

import de.stuebingerb.kgraphql.schema.model.Depreciable

interface __Field : Depreciable, Describable, Named {

    val type: __Type

    val args: List<__InputValue>
}
