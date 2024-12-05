package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.InvalidInputValueException
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

@Specification("3.11 List", "3.12 Non-Null")
// See also ListsSpecificationTest
class ListInputCoercionTest {
    private val schema = defaultSchema {
        query("NullableList") { resolver { value: List<Int?>? -> value } }
        query("NullableNestedList") { resolver { value: List<List<Int?>?>? -> value } }
        query("RequiredList") { resolver { value: List<Int?> -> value } }
    }

    @Test
    fun `null should be valid for a nullable list of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableList(value: null) }"))
        assertThat(response.extract<List<Int>>("data/NullableList"), nullValue())
    }

    @Test
    fun `1 should be valid for a nullable list of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableList(value: 1) }"))
        assertThat(response.extract<List<Int>>("data/NullableList"), equalTo(listOf(1)))
    }

    @Test
    fun `a list should be valid for a nullable list of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableList(value: [1, 2, 3]) }"))
        assertThat(response.extract<List<Int>>("data/NullableList"), equalTo(listOf(1, 2, 3)))
    }

    @Test
    fun `a list of mixed types should not be valid for a nullable list of Int`() {
        invoking {
            deserialize(schema.executeBlocking("{ NullableList(value: [1, \"b\", true]) }"))
        } shouldThrow InvalidInputValueException::class withMessage "Cannot coerce \"b\" to numeric constant"
    }

    @Test
    fun `foo should not be valid for a nullable list of Int`() {
        invoking {
            deserialize(schema.executeBlocking("{ RequiredList(value: \"foo\") }"))
        } shouldThrow InvalidInputValueException::class withMessage "Cannot coerce \"foo\" to numeric constant"
    }

    @Test
    fun `null should be valid for a nullable nested list of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableNestedList(value: null) }"))
        assertThat(response.extract<List<Int>>("data/NullableNestedList"), nullValue())
    }

    @Test
    fun `1 should be valid for a nullable nested list of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableNestedList(value: 1) }"))
        assertThat(response.extract<List<Int>>("data/NullableNestedList"), equalTo(listOf(listOf(1))))
    }

    @Test
    fun `a nested list should be valid for a nullable nested list of Int`() {
        val response = deserialize(schema.executeBlocking("{ NullableNestedList(value: [[1], [2, 3]]) }"))
        assertThat(response.extract<List<Int>>("data/NullableNestedList"), equalTo(listOf(listOf(1), listOf(2, 3))))
    }

    @Test
    fun `a non-nested list should not be valid for a nullable nested list of Int`() {
        invoking {
            val result = deserialize(schema.executeBlocking("{ NullableNestedList(value: [1, 2, 3]) }"))
            println(result)
        } shouldThrow InvalidInputValueException::class withMessage "argument '1' is not valid value of type List"
    }

    @Test
    fun `null should not be valid for a required list of Int`() {
        invoking {
            deserialize(schema.executeBlocking("{ RequiredList(value: null) }"))
        } shouldThrow InvalidInputValueException::class withMessage "argument 'null' is not valid value of type Int"
    }

    @Test
    fun `a list should be valid for a required list of Int`() {
        val response = deserialize(schema.executeBlocking("{ RequiredList(value: [1, 2, 3]) }"))
        assertThat(response.extract<List<Int?>>("data/RequiredList"), equalTo(listOf(1, 2, 3)))
    }

    @Test
    fun `a list with null value should be valid for a required list of Int`() {
        val response = deserialize(schema.executeBlocking("{ RequiredList(value: [1, 2, null]) }"))
        assertThat(response.extract<List<Int?>>("data/RequiredList"), equalTo(listOf(1, 2, null)))
    }
}
