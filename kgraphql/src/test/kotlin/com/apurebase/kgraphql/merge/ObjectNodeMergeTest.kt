package com.apurebase.kgraphql.merge

import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.schema.execution.merge
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ObjectNodeMergeTest {
    private val jsonNodeFactory = JsonNodeFactory.instance

    @Test
    fun `merge should add property`() {
        val existing = jsonNodeFactory.objectNode().put("param1", "value1")
        val update = jsonNodeFactory.objectNode().put("param2", "value2")

        existing.merge(update)

        val expected: JsonNode? = jsonNodeFactory.textNode("value2")
        existing.get("param2") shouldBe expected
    }

    @Test
    fun `merge should add nested property`() {
        val existing = jsonNodeFactory.objectNode().put("param1", "value1")
        val update = jsonNodeFactory.objectNode()
        update.putObject("sub").put("param2", "value2")

        existing.merge(update)

        val expected: JsonNode? = jsonNodeFactory.objectNode().put("param2", "value2")
        existing.get("sub") shouldBe expected
    }

    @Test
    fun `merge should not change simple node`() {
        val existing = jsonNodeFactory.objectNode().put("param", "value1")
        val update = jsonNodeFactory.objectNode().put("param", "value2")

        expect<IllegalStateException>("trying to merge different simple nodes for param") {
            existing.merge(update)
        }

        val expected: JsonNode? = jsonNodeFactory.textNode("value1")
        existing.get("param") shouldBe expected
    }

    @Test
    fun `merge should not merge simple node with object node`() {
        val existing = jsonNodeFactory.objectNode().put("param", "value1")
        val update = jsonNodeFactory.objectNode()
        update.putObject("param")

        expect<IllegalStateException>("trying to merge object with simple node for param") {
            existing.merge(update)
        }

        val expected: JsonNode? = jsonNodeFactory.textNode("value1")
        existing.get("param") shouldBe expected
    }

    @Test
    fun `merge should not merge object node with simple node`() {
        val existing = jsonNodeFactory.objectNode()
        val existingObj: JsonNode? = existing.putObject("param").put("other", "value1")
        val update = jsonNodeFactory.objectNode().put("param", "value2")

        expect<IllegalStateException>("trying to merge simple node with object node for param") {
            existing.merge(update)
        }

        existing.get("param") shouldBe existingObj
    }
}
