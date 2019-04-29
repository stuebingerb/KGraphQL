package com.apurebase.kgraphql.schema.dsl


class EnumValueDSL<T : Enum<T>>(val value : T, block : EnumValueDSL<T>.() -> Unit) : DepreciableItemDSL(){
    init {
        block()
    }
}