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
    fun `null should be valid for a nullable list of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableList(value: null) }"))
        response.extract<List<Int>>("data/NullableList") shouldBe null
    }

    @Test
    fun `1 should be valid for a nullable list of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableList(value: 1) }"))
        response.extract<List<Int>>("data/NullableList") shouldBe listOf(1)
    }

    @Test
    fun `a list should be valid for a nullable list of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableList(value: [1, 2, 3]) }"))
        response.extract<List<Int>>("data/NullableList") shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `a list of mixed types should not be valid for a nullable list of Int`() {
        expect<InvalidInputValueException>("Cannot coerce '\"b\"' to Int") {
            schema.executeBlocking("{ NullableList(value: [1, \"b\", true]) }")
        }
    }

    @Test
    fun `foo should not be valid for a nullable list of Int`() {
        expect<InvalidInputValueException>("Cannot coerce '\"foo\"' to Int") {
            schema.executeBlocking("{ RequiredList(value: \"foo\") }")
        }
    }

    @Test
    fun `null should be valid for a nullable nested list of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableNestedList(value: null) }"))
        response.extract<List<Int>>("data/NullableNestedList") shouldBe null
    }

    @Test
    fun `1 should be valid for a nullable nested list of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableNestedList(value: 1) }"))
        response.extract<List<Int>>("data/NullableNestedList") shouldBe listOf(listOf(1))
    }

    @Test
    fun `a nested list should be valid for a nullable nested list of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableNestedList(value: [[1], [2, 3]]) }"))
        response.extract<List<Int>>("data/NullableNestedList") shouldBe listOf(listOf(1), listOf(2, 3))
    }

    @Test
    fun `a non-nested list should not be valid for a nullable nested list of Int`() {
        expect<InvalidInputValueException>("Cannot coerce '1' to List") {
            schema.executeBlocking("{ NullableNestedList(value: [1, 2, 3]) }")
        }
    }

    @Test
    fun `null should not be valid for a required list of Int`() {
        expect<InvalidInputValueException>("Cannot coerce 'null' to Int") {
            schema.executeBlocking("{ RequiredList(value: null) }")
        }
    }

    @Test
    fun `a list should be valid for a required list of Int`() {
        val response = deserialize(schema.executeBlocking("{ RequiredList(value: [1, 2, 3]) }"))
        response.extract<List<Int?>>("data/RequiredList") shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `a list with null value should be valid for a required list of Int`() {
        val response = deserialize(schema.executeBlocking("{ RequiredList(value: [1, 2, null]) }"))
        response.extract<List<Int?>>("data/RequiredList") shouldBe listOf(1, 2, null)
    }

    @Test
    fun `null should be valid for a nullable set of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableSet(value: null) }"))
        response.extract<Set<Int>>("data/NullableSet") shouldBe null
    }

    @Test
    fun `1 should be valid for a nullable set of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableSet(value: 1) }"))
        response.extract<List<Int>>("data/NullableSet") shouldBe listOf(1)
    }

    @Test
    fun `a list should be valid for a nullable set of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableSet(value: [1, 2, 3, 1]) }"))
        response.extract<List<Int>>("data/NullableSet") shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `a list of mixed types should not be valid for a nullable set of Int`() {
        expect<InvalidInputValueException>("Cannot coerce '\"b\"' to Int") {
            schema.executeBlocking("{ NullableSet(value: [1, \"b\", true]) }")
        }
    }

    @Test
    fun `foo should not be valid for a nullable set of Int`() {
        expect<InvalidInputValueException>("Cannot coerce '\"foo\"' to Int") {
            schema.executeBlocking("{ RequiredSet(value: \"foo\") }")
        }
    }

    @Test
    fun `null should be valid for a nullable nested set of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableNestedSet(value: null) }"))
        response.extract<List<Int>>("data/NullableNestedSet") shouldBe null
    }

    @Test
    fun `1 should be valid for a nullable nested set of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableNestedSet(value: 1) }"))
        response.extract<List<Int>>("data/NullableNestedSet") shouldBe listOf(listOf(1))
    }

    @Test
    fun `a nested list should be valid for a nullable nested set of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableNestedSet(value: [[1], [2, 3, 3]]) }"))
        response.extract<List<Int>>("data/NullableNestedSet") shouldBe listOf(listOf(1), listOf(2, 3))
    }

    @Test
    fun `a nested list should be valid for a nullable nested set of list of set of Int`() {
        val response =
            deserialize(schema.executeBlocking("{ NullableNestedSetListSet(value: [[[1]], [[1]], [[2, 3], [2, 3, 3]]]) }"))

        response.extract<List<Int>>("data/NullableNestedSetListSet") shouldBe listOf(
            listOf(listOf(1)),
            listOf(listOf(2, 3), listOf(2, 3))
        )
    }

    @Test
    fun `a non-nested list should not be valid for a nullable nested set of Int`() {
        expect<InvalidInputValueException>("Cannot coerce '1' to List") {
            schema.executeBlocking("{ NullableNestedSet(value: [1, 2, 3]) }")
        }
    }

    @Test
    fun `null should not be valid for a required set of Int`() {
        expect<InvalidInputValueException>("Cannot coerce 'null' to Int") {
            schema.executeBlocking("{ RequiredSet(value: null) }")
        }
    }

    @Test
    fun `a list should be valid for a required set of Int`() {
        val response = deserialize(schema.executeBlocking("{ RequiredSet(value: [1, 2, 3]) }"))
        response.extract<List<Int?>>("data/RequiredSet") shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `a list with null value should be valid for a required set of Int`() {
        val response = deserialize(schema.executeBlocking("{ RequiredSet(value: [1, 2, null]) }"))
        response.extract<List<Int?>>("data/RequiredSet") shouldBe listOf(1, 2, null)
    }
}
