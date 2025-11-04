package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.schema.SchemaException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InputObjectTest {
    data class Person(val name: String, val age: Int)

    @Test
    fun `property name should default to Kotlin name`() {
        val schema = KGraphQL.schema {
            query("getPerson") {
                resolver { name: String -> Person(name = name, age = 42) }
            }

            mutation("addPerson") {
                resolver { person: Person -> person }
            }
        }

        val sdl = schema.printSchema()
        sdl shouldBe """
            type Mutation {
              addPerson(person: PersonInput!): Person!
            }
            
            type Person {
              age: Int!
              name: String!
            }
            
            type Query {
              getPerson(name: String!): Person!
            }
            
            input PersonInput {
              age: Int!
              name: String!
            }
            
        """.trimIndent()

        schema.executeBlocking(
            """
            query {
              getPerson(name: "foo") { name age }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"getPerson":{"name":"foo","age":42}}}
        """.trimIndent()

        schema.executeBlocking(
            """
            mutation {
              addPerson(person: { name: "bar", age: 20 }) { name age }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"addPerson":{"name":"bar","age":20}}}
        """.trimIndent()

        val variables = """
            { "person": { "name": "foobar", "age": 60 } }
        """.trimIndent()
        schema.executeBlocking(
            """
            mutation(${'$'}person: PersonInput!) {
              addPerson(person: ${'$'}person) { name age }
            }
        """.trimIndent(),
            variables = variables
        ) shouldBe """
            {"data":{"addPerson":{"name":"foobar","age":60}}}
        """.trimIndent()
    }

    @Test
    fun `property name should be configurable`() {
        val schema = KGraphQL.schema {
            inputType<Person> {
                name = "PersonInput"
                property(Person::age) {
                    name = "inputAge"
                }
                property(Person::name) {
                    name = "inputName"
                }
            }

            query("getPerson") {
                resolver { name: String -> Person(name = name, age = 42) }
            }

            mutation("addPerson") {
                resolver { person: Person -> person }
            }
        }

        val sdl = schema.printSchema()
        sdl shouldBe """
            type Mutation {
              addPerson(person: PersonInput!): Person!
            }
            
            type Person {
              age: Int!
              name: String!
            }
            
            type Query {
              getPerson(name: String!): Person!
            }
            
            input PersonInput {
              inputAge: Int!
              inputName: String!
            }
            
        """.trimIndent()

        schema.executeBlocking(
            """
            query {
              getPerson(name: "foo") { name age }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"getPerson":{"name":"foo","age":42}}}
        """.trimIndent()

        schema.executeBlocking(
            """
            mutation {
              addPerson(person: { inputName: "bar", inputAge: 20 }) { name age }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"addPerson":{"name":"bar","age":20}}}
        """.trimIndent()

        val variables = """
            { "person": { "inputName": "foobar", "inputAge": 60 } }
        """.trimIndent()
        schema.executeBlocking(
            """
            mutation(${'$'}person: PersonInput!) {
              addPerson(person: ${'$'}person) { name age }
            }
        """.trimIndent(),
            variables = variables
        ) shouldBe """
            {"data":{"addPerson":{"name":"foobar","age":60}}}
        """.trimIndent()
    }

    @Test
    fun `property name must not start with __ when configured`() {
        expect<SchemaException>("Unable to handle input type 'Person': Illegal name '__name'. Names starting with '__' are reserved for introspection system") {
            KGraphQL.schema {
                inputType<Person> {
                    property(Person::name) {
                        name = "__name"
                    }
                }

                query("getPerson") {
                    resolver { person: Person -> person }
                }
            }
        }
    }
}
