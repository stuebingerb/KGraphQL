package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.Actor
import com.apurebase.kgraphql.KGraphQL.Companion.schema
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.schema.SchemaException
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import nidomiro.kdataloader.ExecutionResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Specification("3.1.2 Objects")
class ObjectsSpecificationTest {
    data class Underscore(val __field: Int, val field__: String = "")
    data class Type(val field: String)

    @Test
    fun `all fields defined within an Object type must not have a name which begins with __`() {
        expect<SchemaException>("Illegal name '__field'. Names starting with '__' are reserved for introspection system") {
            schema {
                query("underscore") {
                    resolver { -> Underscore(0) }
                }
            }
        }
    }

    @Test
    fun `all fields defined within an Object type must have a unique name`() {
        data class Person(val name: String, val age: Int)

        expect<SchemaException>("Cannot add extension field with duplicated name 'age'") {
            schema {
                type<Person> {
                    property("age") {
                        resolver { person: Person -> person.age.toString() }
                    }
                }

                query("getPerson") {
                    resolver { -> Person("foo", 42) }
                }
            }
        }

        expect<SchemaException>("Cannot add dataloaded field with duplicated name 'age'") {
            schema {
                type<Person> {
                    dataProperty<Int, String>("age") {
                        prepare { person: Person -> person.age }
                        loader { ages -> ages.map { ExecutionResult.Success(it.toString()) } }
                    }
                }

                query("getPerson") {
                    resolver { -> Person("foo", 42) }
                }
            }
        }

        expect<SchemaException>("Cannot add union field with duplicated name 'age'") {
            schema {
                type<Person> {
                    unionProperty("age") {
                        returnType = unionType("PersonUnion") {
                            type<Person>()
                        }
                        resolver { person: Person -> person.age.toString() }
                    }
                }

                query("getPerson") {
                    resolver { -> Person("foo", 42) }
                }
            }
        }

        // Conflicts should not only be detected with kotlin properties but also with previous extensions
        expect<SchemaException>("Cannot add dataloaded field with duplicated name 'newAge'") {
            schema {
                type<Person> {
                    property("newAge") {
                        resolver { person: Person -> person.age + 1 }
                    }
                    dataProperty<Int, Int>("newAge") {
                        prepare { person: Person -> person.age }
                        loader { ages -> ages.map { ExecutionResult.Success(it + 1) } }
                    }
                }

                query("getPerson") {
                    resolver { -> Person("foo", 42) }
                }
            }
        }

        // We process fields in fixed order, not as they were configured: even though the union property
        // was added first, the regular property still "wins"
        expect<SchemaException>("Cannot add union field with duplicated name 'newAge'") {
            schema {
                type<Person> {
                    unionProperty("newAge") {
                        returnType = unionType("PersonUnion") {
                            type<Person>()
                        }
                        resolver { person: Person -> person.age.toString() }
                    }
                    property("newAge") {
                        resolver { person: Person -> person.age + 1 }
                    }
                }

                query("getPerson") {
                    resolver { -> Person("foo", 42) }
                }
            }
        }

        // If the original property is ignored, a new one should be able to take its name
        var schema = schema {
            type<Person> {
                property(Person::age) {
                    ignore = true
                }
                property("age") {
                    resolver { person: Person -> person.age.toString() }
                }
            }

            query("getPerson") {
                resolver { -> Person("foo", 42) }
            }
        }

        schema.printSchema() shouldBe """
            type Person {
              age: String!
              name: String!
            }

            type Query {
              getPerson: Person!
            }
            
        """.trimIndent()

        // If the original property is renamed, a new one should be able to take its original name
        schema = schema {
            type<Person> {
                property(Person::age) {
                    name = "oldAge"
                }
                property("age") {
                    resolver { person: Person -> person.age.toString() }
                }
            }

            query("getPerson") {
                resolver { -> Person("foo", 42) }
            }
        }

        schema.printSchema() shouldBe """
            type Person {
              age: String!
              name: String!
              oldAge: Int!
            }

            type Query {
              getPerson: Person!
            }
            
        """.trimIndent()

        // Names are case-sensitive, so different case should be allowed
        schema = schema {
            type<Person> {
                property("Age") {
                    resolver { person: Person -> person.age.toString() }
                }
            }

            query("getPerson") {
                resolver { -> Person("foo", 42) }
            }
        }

        schema.printSchema() shouldBe """
            type Person {
              age: Int!
              Age: String!
              name: String!
            }

            type Query {
              getPerson: Person!
            }
            
        """.trimIndent()
    }

