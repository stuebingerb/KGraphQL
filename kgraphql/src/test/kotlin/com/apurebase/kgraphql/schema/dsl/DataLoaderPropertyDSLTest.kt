package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.Scenario
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.schema.SchemaBuilderTest
import com.apurebase.kgraphql.schema.dsl.types.TypeDSL
import com.apurebase.kgraphql.schema.execution.Executor
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
            configure {
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

    private inline fun <T : Any, reified P : Any> TypeDSL<T>.createGenericDataProperty(
        returnType: KType,
        crossinline resolver: () -> P
    ) {
        dataProperty<T, P>("data") {
            prepare { it }
            loader { it.map { ExecutionResult.Success(resolver()) } }
            setReturnType(returnType)
        }
    }

    @Test
    fun `specifying return type explicitly allows generic data property creation`() {
        val schema = defaultSchema {
            configure {
                executor = Executor.DataLoaderPrepared
            }
            query("scenario") {
                resolver { -> "dummy" }
            }
            type<Scenario> {
                createGenericDataProperty(typeOf<SchemaBuilderTest.InputOne>()) { SchemaBuilderTest.InputOne("generic") }
            }
        }

        schema.typeByKClass(SchemaBuilderTest.InputOne::class) shouldNotBe null
    }

    data class Prop<T>(val resultType: KType, val resolver: () -> T)

    @Test
    fun `creation of data properties from a list`() {

        val props = listOf(Prop(typeOf<Int>()) { 0 }, Prop(typeOf<String>()) { "test" })

        val schema = defaultSchema {
            configure {
                executor = Executor.DataLoaderPrepared
            }
            query("scenario") {
                resolver { -> "dummy" }
            }
            type<Scenario> {
                props.forEach { prop ->
                    createGenericDataProperty(prop.resultType, prop.resolver)
                }
            }
        }

        schema.typeByKClass(Int::class) shouldNotBe null
        schema.typeByKClass(String::class) shouldNotBe null
    }
}
