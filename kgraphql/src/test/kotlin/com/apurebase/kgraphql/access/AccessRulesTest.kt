package com.apurebase.kgraphql.access

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.context
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AccessRulesTest {

    class Player(val name: String, val id: Int = 0)

    val schema = defaultSchema {
        query("black_mamba") {
            resolver { -> Player("KOBE") }
            accessRule { ctx -> if (ctx.get<String>().equals("LAKERS")) null else IllegalAccessException() }
        }

        query("white_mamba") {
            resolver { -> Player("BONNER") }
        }

        type<Player> {
            val accessRuleBlock = { player: Player, _: Context ->
                if (player.name != "BONNER") IllegalAccessException("ILLEGAL ACCESS") else null
            }
            property(Player::id) {
                accessRule(accessRuleBlock)
            }
            property("item") {
                accessRule(accessRuleBlock)
                resolver { "item" }
            }
        }
    }

    @Test
    fun `allow when matching`() = runTest {
        val kobe = deserialize(
            schema.execute("{black_mamba{name}}", context = context { +"LAKERS" })
        ).extract<String>("data/black_mamba/name")

        kobe shouldBe "KOBE"
    }

    @Test
    fun `reject when not matching`() = runTest {
        expect<IllegalAccessException>("ILLEGAL ACCESS") {
            deserialize(
                schema.execute("{ black_mamba {id} }", context = context { +"LAKERS" })
            ).extract<String>("data/black_mamba/id")
        }
    }

    @Test
    fun `allow property resolver access rule`() = runTest {
        deserialize(schema.execute("{white_mamba {item}}")).extract<String>("data/white_mamba/item") shouldBe "item"
    }

    @Test
    fun `reject property resolver access rule`() = runTest {
        expect<IllegalAccessException>("ILLEGAL ACCESS") {
            schema.execute("{black_mamba {item}}", context = context { +"LAKERS" }).also(::println)
        }
    }

    //TODO: MORE TESTS
}
