package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.scalar.StringScalarCoercion
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.with
import org.amshove.kluent.withMessage
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

@Specification("3.1.1 Scalars")
class ScalarsSpecificationTest {

    @Test
    fun `built-in scalars should be available by default`() {
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

        val response = deserialize(schema.executeBlocking("{ int float double string boolean }"))
        assertThat(response.extract<Int>("data/int"), equalTo(1))
        assertThat(response.extract<Float>("data/float"), equalTo(2.0))
        assertThat(response.extract<Double>("data/double"), equalTo(3.0))
        assertThat(response.extract<String>("data/string"), equalTo("foo"))
        assertThat(response.extract<Boolean>("data/boolean"), equalTo(true))
    }

    @Test
    fun `extended scalars should not be available by default`() {
        invoking {
            KGraphQL.schema {
                query("long") {
                    resolver<Long> { 1L }
                }
            }
        } shouldThrow SchemaException::class withMessage "An Object type must define one or more fields. Found none on type Long"

        invoking {
            KGraphQL.schema {
                query("short") {
                    resolver<Short> { 2.toShort() }
                }
            }
        } shouldThrow SchemaException::class withMessage "An Object type must define one or more fields. Found none on type Short"
    }

    @Test
    fun `extended scalars should be available if included`() {
        val schema = KGraphQL.schema {
            extendedScalars()
            query("long") {
                resolver<Long> { Long.MAX_VALUE }
            }
            query("short") {
                resolver<Short> { 2.toShort() }
            }
        }

        val response = deserialize(schema.executeBlocking("{ long short }"))
        assertThat(response.extract<Long>("data/long"), equalTo(9223372036854775807L))
        assertThat(response.extract<Int>("data/short"), equalTo(2))
    }

    data class Person(val uuid: UUID, val name: String)

    @Test
    fun `type systems can add additional scalars with semantic meaning`() {
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

        val queryResponse = deserialize(testedSchema.executeBlocking("{ person{ uuid } }"))
        assertThat(queryResponse.extract<String>("data/person/uuid"), equalTo(uuid.toString()))

        val mutationResponse = deserialize(
            testedSchema.executeBlocking(
                "mutation { createPerson(uuid: \"$uuid\", name: \"John\"){ uuid name } }"
            )
        )
        assertThat(mutationResponse.extract<String>("data/createPerson/uuid"), equalTo(uuid.toString()))
        assertThat(mutationResponse.extract<String>("data/createPerson/name"), equalTo("John"))
    }

    @Test
    fun `integer value represents a value grater than 2^-31 and less or equal to 2^31`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("Int") {
                resolver { int: Int -> int }
            }
        }

        invoking {
            schema.executeBlocking("mutation { Int(int: ${Integer.MAX_VALUE.toLong() + 2L}) }")
        } shouldThrow GraphQLError::class with {
            message shouldBeEqualTo "Cannot coerce to type of Int as '${Integer.MAX_VALUE.toLong() + 2L}' is greater than (2^-31)-1"
        }
    }

    @Test
    fun `when float is expected as an input type, both integer and float input values are accepted`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("float") {
                resolver { float: Float -> float }
            }
        }
        val map = deserialize(schema.executeBlocking("mutation { float(float: 1) }"))
        assertThat(map.extract<Double>("data/float"), equalTo(1.0))
    }

    @Test
    fun `server can declare custom ID type`() {
        val testedSchema = KGraphQL.schema {
            stringScalar<UUID> {
                name = "ID"
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
            deserialize(testedSchema.executeBlocking("query(\$id: ID = \"$randomUUID\"){ personById(id: \$id) { uuid, name } }"))
        assertThat(map.extract<String>("data/personById/uuid"), equalTo(randomUUID.toString()))
    }


    @Test
    fun `For numeric scalars, input string with numeric content must raise a query error indicating an incorrect type`() {
        val schema = KGraphQL.schema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("Int") {
                resolver { int: Int -> int }
            }
        }

        invoking {
            schema.executeBlocking("mutation { Int(int: \"223\") }")
        } shouldThrow GraphQLError::class withMessage "Cannot coerce \"223\" to numeric constant"
    }

    data class Number(val int: Int)

    @Test
    fun `Schema may declare custom int scalar type`() {
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
        val response = deserialize(schema.executeBlocking("{ number(number: $value) }"))
        assertThat(response.extract<Int>("data/number"), equalTo(value))
    }

    data class Bool(val boolean: Boolean)

    @Test
    fun `Schema may declare custom boolean scalar type`() {
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
        val response = deserialize(schema.executeBlocking("{ boolean(boolean: $value) }"))
        assertThat(response.extract<Boolean>("data/boolean"), equalTo(value))
    }

    data class Boo(val boolean: Boolean)
    data class Sho(val short: Short)
    data class Lon(val long: Long)
    data class Dob(val double: Double)
    data class Num(val int: Int)
    data class Str(val string: String)
    data class Multi(val boo: Boo, val str: String, val num: Num)

    @Test
    fun `Schema may declare custom double scalar type`() {
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
        val response = deserialize(schema.executeBlocking("{ double(double: $value) }"))
        assertThat(response.extract<Double>("data/double"), equalTo(value))
    }

    @Test
    fun `Scalars within input variables`() {
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
            query Query(${d}boo: Boo!,  ${d}sho: Sho!, ${d}lon: Lon!, ${d}dob: Dob!, ${d}num: Num!, ${d}str: Str!) {
                boo(boo: ${d}boo)
                sho(sho: ${d}sho)
                lon(lon: ${d}lon)
                dob(dob: ${d}dob)
                num(num: ${d}num)
                str(str: ${d}str)
                multi { boo, str, num }
            }
        """.trimIndent()

        val values = """
            {
                "boo": $booValue,
                "sho": $shoValue,
                "lon": $lonValue,
                "dob": $dobValue,
                "num": $numValue,
                "str": "$strValue"
            }
        """.trimIndent()

        try {
            val response = deserialize(schema.executeBlocking(req, values))
            assertThat(response.extract<Boolean>("data/boo"), equalTo(booValue))
            assertThat(response.extract<Int>("data/sho"), equalTo(shoValue.toInt()))
            assertThat(response.extract<Int>("data/lon"), equalTo(lonValue.toInt()))
            assertThat(response.extract<Double>("data/dob"), equalTo(dobValue))
            assertThat(response.extract<Int>("data/num"), equalTo(numValue))
            assertThat(response.extract<String>("data/str"), equalTo(strValue))

            assertThat(response.extract<Boolean>("data/multi/boo"), equalTo(false))
            assertThat(response.extract<String>("data/multi/str"), equalTo("String"))
            assertThat(response.extract<Int>("data/multi/num"), equalTo(25))
        } catch (e: GraphQLError) {
            println(e.prettyPrint())
            throw e
        }
    }

    data class NewPart(val manufacturer: String, val name: String, val oem: Boolean, val addedDate: LocalDate)

    @Test
    fun `Schema may declare LocalDate custom scalar`() {
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
            schema.executeBlocking(
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

        assertThat(response.extract<String>("data/addPart/manufacturer"), equalTo(manufacturer))
        assertThat(response.extract<String>("data/addPart/addedDate"), equalTo(addedDate))
    }
}
