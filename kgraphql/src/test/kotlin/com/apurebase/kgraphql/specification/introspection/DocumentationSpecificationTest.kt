package com.apurebase.kgraphql.specification.introspection

import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DocumentationSpecificationTest {

    @Test
    suspend fun `queries may be documented`() {
        val expected = "sample query"
        val schema = defaultSchema {
            query("sample") {
                description = expected
                resolver<String> { "SAMPLE" }
            }
        }

        val response = deserialize(schema.execute("{__schema{queryType{fields{name, description}}}}"))
        response.extract<String>("data/__schema/queryType/fields[0]/description") shouldBe expected
    }

    @Test
    suspend fun `mutations may be documented`() {
        val expected = "sample mutation"
        val schema = defaultSchema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("sample") {
                description = expected
                resolver<String> { "SAMPLE" }
            }
        }

        val response = deserialize(schema.execute("{__schema{mutationType{fields{name, description}}}}"))
        response.extract<String>("data/__schema/mutationType/fields[0]/description") shouldBe expected
    }

    data class Sample(val content: String)

    @Test
    suspend fun `object type may be documented`() {
        val expected = "sample type"
        val schema = defaultSchema {
            query("sample") {
                resolver<Sample> { Sample("SAMPLE") }
            }
            type<Sample> {
                description = expected
            }
        }

        val response = deserialize(schema.execute("{__type(name: \"Sample\"){description}}"))
        response.extract<String>("data/__type/description/") shouldBe expected
    }

    @Test
    suspend fun `input type may be documented`() {
        val expected = "sample type"
        val schema = defaultSchema {
            query("sample") {
                resolver<String> { "SAMPLE" }
            }

            inputType<Sample> {
                description = expected
            }
        }

        val response = deserialize(schema.execute("{__type(name: \"Sample\"){description}}"))
        response.extract<String>("data/__type/description/") shouldBe expected
    }

    @Test
    suspend fun `kotlin field may be documented`() {
        val expected = "sample type"
        val schema = defaultSchema {
            query("sample") {
                resolver<String> { "SAMPLE" }
            }

            type<Sample> {
                Sample::content.configure {
                    description = expected
                }
            }
        }

        val response = deserialize(schema.execute("{__type(name: \"Sample\"){fields{description}}}"))
        response.extract<String>("data/__type/fields[0]/description/") shouldBe expected
    }

    @Test
    suspend fun `extension field may be documented`() {
        val expected = "sample type"
        val schema = defaultSchema {
            query("sample") {
                resolver<String> { "SAMPLE" }
            }

            type<Sample> {
                property("add") {
                    description = expected
                    resolver { (content) -> content.uppercase() }
                }
            }
        }

        val response = deserialize(schema.execute("{__type(name: \"Sample\"){fields{name, description}}}"))
        response.extract<Map<String, Any?>>("data/__type/fields[1]") shouldContainAll mapOf(
            "name" to "add",
            "description" to expected
        )
    }

    enum class SampleEnum { ONE, TWO, THREE }

    @Test
    suspend fun `enum value may be documented`() {
        val expected = "some enum value"
        val schema = defaultSchema {
            query("sample") {
                resolver<String> { "SAMPLE" }
            }

            enum<SampleEnum> {
                value(SampleEnum.ONE) {
                    description = expected
                }
            }
        }

        val response =
            deserialize(schema.execute("{__type(name: \"SampleEnum\"){enumValues{name, description}}}"))
        response.extract<Map<String, Any?>>("data/__type/enumValues[0]") shouldContainAll mapOf(
            "name" to SampleEnum.ONE.name,
            "description" to expected
        )
    }

    data class Documented(val id: Int)

    @Test
    suspend fun `type may be documented`() {
        val expected = "very documented type"
        val schema = defaultSchema {
            query("documented") {
                resolver { -> Documented(1) }
            }

            type<Documented> {
                description = "very documented type"
            }
        }

        val response =
            deserialize(schema.execute("query { __type(name: \"Documented\") {  name, kind, description } }"))
        response.extract<String>("data/__type/description") shouldBe expected
    }
}
