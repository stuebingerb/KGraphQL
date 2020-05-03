package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.defaultSchema
import org.junit.Test

class DataLoaderPropertyDSLTest {

    @Test
    fun `prepare() should support multiple arguments`() {
        defaultSchema {
            type<Parent> {
                dataProperty<Element, Parent>("child") {
                    prepare { parent, a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H ->
                        parent + a + b + c + d + e + f + g + h
                    }
                }
            }
        }
    }

    abstract class Element {
        operator fun plus(a: Element): Element {
            return a
        }
    }
    class Parent : Element()
    class A : Element()
    class B : Element()
    class C : Element()
    class D : Element()
    class E : Element()
    class F : Element()
    class G : Element()
    class H : Element()
}