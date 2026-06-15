package de.stuebingerb.kgraphql.specification.language

import de.stuebingerb.kgraphql.Actor
import de.stuebingerb.kgraphql.Specification
import de.stuebingerb.kgraphql.defaultSchema
import de.stuebingerb.kgraphql.deserialize
import de.stuebingerb.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@Specification("2.5 Fields")
class FieldsSpecificationTest {

    data class ActorWrapper(val id: String, val actualActor: Actor)

    val age = 432

    val schema = defaultSchema {
        query("actor") {
            resolver { -> ActorWrapper("BLinda", Actor("Boguś Linda", age)) }
        }
    }

    @Test
    fun `field may itself contain a selection set`() {
        val response = deserialize(schema.executeBlocking("{actor{id, actualActor{name, age}}}"))
        val map = response.extract<Map<String, Any>>("data/actor/actualActor")
        map shouldBe mapOf("name" to "Boguś Linda", "age" to age)
    }
}
