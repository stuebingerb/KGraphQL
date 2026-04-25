package com.apurebase.kgraphql.access

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.context
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AccessRulesTest {

    class Player(val name: String, val id: Int = 0)

    val schema = defaultSchema {
        query("black_mamba") {
            resolver { -> Player("KOBE") }
            accessRule { ctx ->
                if (ctx.get<String>() == "LAKERS") {
                    null
                } else {
                    IllegalAccessException()
                }
            }
        }

        query("white_mamba") {
            resolver { -> Player("BONNER") }
        }

        type<Player> {
            val accessRuleBlock = { player: Player, _: Context ->
                if (player.name != "BONNER") {
                    IllegalAccessException("ILLEGAL ACCESS")
                } else {
                    null
                }
            }
            property(Player::id) {
                accessRule(accessRuleBlock)
            }
            property<String?>("item") {
                accessRule(accessRuleBlock)
                resolver { "item" }
            }
        }
    }

    @Test
    fun `allow when matching`() {
        val kobe = deserialize(
            schema.executeBlocking("{black_mamba{name}}", context = context { +"LAKERS" })
        ).extract<String>("data/black_mamba/name")

        kobe shouldBe "KOBE"
    }

    @Test
    fun `reject when not matching`() {
        schema.executeBlocking("{ black_mamba {id} }", context = context { +"LAKERS" }) shouldBe """
            {"errors":[{"message":"ILLEGAL ACCESS","locations":[{"line":1,"column":16}],"path":["black_mamba","id"],"extensions":{"type":"INTERNAL_SERVER_ERROR"}}],"data":null}
        """.trimIndent()
    }

    @Test
    fun `allow property resolver access rule`() {
        deserialize(schema.executeBlocking("{white_mamba {item}}")).extract<String>("data/white_mamba/item") shouldBe "item"
    }

    @Test
    fun `reject property resolver access rule`() {
        schema.executeBlocking("{black_mamba {item}}", context = context { +"LAKERS" }) shouldBe """
            {"errors":[{"message":"ILLEGAL ACCESS","locations":[{"line":1,"column":15}],"path":["black_mamba","item"],"extensions":{"type":"INTERNAL_SERVER_ERROR"}}],"data":{"black_mamba":{"item":null}}}
        """.trimIndent()
    }

    //TODO: MORE TESTS
}
