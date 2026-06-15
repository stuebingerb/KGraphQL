package de.stuebingerb.kgraphql

import de.stuebingerb.kgraphql.configuration.PluginConfiguration

class KtorGraphQLConfiguration(
    val playground: Boolean,
    val endpoint: String
) : PluginConfiguration
