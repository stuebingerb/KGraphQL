package de.stuebingerb.kgraphql.schema.dsl

import de.stuebingerb.kgraphql.Context

abstract class LimitedAccessItemDSL<PARENT> : DepreciableItemDSL() {

    internal var accessRuleBlock: ((PARENT?, Context) -> Exception?)? = null
}
