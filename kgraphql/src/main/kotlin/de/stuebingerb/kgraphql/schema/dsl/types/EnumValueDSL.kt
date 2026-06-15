package de.stuebingerb.kgraphql.schema.dsl.types

import de.stuebingerb.kgraphql.schema.dsl.DepreciableItemDSL

class EnumValueDSL<T : Enum<T>>(val value: T) : DepreciableItemDSL()
