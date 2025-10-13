package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.scalar.ID
import com.apurebase.kgraphql.schema.scalar.StringScalarCoercion
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

@Specification("3.1.1 Scalars")
class ScalarsSpecificationTest {

    @Test
    suspend fun `built-in scalars should be available by default`() {
        val schema = KGraphQL.schema {
            query("int") {
                resolver<Int> { 1 }
            }
            query("float") {
                resolver<Float> { 2.0f }
            }
            query("double") {
                resolver<Double> { 3.0 }
            }
            query("string") {
                resolver<String> { "foo" }
            }
            query("boolean") {
                resolver<Boolean> { true }
            }
            // TODO: ID, cf. https://github.com/stuebingerb/KGraphQL/issues/83
        }

        val response = deserialize(schema.execute("{ int float double string boolean }"))
        response.extract<Int>("data/int") shouldBe 1
        response.extract<Float>("data/float") shouldBe 2.0
        response.extract<Double>("data/double") shouldBe 3.0
        response.extract<String>("data/string") shouldBe "foo"
        response.extract<Boolean>("data/boolean") shouldBe true
    }

    @Test
    fun `extended scalars should not be available by default`() {
        expect<SchemaException>("An object type must define one or more fields. Found none on type Long") {
            KGraphQL.schema {
                query("long") {
                    resolver<Long> { 1L }
                }
            }
        }

        expect<SchemaException>("An object type must define one or more fields. Found none on type Short") {
            KGraphQL.schema {
                query("short") {
                    resolver<Short> { 2.toShort() }
                }
            }
        }
    }

    @Test
    suspend fun `extended scalars should be available if included`() {
        val schema = KGraphQL.schema {
            extendedScalars()
            query("long") {
                resolver<Long> { Long.MAX_VALUE }
            }
            query("short") {
                resolver<Short> { 2.toShort() }
            }
        }

        val response = deserialize(schema.execute("{ long short }"))
        response.extract<Long>("data/long") shouldBe 9223372036854775807L
        response.extract<Int>("data/short") shouldBe 2
    }

    data class Person(val uuid: UUID, val name: String)

    @Test
    suspend fun `type systems can add additional scalars with semantic meaning`() {
        val uuid = UUID.randomUUID()
        val testedSchema = KGraphQL.schema {
            stringScalar<UUID> {
                description = "a unique identifier of object"

                coercion = object : StringScalarCoercion<UUID> {
                    override fun serialize(instance: UUID): String = instance.toString()

                    override fun deserialize(raw: String, valueNode: ValueNode): UUID = UUID.fromString(raw)
                }
            }
            query("person") {
                resolver { -> Person(uuid, "John Smith") }
            }
            mutation("createPerson") {
                resolver { uuid: UUID, name: String -> Person(uuid, name) }
            }
        }

        val queryResponse = deserialize(testedSchema.execute("{ person{ uuid } }"))
        queryResponse.extract<String>("data/person/uuid") shouldBe uuid.toString()

        val mutationResponse = deserialize(
            testedSchema.execute(
                "mutation { createPerson(uuid: \"$uuid\", name: \"John\"){ uuid name } }"
            )
        )
        mutationResponse.extract<String>("data/createPerson/uuid") shouldBe uuid.toString()
        mutationResponse.extract<String>("data/createPerson/name") shouldBe "John"
    }

