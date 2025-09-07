package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.Scenario
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.schema.SchemaBuilderTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import nidomiro.kdataloader.ExecutionResult
import org.junit.jupiter.api.Test
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class DataLoaderPropertyDSLTest {

    @Test
    fun `prepare() should support multiple arguments`() {
        val schema = defaultSchema {
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
        val results = schema.executeBlocking(
            """
            {
                parent {
                    child(a: "A", b: 1, c: "C", d: 2, e: "E", f: 3, g: "G", h: 4) {
                        data
                    }
                }
            }
            """.trimIndent()
        ).deserialize()
        results.extract<String>("data/parent/child/data") shouldBe "A 1 C 2 E 3 G 4"
    }

    class Parent(val data: String = "")

    @Test
    fun `specifying return type explicitly allows generic data property creation`() {
        val schema = defaultSchema {
            query("scenario") {
                resolver { -> "dummy" }
            }
            type<Scenario> {
                dataProperty("data") {
                    prepare { it }
                    loader { it.map { ExecutionResult.Success(SchemaBuilderTest.InputOne("generic")) } }
                    setReturnType(typeOf<SchemaBuilderTest.InputOne>())
                }
            }
        }

        schema.typeByKClass(SchemaBuilderTest.InputOne::class) shouldNotBe null
    }

    data class Prop<T>(val resultType: KType, val resolver: () -> T)

    @Test
    fun `creation of data properties from a list`() {
        val props = listOf(Prop(typeOf<Int>()) { 0 }, Prop(typeOf<String>()) { "test" })

        val schema = defaultSchema {
            query("scenario") {
                resolver { -> "dummy" }
            }
            type<Scenario> {
                props.forEachIndexed { index, prop ->
                    dataProperty("data_$index") {
                        prepare { it }
                        loader { it.map { ExecutionResult.Success(prop.resolver()) } }
                        setReturnType(prop.resultType)
                    }
                }
            }
        }

        schema.typeByKClass(Int::class) shouldNotBe null
        schema.typeByKClass(String::class) shouldNotBe null
    }
}
