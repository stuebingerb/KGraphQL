package com.apurebase.kgraphql.merge

import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.schema.execution.merge
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.junit.jupiter.api.Test

class MapMergeTest {
    private val jsonNodeFactory = JsonNodeFactory.instance

    @Test
    suspend fun `merge should add property`() {
        val existing = createMap("param1" to CompletableDeferred(jsonNodeFactory.textNode("value1")))
        val update = CompletableDeferred(jsonNodeFactory.textNode("value2"))

        existing.merge("param2", update)

        existing["param2"] shouldBe update
    }

    @Test
    suspend fun `merge should add nested property`() {
        val existing = createMap("param1" to CompletableDeferred(jsonNodeFactory.textNode("value1")))
        val update = CompletableDeferred(jsonNodeFactory.objectNode().put("param2", "value2"))

        existing.merge("sub", update)

        existing["sub"] shouldBe update
    }

    @Test
    suspend fun `merge should not change simple node`() {
        val existingValue = CompletableDeferred(jsonNodeFactory.textNode("value1"))
        val existing = createMap("param" to existingValue)
        val update = CompletableDeferred(jsonNodeFactory.textNode("value2"))

        expect<IllegalStateException>("trying to merge different simple nodes for param") {
            existing.merge(
                "param",
                update
            )
        }

        existing["param"] shouldBe existingValue
    }

    @Test
    suspend fun `merge should not merge simple node with object node`() {
        val existingValue = CompletableDeferred(jsonNodeFactory.textNode("value1"))
        val existing = createMap("param" to existingValue)
        val update = CompletableDeferred(jsonNodeFactory.objectNode())

        expect<IllegalStateException>("trying to merge object with simple node for param") {
            existing.merge(
                "param",
                update
            )
        }

        val expected: JsonNode? = jsonNodeFactory.textNode("value1")
        existing["param"]?.await() shouldBe expected
    }

    @Test
    suspend fun `merge should not merge object node with simple node`() {
        val existingObj = CompletableDeferred(jsonNodeFactory.objectNode().put("other", "value1"))
        val existing = createMap("param" to existingObj)
        val update = CompletableDeferred(jsonNodeFactory.textNode("value2"))

        expect<IllegalStateException>("trying to merge simple node with object node for param") {
            existing.merge(
                "param",
                update
            )
        }

        existing["param"] shouldBe existingObj
    }

    private fun createMap(vararg pairs: Pair<String, Deferred<JsonNode?>>) = mutableMapOf(*pairs)
}
