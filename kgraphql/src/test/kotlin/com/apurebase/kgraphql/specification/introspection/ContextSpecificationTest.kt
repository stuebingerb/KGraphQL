package com.apurebase.kgraphql.specification.introspection

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ContextSpecificationTest {

    @Test
    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    fun `query resolver should not return context param`() {
        val schema = defaultSchema {
            query("sample") {
                resolver { ctx: Context, limit: Int -> "SAMPLE" }
            }
        }

        val response = deserialize(schema.executeBlocking("{__schema{queryType{fields{args{name}}}}}"))
        response.extract<String>("data/__schema/queryType/fields[0]/args[0]/name") shouldBe "limit"
    }

    @Test
    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    fun `mutation resolver should not return context param`() {
        val schema = defaultSchema {
            query("sample") {
                resolver { -> "dummy" }
            }
            mutation("sample") {
                resolver { ctx: Context, input: String -> "SAMPLE" }
            }
        }

        val response = deserialize(schema.executeBlocking("{__schema{mutationType{fields{args{name}}}}}"))
        response.extract<String>("data/__schema/mutationType/fields[0]/args[0]/name") shouldBe "input"
    }
}
