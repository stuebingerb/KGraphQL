package com.apurebase.kgraphql.access

import com.apurebase.kgraphql.context
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class AccessRulesTest {

    class Player(val name : String, val id : Int = 0)

    val schema = defaultSchema {
        query("black_mamba") {
            resolver { -> Player("KOBE") }
            accessRule { ctx -> if (ctx.get<String>().equals("LAKERS")) null else IllegalAccessException() }
        }

        query("white_mamba") {
            resolver { -> Player("BONNER") }
        }

        type<Player>{
            property(Player::id){
                accessRule { player, _ ->
                    if(player.name != "BONNER") IllegalAccessException("ILLEGAL ACCESS") else null
                }
            }
        }
    }


    @Test
    fun `allow when matching`(){
        val kobe = deserialize (
                schema.execute("{black_mamba{name}}", context { +"LAKERS" })
        ).extract<String>("data/black_mamba/name")

        assertThat(kobe, equalTo("KOBE"))
    }

    @Test
    fun `reject when not matching`(){
        expect<IllegalAccessException> {
            deserialize (
                    schema.execute("{ black_mamba {id} }", context { +"LAKERS" })
            ).extract<String>("data/black_mamba/id")
        }
    }

    //TODO: MORE TESTS

}