    @Test
    fun `it should be possible to make classes with illegal field names work by renaming problematic properties`() {
        val schema = schema {
            type<Underscore> {
                property(Underscore::__field) {
                    name = "renamed"
                }
            }
            query("underscore") {
                resolver { -> Underscore(0) }
            }
        }

        schema.printSchema() shouldBe """
            type Query {
              underscore: Underscore!
            }

            type Underscore {
              field__: String!
              renamed: Int!
            }
            
        """.trimIndent()
    }

    @Test
    fun `it should be possible to make classes with illegal field names work by ignoring problematic properties`() {
        val schema = schema {
            type<Underscore> {
                property(Underscore::__field) {
                    ignore = true
                }
            }
            query("underscore") {
                resolver { -> Underscore(0) }
            }
        }

        schema.printSchema() shouldBe """
            type Query {
              underscore: Underscore!
            }

            type Underscore {
              field__: String!
            }
            
        """.trimIndent()
    }

    @ParameterizedTest
    @ValueSource(strings = ["name", "Name", "NAME", "legal_name", "name1", "nameWithNumber_42", "_underscore", "_more_under_scores_", "even__", "more__underscores"])
    @Specification("2.1.9 Names")
    fun `legal field names defined within an Object type should be possible`(legalName: String) {
        val schema = schema {
            type<Type> {
                property(Type::field) {
                    name = legalName
                }
            }
            query("queryType") {
                resolver { -> Type("type") }
            }
        }

        schema.types.first { it.name == "Type" }.fields?.firstOrNull { it.name == legalName } shouldNotBe null
    }

    @ParameterizedTest
    @ValueSource(strings = ["special!", "1special", "big$", "ßpecial", "<UNK>pecial", "42", "speciäl"])
    @Specification("2.1.9 Names")
    fun `illegal field names defined within an Object type should result in an appropriate exception`(illegalName: String) {
        expect<SchemaException>("Illegal name '$illegalName'. Names must start with a letter or underscore, and may only contain [_a-zA-Z0-9]") {
            schema {
                type<Type> {
                    property(Type::field) {
                        name = illegalName
                    }
                }
                query("queryType") {
                    resolver { -> Type("type") }
                }
            }
        }
    }

    data class ManyFields(
        val id: String = "Many",
        val id2: String = "Fields",
        val value: Int = 0,
        val smooth: Boolean = false,
        val active: Boolean = false
    )

    data class FewFields(val name: String = "Boguś", val surname: String = "Linda")

    @Test
    fun `fields are conceptually ordered in the same order in which they were encountered during query execution`() = runTest {
        val schema = schema {
            query("many") { resolver { -> ManyFields() } }
            type<ManyFields> {
                property("name") {
                    resolver { _ -> "Boguś" }
                }
            }
        }

        val result = schema.execute("{many{id, id2, value, active, smooth, name}}")
        with(result) {
            indexOf("\"name\"") shouldBeGreaterThan indexOf("\"smooth\"")
            indexOf("\"smooth\"") shouldBeGreaterThan indexOf("\"active\"")
            indexOf("\"active\"") shouldBeGreaterThan indexOf("\"value\"")
            indexOf("\"value\"") shouldBeGreaterThan indexOf("\"id2\"")
            indexOf("\"id2\"") shouldBeGreaterThan indexOf("\"id\"")
        }

        val result2 = schema.execute("{many{name, active, id2, value, smooth, id}}")
        with(result2) {
            indexOf("\"id\"") shouldBeGreaterThan indexOf("\"smooth\"")
            indexOf("\"smooth\"") shouldBeGreaterThan indexOf("\"value\"")
            indexOf("\"value\"") shouldBeGreaterThan indexOf("\"id2\"")
            indexOf("\"id2\"") shouldBeGreaterThan indexOf("\"active\"")
            indexOf("\"active\"") shouldBeGreaterThan indexOf("\"name\"")
        }
    }

