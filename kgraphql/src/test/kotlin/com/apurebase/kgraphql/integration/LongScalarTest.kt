package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LongScalarTest {

    @Test
    fun testLongField() {
        val schema = defaultSchema {
            extendedScalars()
            query("long") {
                resolver { -> Long.MAX_VALUE }
            }
        }

        val response = schema.executeBlocking("{ long }")
        val long = deserialize(response).extract<Long>("data/long")
        long shouldBe Long.MAX_VALUE
    }

    @Test
    fun testLongArgument() {
        val schema = defaultSchema {
            extendedScalars()
            query("isLong") {
                resolver { long: Long ->
                    if (long > Int.MAX_VALUE) {
                        "YES"
                    } else {
                        "NO"
                    }
                }
            }
        }

        val isLong = deserialize(
            schema.executeBlocking("{ isLong(long: ${Int.MAX_VALUE.toLong() + 1}) }")
        ).extract<String>("data/isLong")
        isLong shouldBe "YES"
    }

    data class VeryLong(val long: Long)

    @Test
    fun `Schema may declare custom long scalar type`() {
        val schema = KGraphQL.schema {
            longScalar<VeryLong> {
                deserialize = ::VeryLong
                serialize = { (long) -> long }
            }

            query("number") {
                resolver { number: VeryLong -> number }
            }
        }

        val value = Int.MAX_VALUE.toLong() + 2
        val response = deserialize(schema.executeBlocking("{ number(number: $value) }"))
        response.extract<Long>("data/number") shouldBe value
    }
}