    @Test
    suspend fun `integer value represents a value grater than 2^-31 and less or equal to 2^31`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("Int") {
                resolver { int: Int -> int }
            }
        }

        expect<InvalidInputValueException>("Cannot coerce to type of Int as '${Integer.MAX_VALUE.toLong() + 2L}' is greater than (2^-31)-1") {
            schema.execute("mutation { Int(int: ${Integer.MAX_VALUE.toLong() + 2L}) }")
        }
    }

    @Test
    suspend fun `when float is expected as an input type, both integer and float input values are accepted`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("float") {
                resolver { float: Float -> float }
            }
        }
        val map = deserialize(schema.execute("mutation { float(float: 1) }"))
        map.extract<Double>("data/float") shouldBe 1.0
    }

    @Test
    suspend fun `server can declare custom UUID type`() {
        val testedSchema = KGraphQL.schema {
            stringScalar<UUID> {
                name = "UUID"
                description = "the unique identifier of object"
                deserialize = UUID::fromString
                serialize = UUID::toString
            }
            query("personById") {
                resolver { id: UUID -> Person(id, "John Smith") }
            }
        }

        val randomUUID = UUID.randomUUID()
        val map =
            deserialize(testedSchema.execute("query(\$id: UUID! = \"$randomUUID\"){ personById(id: \$id) { uuid, name } }"))
        map.extract<String>("data/personById/uuid") shouldBe randomUUID.toString()
    }

    @Test
    suspend fun `server can use built-in ID type`() {
        data class IdPerson(val id: ID, val name: String)

        val testedSchema = KGraphQL.schema {
            query("personById") {
                resolver { id: ID -> IdPerson(id, "John Smith") }
            }
            mutation("createPerson") {
                resolver { person: IdPerson -> person }
            }
        }

        testedSchema.printSchema() shouldBe """
            type IdPerson {
              id: ID!
              name: String!
            }
            
            type Mutation {
              createPerson(person: IdPersonInput!): IdPerson!
            }

            type Query {
              personById(id: ID!): IdPerson!
            }
            
            input IdPersonInput {
              id: ID!
              name: String!
            }

        """.trimIndent()

        // UUID
        testedSchema.execute("query(\$id: ID! = \"482629b4-1fac-4f0b-b73c-a3f3ad1a8bf3\") { personById(id: \$id) { id, name } }") shouldBe """
            {"data":{"personById":{"id":"482629b4-1fac-4f0b-b73c-a3f3ad1a8bf3","name":"John Smith"}}}
        """.trimIndent()

        // String
        testedSchema.execute("query(\$id: ID! = \"4\") { personById(id: \$id) { id, name } }") shouldBe """
            {"data":{"personById":{"id":"4","name":"John Smith"}}}
        """.trimIndent()

        // Int
        testedSchema.execute("query(\$id: ID! = 4) { personById(id: \$id) { id, name } }") shouldBe """
            {"data":{"personById":{"id":"4","name":"John Smith"}}}
        """.trimIndent()

        // Negative Int
        testedSchema.execute("query(\$id: ID! = -4) { personById(id: \$id) { id, name } }") shouldBe """
            {"data":{"personById":{"id":"-4","name":"John Smith"}}}
        """.trimIndent()

        // Long
        testedSchema.execute("query(\$id: ID! = 20147483648) { personById(id: \$id) { id, name } }") shouldBe """
            {"data":{"personById":{"id":"20147483648","name":"John Smith"}}}
        """.trimIndent()

        // Double (should fail)
        expect<InvalidInputValueException>("Cannot coerce 4.0 to ID") {
            testedSchema.execute("query(\$id: ID! = 4.0) { personById(id: \$id) { id, name } }")
        }

        // Boolean (should fail)
        expect<InvalidInputValueException>("Cannot coerce true to ID") {
            testedSchema.execute("query(\$id: ID! = true) { personById(id: \$id) { id, name } }")
        }

        // List of strings (should fail)
        expect<InvalidInputValueException>("argument '[\"4\", \"5\"]' is not valid value of type ID") {
            testedSchema.execute("query(\$id: ID! = [\"4\", \"5\"]) { personById(id: \$id) { id, name } }")
        }

        // Object (should fail)
        expect<InvalidInputValueException>("Property 'value' on 'ID' does not exist") {
            testedSchema.execute("query(\$id: ID! = {value: \"4\"}) { personById(id: \$id) { id, name } }")
        }

        // Null (should fail)
        expect<InvalidInputValueException>("argument 'null' is not valid value of type ID") {
            testedSchema.execute("query(\$id: ID! = null) { personById(id: \$id) { id, name } }")
        }

        // Mutation
        testedSchema.execute("mutation(\$person: IdPersonInput! = { id:\"4\",name:\"John Smith\" }){ createPerson(person: \$person) { id, name } }") shouldBe """
            {"data":{"createPerson":{"id":"4","name":"John Smith"}}}
        """.trimIndent()
    }

    @Test
    suspend fun `for numeric scalars, input string with numeric content must raise a query error indicating an incorrect type`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("Int") {
                resolver { int: Int -> int }
            }
        }

        expect<InvalidInputValueException>("Cannot coerce \"223\" to numeric constant") {
            schema.execute("mutation { Int(int: \"223\") }")
        }
    }

    data class Number(val int: Int)

    @Test
    suspend fun `Schema may declare custom int scalar type`() {
        val schema = KGraphQL.schema {
            intScalar<Number> {
                deserialize = ::Number
                serialize = { (int) -> int }
            }

            query("number") {
                resolver { number: Number -> number }
            }
        }

        val value = 3434
        val response = deserialize(schema.execute("{ number(number: $value) }"))
        response.extract<Int>("data/number") shouldBe value
    }

    data class Bool(val boolean: Boolean)

    @Test
    suspend fun `Schema may declare custom boolean scalar type`() {
        val schema = KGraphQL.schema {
            booleanScalar<Bool> {
                deserialize = ::Bool
                serialize = { (boolean) -> boolean }
            }

            query("boolean") {
                resolver { boolean: Boolean -> boolean }
            }
        }

        val value = true
        val response = deserialize(schema.execute("{ boolean(boolean: $value) }"))
        response.extract<Boolean>("data/boolean") shouldBe value
    }

    data class Boo(val boolean: Boolean)
    data class Sho(val short: Short)
    data class Lon(val long: Long)
    data class Dob(val double: Double)
    data class Num(val int: Int)
    data class Str(val string: String)
    data class Multi(val boo: Boo, val str: String, val num: Num)

    @Test
    suspend fun `schema may declare custom double scalar type`() {
        val schema = KGraphQL.schema {
            floatScalar<Dob> {
                deserialize = ::Dob
                serialize = { (double) -> double }
            }

            query("double") {
                resolver { double: Dob -> double }
            }
        }

        val value = 232.33
        val response = deserialize(schema.execute("{ double(double: $value) }"))
        response.extract<Double>("data/double") shouldBe value
    }

    @Test
    suspend fun `scalars within input variables`() {
        val schema = KGraphQL.schema {
            booleanScalar<Boo> {
                deserialize = ::Boo
                serialize = { (boolean) -> boolean }
            }
            longScalar<Lon> {
                deserialize = ::Lon
                serialize = { (long) -> long }
            }
            floatScalar<Dob> {
                deserialize = ::Dob
                serialize = { (double) -> double }
            }
            shortScalar<Sho> {
                deserialize = ::Sho
                serialize = { (short) -> short }
            }
            intScalar<Num> {
                deserialize = ::Num
                serialize = { (num) -> num }
            }
            stringScalar<Str> {
                deserialize = ::Str
                serialize = { (str) -> str }
            }

            query("boo") { resolver { boo: Boo -> boo } }
            query("lon") { resolver { lon: Lon -> lon } }
            query("sho") { resolver { sho: Sho -> sho } }
            query("dob") { resolver { dob: Dob -> dob } }
            query("num") { resolver { num: Num -> num } }
            query("str") { resolver { str: Str -> str } }
            query("multi") { resolver { -> Multi(Boo(false), "String", Num(25)) } }
        }

        val booValue = true
        val lonValue = 124L
        val shoValue: Short = 1
        val dobValue = 2.5
        val numValue = 155
        val strValue = "Test"
        val d = '$'

        val req = """
            query Query(${d}boo: Boo!, ${d}sho: Sho!, ${d}lon: Lon!, ${d}dob: Dob!, ${d}num: Num!, ${d}str: Str!) {
                boo(boo: ${d}boo)
                sho(sho: ${d}sho)
                lon(lon: ${d}lon)
                dob(dob: ${d}dob)
                num(num: ${d}num)
                str(str: ${d}str)
                multi { boo, str, num }
            }
        """.trimIndent()

        var variables = """
            {
                "boo": $booValue,
                "sho": $shoValue,
                "lon": $lonValue,
                "dob": $dobValue,
                "num": $numValue,
                "str": "$strValue"
            }
        """.trimIndent()

        deserialize(schema.execute(req, variables)).run {
            extract<Boolean>("data/boo") shouldBe booValue
            extract<Int>("data/sho") shouldBe shoValue.toInt()
            extract<Int>("data/lon") shouldBe lonValue.toInt()
            extract<Double>("data/dob") shouldBe dobValue
            extract<Int>("data/num") shouldBe numValue
            extract<String>("data/str") shouldBe strValue
            extract<Boolean>("data/multi/boo") shouldBe false
            extract<String>("data/multi/str") shouldBe "String"
            extract<Int>("data/multi/num") shouldBe 25
        }

        // Second request with variables of "incorrect" type (json does not differentiate between 1 and 1.0)
        variables = """
            {
                "boo": $booValue,
                "sho": $shoValue.0,
                "lon": $lonValue.0,
                "dob": $dobValue,
                "num": $numValue.0,
                "str": "$strValue"
            }
        """.trimIndent()

        deserialize(schema.execute(req, variables)).run {
            extract<Boolean>("data/boo") shouldBe booValue
            extract<Int>("data/sho") shouldBe shoValue.toInt()
            extract<Int>("data/lon") shouldBe lonValue.toInt()
            extract<Double>("data/dob") shouldBe dobValue
            extract<Int>("data/num") shouldBe numValue
            extract<String>("data/str") shouldBe strValue
            extract<Boolean>("data/multi/boo") shouldBe false
            extract<String>("data/multi/str") shouldBe "String"
            extract<Int>("data/multi/num") shouldBe 25
        }
    }

    data class NewPart(val manufacturer: String, val name: String, val oem: Boolean, val addedDate: LocalDate)

    @Test
    suspend fun `schema may declare LocalDate custom scalar`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            stringScalar<LocalDate> {
                serialize = { date -> date.toString() }
                deserialize = { dateString -> LocalDate.parse(dateString) }
                description = "Date in format yyyy-mm-dd"
            }

            mutation("addPart") {
                description = "Adds a new part in the parts inventory database"
                resolver { newPart: NewPart ->
                    newPart
                }
            }
        }

        val manufacturer = """Joe Bloggs"""
        val addedDate = "2001-09-01"

        val response = deserialize(
            schema.execute(
                "mutation Mutation(\$newPart: NewPartInput!) { addPart(newPart: \$newPart) { addedDate manufacturer } }",
                """
                {
                  "newPart": {
                    "manufacturer": "$manufacturer",
                    "name": "Front bumper",
                    "oem": true,
                    "addedDate": "$addedDate"
                  }
                }
                """.trimIndent()
            )
        )

        response.extract<String>("data/addPart/manufacturer") shouldBe manufacturer
        response.extract<String>("data/addPart/addedDate") shouldBe addedDate
    }
}