    @Test
    fun `fragment spread fields occur before the following fields`() = runTest {
        val schema = schema {
            query("many") { resolver { -> ManyFields() } }
        }

        val result =
            schema.execute("{many{active, ...Fields , smooth, id}} fragment Fields on ManyFields { id2, value }")
        with(result) {
            indexOf("\"id\"") shouldBeGreaterThan indexOf("\"smooth\"")
            indexOf("\"smooth\"") shouldBeGreaterThan indexOf("\"value\"")
            indexOf("\"value\"") shouldBeGreaterThan indexOf("\"id2\"")
            indexOf("\"id2\"") shouldBeGreaterThan indexOf("\"active\"")
        }
    }

    @Test
    fun `fragments for which the type does not apply does not affect ordering`() = runTest {
        val schema = schema {
            query("many") { resolver { -> ManyFields() } }
            type<FewFields>()
        }

        val result = schema.execute(
            "{many{active, ...Fields, ...Few , smooth, id}} " +
                "fragment Fields on ManyFields { id2, value }" +
                "fragment Few on FewFields { name } "
        )
        with(result) {
            indexOf("\"id\"") shouldBeGreaterThan indexOf("\"smooth\"")
            indexOf("\"smooth\"") shouldBeGreaterThan indexOf("\"value\"")
            indexOf("\"value\"") shouldBeGreaterThan indexOf("\"id2\"")
            indexOf("\"id2\"") shouldBeGreaterThan indexOf("\"active\"")
        }
    }

    @Test
    fun `if a field is queried multiple times in a selection, it is ordered by the first time it is encountered`() = runTest {
        val schema = schema {
            query("many") { resolver { -> ManyFields() } }
        }

        val result = schema.execute("{many{id, id2, value, id, active, smooth}}")
        with(result) {
            //ensure that "id" appears only once
            indexOf("\"id\"") shouldBe lastIndexOf("\"id\"")

            indexOf("\"smooth\"") shouldBeGreaterThan indexOf("\"active\"")
            indexOf("\"active\"") shouldBeGreaterThan indexOf("\"value\"")
            indexOf("\"value\"") shouldBeGreaterThan indexOf("\"id2\"")
            indexOf("\"id2\"") shouldBeGreaterThan indexOf("\"id\"")
        }

        val resultFragment =
            schema.execute("{many{id, id2, ...Many, active, smooth}} fragment Many on ManyFields{value, id}")
        with(resultFragment) {
            //ensure that "id" appears only once
            indexOf("\"id\"") shouldBe lastIndexOf("\"id\"")

            indexOf("\"smooth\"") shouldBeGreaterThan indexOf("\"active\"")
            indexOf("\"active\"") shouldBeGreaterThan indexOf("\"value\"")
            indexOf("\"value\"") shouldBeGreaterThan indexOf("\"id2\"")
            indexOf("\"id2\"") shouldBeGreaterThan indexOf("\"id\"")
        }
    }

    @Test
    fun `all arguments defined within a field must not have a name which begins with __`() {
        expect<SchemaException>("Illegal name '__id'. Names starting with '__' are reserved for introspection system") {
            schema {
                query("many") { resolver { __id: String -> ManyFields(__id) } }
            }
        }
    }

    class Empty

    @Test
    fun `an object type must define one or more fields`() {
        expect<SchemaException>("An object type must define one or more fields. Found none on type Empty") {
            schema { type<Empty>() }
        }
    }

    @Test
    fun `field resolution order does not affect response field order`() = runTest {
        val schema = schema {
            type<Actor> {
                property("long") {
                    resolver {
                        Thread.sleep(20)
                        "FINISHED LONG"
                    }
                }

                property("short") {
                    resolver {
                        "FINISHED SHORT"
                    }
                }
            }

            query("actor") {
                resolver<Actor> { Actor("Harden", 22) }
            }
        }

        val responseShortAfterLong = schema.execute("{actor{long, short}}")
        with(responseShortAfterLong) {
            indexOf("short") shouldBeGreaterThan indexOf("long")
        }

        val responseLongAfterShort = schema.execute("{actor{short, long}}")
        with(responseLongAfterShort) {
            indexOf("long") shouldBeGreaterThan indexOf("short")
        }
    }

    @Test
    fun `operation resolution order does not affect response field order`() = runTest {
        val schema = schema {
            query("long") {
                resolver<String> {
                    Thread.sleep(100)
                    "FINISHED LONG"
                }
            }

            query("short") {
                resolver<String> {
                    "FINISHED SHORT"
                }
            }
        }

        val responseShortAfterLong = schema.execute("{long, short}")
        with(responseShortAfterLong) {
            indexOf("short") shouldBeGreaterThan indexOf("long")
        }
    }
}
