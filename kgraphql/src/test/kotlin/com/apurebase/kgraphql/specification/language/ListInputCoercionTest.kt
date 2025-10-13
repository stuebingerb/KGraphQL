package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@Specification("3.11 List", "3.12 Non-Null")
// See also ListsSpecificationTest
class ListInputCoercionTest {
    private val schema = defaultSchema {
        query("NullableList") { resolver { value: List<Int?>? -> value } }
        query("NullableNestedList") { resolver { value: List<List<Int?>?>? -> value } }
        query("RequiredList") { resolver { value: List<Int?> -> value } }
        query("NullableSet") { resolver { value: Set<Int?>? -> value } }
        query("NullableNestedSet") { resolver { value: Set<Set<Int?>?>? -> value } }
        query("NullableNestedSetListSet") { resolver { value: Set<List<Set<Int?>?>?>? -> value } }
        query("RequiredSet") { resolver { value: Set<Int?> -> value } }

    }

    @Test
    suspend fun `null should be valid for a nullable list of Int`() {
        val response = deserialize(schema.execute("{ NullableList(value: null) }"))
        response.extract<List<Int>>("data/NullableList") shouldBe null
    }

    @Test
    suspend fun `1 should be valid for a nullable list of Int`() {
        val response = deserialize(schema.execute("{ NullableList(value: 1) }"))
        response.extract<List<Int>>("data/NullableList") shouldBe listOf(1)
    }

    @Test
    suspend fun `a list should be valid for a nullable list of Int`() {
        val response = deserialize(schema.execute("{ NullableList(value: [1, 2, 3]) }"))
        response.extract<List<Int>>("data/NullableList") shouldBe listOf(1, 2, 3)
    }

    @Test
    suspend fun `a list of mixed types should not be valid for a nullable list of Int`() {
        expect<InvalidInputValueException>("Cannot coerce \"b\" to numeric constant") {
            deserialize(schema.execute("{ NullableList(value: [1, \"b\", true]) }"))
        }
    }

    @Test
    suspend fun `foo should not be valid for a nullable list of Int`() {
        expect<InvalidInputValueException>("Cannot coerce \"foo\" to numeric constant") {
            deserialize(schema.execute("{ RequiredList(value: \"foo\") }"))
        }
    }

    @Test
    suspend fun `null should be valid for a nullable nested list of Int`() {
        val response = deserialize(schema.execute("{ NullableNestedList(value: null) }"))
        response.extract<List<Int>>("data/NullableNestedList") shouldBe null
    }

    @Test
    suspend fun `1 should be valid for a nullable nested list of Int`() {
        val response = deserialize(schema.execute("{ NullableNestedList(value: 1) }"))
        response.extract<List<Int>>("data/NullableNestedList") shouldBe listOf(listOf(1))
    }

    @Test
    suspend fun `a nested list should be valid for a nullable nested list of Int`() {
        val response = deserialize(schema.execute("{ NullableNestedList(value: [[1], [2, 3]]) }"))
        response.extract<List<Int>>("data/NullableNestedList") shouldBe listOf(listOf(1), listOf(2, 3))
    }

    @Test
    suspend fun `a non-nested list should not be valid for a nullable nested list of Int`() {
        expect<InvalidInputValueException>("argument '1' is not valid value of type List") {
            deserialize(schema.execute("{ NullableNestedList(value: [1, 2, 3]) }"))
        }
    }

    @Test
    suspend fun `null should not be valid for a required list of Int`() {
        expect<InvalidInputValueException>("argument 'null' is not valid value of type Int") {
            deserialize(schema.execute("{ RequiredList(value: null) }"))
        }
    }

    @Test
    suspend fun `a list should be valid for a required list of Int`() {
        val response = deserialize(schema.execute("{ RequiredList(value: [1, 2, 3]) }"))
        response.extract<List<Int?>>("data/RequiredList") shouldBe listOf(1, 2, 3)
    }

