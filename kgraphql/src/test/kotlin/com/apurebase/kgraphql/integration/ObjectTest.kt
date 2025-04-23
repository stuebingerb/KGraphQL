package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.schema.SchemaException
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.junit.jupiter.api.Test

class ObjectTest {
    data class Person(val name: String, val age: Int)

    @Test
    fun `property name should default to Kotlin name`() {
        val schema = KGraphQL.schema {
            query("getPerson") {
                resolver { name: String -> Person(name = name, age = 42) }
            }
        }

        val sdl = schema.printSchema()
        sdl shouldBeEqualTo """
            type Person {
              age: Int!
              name: String!
            }
            
            type Query {
              getPerson(name: String!): Person!
            }
            
        """.trimIndent()

        schema.executeBlocking(
            """
            query {
              getPerson(name: "foo") { name age }
            }
        """.trimIndent()
        ) shouldBeEqualTo """
            {"data":{"getPerson":{"name":"foo","age":42}}}
        """.trimIndent()
    }

    @Test
    fun `property name should be configurable`() {
        val schema = KGraphQL.schema {
            type<Person> {
                property(Person::age) {
                    name = "newAge"
                }
                property(Person::name) {
                    name = "newName"
                }
            }

            query("getPerson") {
                resolver { name: String -> Person(name = name, age = 42) }
            }
        }

        val sdl = schema.printSchema()
        sdl shouldBeEqualTo """
            type Person {
              newAge: Int!
              newName: String!
            }
            
            type Query {
              getPerson(name: String!): Person!
            }
            
        """.trimIndent()

        schema.executeBlocking(
            """
            query {
              getPerson(name: "foo") { newName newAge }
            }
        """.trimIndent()
        ) shouldBeEqualTo """
            {"data":{"getPerson":{"newName":"foo","newAge":42}}}
        """.trimIndent()
    }

    @Test
    fun `property name must not start with __ when configured`() {
        invoking {
            KGraphQL.schema {
                type<Person> {
                    property(Person::name) {
                        name = "__name"
                    }
                }

                query("getPerson") {
                    resolver { name: String -> Person(name = name, age = 42) }
                }
            }
        } shouldThrow SchemaException::class withMessage "Illegal name '__name'. Names starting with '__' are reserved for introspection system"
    }
}
