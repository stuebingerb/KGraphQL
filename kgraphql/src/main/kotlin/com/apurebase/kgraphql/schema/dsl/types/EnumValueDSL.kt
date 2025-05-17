package com.apurebase.kgraphql.schema.dsl.types

import com.apurebase.kgraphql.schema.dsl.DepreciableItemDSL

class EnumValueDSL<T : Enum<T>>(val value: T) : DepreciableItemDSL()
