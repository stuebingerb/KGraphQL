package de.stuebingerb.kgraphql.schema.model

interface Depreciable {
    val isDeprecated: Boolean
    val deprecationReason: String?
}
