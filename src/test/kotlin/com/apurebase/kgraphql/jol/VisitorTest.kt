package com.apurebase.kgraphql.jol

import com.apurebase.kgraphql.schema.jol.Parser
import org.junit.Ignore
import org.junit.Test

@Ignore("Will probably remove this all together")
class VisitorTest {

    @Test
    fun `validates path argument`() {
        val visited = mutableListOf<Pair<String, Any>>()

        val ast = Parser("{ a }", Parser.Options(noLocation = true)).parseDocument()


    }

    @Test
    fun `validates ancestors argument`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows editing a node both on enter and on leave`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows editing the root node on enter and on leave`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows for editing on enter`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows for editing on leave`() {
        TODO("Implement Test!")
    }

    @Test
    fun `visits edited node`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows skipping a sub-tree`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows early exit while visiting`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows early exit while leaving`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows a named functions visitor API`() {
        TODO("Implement Test!")
    }

    @Test
    fun `visits kitchen sink`() {
        TODO("Implement Test!")
    }

    /////////////////////////////
    //     visitInParallel     //
    /////////////////////////////

    @Test
    fun `allows skipping a sub_tree in parallel`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows skipping different sub-trees in parallel`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows early exit while visiting in parallel`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows early exit from different points in parallel`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows early exit while leaving in parallel`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows early exit from leaving different points in parallel`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows for editing on enter in parallel`() {
        TODO("Implement Test!")
    }

    @Test
    fun `allows for editing on leave in parallel`() {
        TODO("Implement Test!")
    }

    @Test
    fun `visitWithTypeInfo in parallel`() {
        TODO("Implement Test!")
    }

    @Test
    fun `maintains type info during edit in parallel`() {
        TODO("Implement Test!")
    }

}
