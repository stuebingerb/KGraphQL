package com.apurebase.kgraphql.stitched

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.request.Introspection
import com.apurebase.kgraphql.schema.SchemaPrinter
import com.apurebase.kgraphql.schema.stitched.IntrospectedSchema
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class IntrospectedSchemaTest {
    data class TestObject(val name: String)

    @Suppress("unused")
    enum class TestEnum {
        TYPE1, TYPE2
    }

    @Test
    fun `introspected schema should result in the same SDL as the schema itself`() {
        val schema = KGraphQL.schema {
            extendedScalars()

            query("getObject") {
                resolver { -> TestObject("dummy") }
            }
            query("getEnum") {
                resolver { -> TestEnum.TYPE1 }
            }
            inputType<TestObject> {
                name = "TestObjectInput"
            }
            mutation("add") {
                resolver { input: TestObject -> input }
            }
            enum<TestEnum>()
        }

        val schemaFromIntrospection = IntrospectedSchema.fromIntrospectionResponse(
            schema.executeBlocking(Introspection.query())
        )

        SchemaPrinter().print(schemaFromIntrospection) shouldBeEqualTo SchemaPrinter().print(schema)
    }
}