    @Test
    suspend fun `a list with null value should be valid for a required list of Int`() {
        val response = deserialize(schema.execute("{ RequiredList(value: [1, 2, null]) }"))
        response.extract<List<Int?>>("data/RequiredList") shouldBe listOf(1, 2, null)
    }

    @Test
    suspend fun `null should be valid for a nullable set of Int`() {
        val response = deserialize(schema.execute("{ NullableSet(value: null) }"))
        response.extract<Set<Int>>("data/NullableSet") shouldBe null
    }

    @Test
    suspend fun `1 should be valid for a nullable set of Int`() {
        val response = deserialize(schema.execute("{ NullableSet(value: 1) }"))
        response.extract<List<Int>>("data/NullableSet") shouldBe listOf(1)
    }

    @Test
    suspend fun `a list should be valid for a nullable set of Int`() {
        val response = deserialize(schema.execute("{ NullableSet(value: [1, 2, 3, 1]) }"))
        response.extract<List<Int>>("data/NullableSet") shouldBe listOf(1, 2, 3)
    }

    @Test
    suspend fun `a list of mixed types should not be valid for a nullable set of Int`() {
        expect<InvalidInputValueException>("Cannot coerce \"b\" to numeric constant") {
            deserialize(schema.execute("{ NullableSet(value: [1, \"b\", true]) }"))
        }
    }

    @Test
    suspend fun `foo should not be valid for a nullable set of Int`() {
        expect<InvalidInputValueException>("Cannot coerce \"foo\" to numeric constant") {
            deserialize(schema.execute("{ RequiredSet(value: \"foo\") }"))
        }
    }

    @Test
    suspend fun `null should be valid for a nullable nested set of Int`() {
        val response = deserialize(schema.execute("{ NullableNestedSet(value: null) }"))
        response.extract<List<Int>>("data/NullableNestedSet") shouldBe null
    }

    @Test
    suspend fun `1 should be valid for a nullable nested set of Int`() {
        val response = deserialize(schema.execute("{ NullableNestedSet(value: 1) }"))
        response.extract<List<Int>>("data/NullableNestedSet") shouldBe listOf(listOf(1))
    }

    @Test
    suspend fun `a nested list should be valid for a nullable nested set of Int`() {
        val response = deserialize(schema.execute("{ NullableNestedSet(value: [[1], [2, 3, 3]]) }"))
        response.extract<List<Int>>("data/NullableNestedSet") shouldBe listOf(listOf(1), listOf(2, 3))
    }

    @Test
    suspend fun `a nested list should be valid for a nullable nested set of list of set of Int`() {
        val response =
            deserialize(schema.execute("{ NullableNestedSetListSet(value: [[[1]], [[1]], [[2, 3], [2, 3, 3]]]) }"))

        response.extract<List<Int>>("data/NullableNestedSetListSet") shouldBe listOf(
            listOf(listOf(1)),
            listOf(listOf(2, 3), listOf(2, 3))
        )
    }

    @Test
    suspend fun `a non-nested list should not be valid for a nullable nested set of Int`() {
        expect<InvalidInputValueException>("argument '1' is not valid value of type List") {
            deserialize(schema.execute("{ NullableNestedSet(value: [1, 2, 3]) }"))
        }
    }

    @Test
    suspend fun `null should not be valid for a required set of Int`() {
        expect<InvalidInputValueException>("argument 'null' is not valid value of type Int") {
            deserialize(schema.execute("{ RequiredSet(value: null) }"))
        }
    }

    @Test
    suspend fun `a list should be valid for a required set of Int`() {
        val response = deserialize(schema.execute("{ RequiredSet(value: [1, 2, 3]) }"))
        response.extract<List<Int?>>("data/RequiredSet") shouldBe listOf(1, 2, 3)
    }

    @Test
    suspend fun `a list with null value should be valid for a required set of Int`() {
        val response = deserialize(schema.execute("{ RequiredSet(value: [1, 2, null]) }"))
        response.extract<List<Int?>>("data/RequiredSet") shouldBe listOf(1, 2, null)
    }
}
