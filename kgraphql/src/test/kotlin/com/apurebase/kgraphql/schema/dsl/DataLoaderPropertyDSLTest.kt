package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.schema.execution.Executor
import nidomiro.kdataloader.ExecutionResult
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class DataLoaderPropertyDSLTest {

    @Test
    fun `prepare() should support multiple arguments`() {
        val schema = defaultSchema {
            configure{
                executor = Executor.DataLoaderPrepared
            }
            query("parent") {
                resolver { -> Parent() }
            }
            type<Parent> {
                dataProperty<String, Parent>("child") {
                    prepare { _, a: String, b: Int, c: String, d: Int, e: String, f: Int, g: String, h: Int ->
                        "$a $b $c $d $e $f $g $h"
                    }
                    loader { keys ->
                        keys.map { ExecutionResult.Success(Parent(it)) }
                    }
                }
            }
        }
        val results = schema.executeBlocking("""
            {
                parent {
                    child(a: "A", b: 1, c: "C", d: 2, e: "E", f: 3, g: "G", h: 4) {
                        data
                    }
                }
            }
            """.trimIndent()).deserialize()
        results.extract<String>("data/parent/child/data") shouldBeEqualTo "A 1 C 2 E 3 G 4"
    }

    class Parent(val data: String = "")
}