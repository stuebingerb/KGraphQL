package de.stuebingerb.kgraphql.specification.language

import de.stuebingerb.kgraphql.Actor
import de.stuebingerb.kgraphql.Specification
import de.stuebingerb.kgraphql.defaultSchema
import de.stuebingerb.kgraphql.deserialize
import de.stuebingerb.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@Specification("2.4 Selection Sets")
class SelectionSetsSpecificationTest {

    val age = 432

    val schema = defaultSchema {
        query("actor") {
            resolver { -> Actor("Boguś Linda", age) }
        }
    }

    @Test
    fun `operation selects the set of information it needs`() {
        val response = deserialize(schema.executeBlocking("{actor{name, age}}"))
        val map = response.extract<Map<String, Any>>("data/actor")
        map shouldBe mapOf("name" to "Boguś Linda", "age" to age)
    }

    @Test
    fun `operation selects the set of information it needs 2`() {
        val response = deserialize(schema.executeBlocking("{actor{name}}"))
        val map = response.extract<Map<String, Any>>("data/actor")
        map shouldBe mapOf<String, Any>("name" to "Boguś Linda")
    }

    @Test
    fun `operation selects the set of information it needs 3`() {
        val response = deserialize(schema.executeBlocking("{actor{age}}"))
        val map = response.extract<Map<String, Any>>("data/actor")
        map shouldBe mapOf<String, Any>("age" to age)
    }
}
