package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.Context

abstract class LimitedAccessItemDSL<PARENT> : DepreciableItemDSL() {

    internal var accessRuleBlock: ((PARENT?, Context) -> Exception?)? = null
}
