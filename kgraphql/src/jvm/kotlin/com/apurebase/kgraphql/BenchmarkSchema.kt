package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder

data class ModelOne(val name: String, val quantity: Int = 1, val active: Boolean = true)

data class ModelTwo(val one: ModelOne, val range: FakeIntRange)

data class ModelThree(val id: String, val twos: List<ModelTwo>)

// wrapping needed, because https://github.com/stuebingerb/KGraphQL/commit/d8ce0085130b9f0f30c0c2f31ed52f44d6456981
// destroyed compatibility with ranges in the schema.
// please see: https://github.com/stuebingerb/KGraphQL/issues/176
class FakeIntRange(range: IntRange) {
    val start = range.first
    val endInclusive = range.last
}

object BenchmarkSchema {
    val ones = listOf(ModelOne("DUDE"), ModelOne("GUY"), ModelOne("PAL"), ModelOne("FELLA"))

    val oneResolver: suspend () -> List<ModelOne> = { ones }

    val twoResolver: suspend (name: String) -> ModelTwo? = { name ->
        ones.find { it.name == name }?.let { ModelTwo(it, FakeIntRange(it.quantity..12)) }
    }

    val threeResolver: suspend () -> ModelThree = { ModelThree("", ones.map { ModelTwo(it, FakeIntRange(it.quantity..10)) }) }

    object HasOneResolver {
        fun oneResolver(): List<ModelOne> {
            return ones
        }
    }

    fun create(block: SchemaBuilder.() -> Unit): Schema = KGraphQL.schema {
        block()
        query("one") {
            resolver(oneResolver)
        }
        query("two") {
            resolver(twoResolver)
        }
        query("three") {
            resolver(threeResolver)
        }
        query("threeKF") {
            HasOneResolver::oneResolver.toResolver()
        }
    }
}
