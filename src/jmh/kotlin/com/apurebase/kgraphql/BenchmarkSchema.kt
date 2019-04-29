package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder


data class ModelOne(val name : String, val quantity : Int = 1, val active : Boolean = true)

data class ModelTwo(val one : com.apurebase.kgraphql.ModelOne, val range: IntRange)

data class ModelThree(val id : String, val twos : List<com.apurebase.kgraphql.ModelTwo>)

object BenchmarkSchema {
    val ones = listOf(
        com.apurebase.kgraphql.ModelOne("DUDE"),
        com.apurebase.kgraphql.ModelOne("GUY"),
        com.apurebase.kgraphql.ModelOne("PAL"),
        com.apurebase.kgraphql.ModelOne("FELLA")
    )

    val oneResolver : ()->List<com.apurebase.kgraphql.ModelOne> = { com.apurebase.kgraphql.BenchmarkSchema.ones }

    val twoResolver : (name : String)-> com.apurebase.kgraphql.ModelTwo? = { name ->
        com.apurebase.kgraphql.BenchmarkSchema.ones.find { it.name == name }?.let {
            com.apurebase.kgraphql.ModelTwo(
                it,
                it.quantity..12
            )
        }
    }

    val threeResolver : ()-> com.apurebase.kgraphql.ModelThree = {
        com.apurebase.kgraphql.ModelThree(
            "",
            com.apurebase.kgraphql.BenchmarkSchema.ones.map { com.apurebase.kgraphql.ModelTwo(it, it.quantity..10) })
    }

    fun create(block : SchemaBuilder<Unit>.()-> Unit): Schema = com.apurebase.kgraphql.KGraphQL.Companion.schema {
        block()
        query("one") {
            resolver(com.apurebase.kgraphql.BenchmarkSchema.oneResolver)
        }
        query("two") {
            resolver(com.apurebase.kgraphql.BenchmarkSchema.twoResolver)
        }
        query("three") {
            resolver(com.apurebase.kgraphql.BenchmarkSchema.threeResolver)
        }
    }
}
