package com.apurebase.kgraphql.specification.introspection

import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.schema.SchemaException
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.reflect.typeOf

class DeprecationSpecificationTest {

    @Test
    fun `queries may be deprecated`() {
        val expected = "sample query"
        val schema = defaultSchema {
            query("sample") {
                deprecate(expected)
                resolver<String> { "SAMPLE" }
            }
        }

        val response =
            deserialize(schema.executeBlocking("{__schema{queryType{fields(includeDeprecated: true){name, deprecationReason, isDeprecated}}}}"))
        response.extract<Map<String, Any?>>("data/__schema/queryType/fields[0]") shouldContainAll mapOf(
            "deprecationReason" to expected,
            "isDeprecated" to true
        )
    }

    @Test
    fun `mutations may be deprecated`() {
        val expected = "sample mutation"
        val schema = defaultSchema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("sample") {
                deprecate(expected)
                resolver<String> { "SAMPLE" }
            }
        }

        val response =
            deserialize(schema.executeBlocking("{__schema{mutationType{fields(includeDeprecated: true){name, deprecationReason, isDeprecated}}}}"))
        response.extract<Map<String, Any?>>("data/__schema/mutationType/fields[0]") shouldContainAll mapOf(
            "deprecationReason" to expected,
            "isDeprecated" to true
        )
    }

    data class Sample(val content: String)

    @Test
    fun `kotlin field may be deprecated`() {
        val expected = "sample type"
        val schema = defaultSchema {
            query("sample") {
                resolver<String> { "SAMPLE" }
            }

            type<Sample> {
                Sample::content.configure {
                    deprecate(expected)
                }
            }
        }

        val response =
            deserialize(schema.executeBlocking("{__type(name: \"Sample\"){fields(includeDeprecated: true){isDeprecated, deprecationReason}}}"))
        response.extract<Map<String, Any?>>("data/__type/fields[0]") shouldContainAll mapOf(
            "deprecationReason" to expected,
            "isDeprecated" to true
        )
    }

    @Test
    fun `extension field may be deprecated`() {
        val expected = "sample type"
        val schema = defaultSchema {
            query("sample") {
                resolver<String> { "SAMPLE" }
            }

            type<Sample> {
                property("add") {
                    deprecate(expected)
                    resolver { (content) -> content.uppercase() }
                }
            }
        }

        val response =
            deserialize(schema.executeBlocking("{__type(name: \"Sample\"){fields(includeDeprecated: true){name, isDeprecated, deprecationReason}}}"))
        response.extract<Map<String, Any?>>("data/__type/fields[1]") shouldContainAll mapOf(
            "deprecationReason" to expected,
            "isDeprecated" to true
        )
    }

    @Suppress("unused")
    enum class SampleEnum { ONE, TWO, THREE }

    @Test
    fun `enum value may be deprecated`() {
        val expected = "some enum value"
        val schema = defaultSchema {
            query("sample") {
                resolver<String> { "SAMPLE" }
            }

            enum<SampleEnum> {
                value(SampleEnum.ONE) {
                    deprecate(expected)
                }
            }
        }

        val response =
            deserialize(schema.executeBlocking("{__type(name: \"SampleEnum\"){enumValues(includeDeprecated: true){name, isDeprecated, deprecationReason}}}"))
        response.extract<Map<String, Any?>>("data/__type/enumValues[0]") shouldContainAll mapOf(
            "name" to SampleEnum.ONE.name,
            "deprecationReason" to expected,
            "isDeprecated" to true
        )
    }

    @Test
    fun `optional input value may be deprecated`() {
        data class InputType(val oldOptional: String?, val new: String)

        val expected = "deprecated input value"
        val schema = defaultSchema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            inputType<InputType> {
                InputType::oldOptional.configure {
                    deprecate(expected)
                }
            }
        }

        val response =
            deserialize(schema.executeBlocking("{__type(name: \"InputType\"){inputFields(includeDeprecated: true){name, deprecationReason, isDeprecated}}}"))
        response.extract<Map<String, Any?>>("data/__type/inputFields[0]") shouldContainAll mapOf(
            "name" to "new",
            "deprecationReason" to null,
            "isDeprecated" to false
        )
        response.extract<Map<String, Any?>>("data/__type/inputFields[1]") shouldContainAll mapOf(
            "name" to "oldOptional",
            "deprecationReason" to expected,
            "isDeprecated" to true
        )
    }

    @Test
    fun `required input value may not be deprecated`() {
        data class InputType(val oldRequired: String, val new: String)

        expect<SchemaException>("Unable to handle input type 'InputType': Required field 'oldRequired' cannot be marked as deprecated") {
            defaultSchema {
                inputType<InputType> {
                    InputType::oldRequired.configure {
                        deprecate("deprecated input value")
                    }
                }
            }
        }
    }

    @Test
    fun `deprecated input values should not be returned by default`() {
        data class InputType(val oldOptional: String?, val new: String)

        val expected = "deprecated input value"
        val schema = defaultSchema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            inputType<InputType> {
                InputType::oldOptional.configure {
                    deprecate(expected)
                }
            }
        }

        val response =
            deserialize(schema.executeBlocking("{__type(name: \"InputType\"){inputFields{name, deprecationReason, isDeprecated}}}"))
        response.extract<Map<String, Any?>>("data/__type/inputFields[0]") shouldContainAll mapOf(
            "name" to "new",
            "deprecationReason" to null,
            "isDeprecated" to false
        )
        // oldOptional should not be returned
        response.contains("data/__type/inputFields[1]") shouldBe false
    }

    @Test
    fun `optional field args may be deprecated`() {
        val expected = "deprecated field arg"

        @Suppress("UNUSED_ANONYMOUS_PARAMETER")
        val schema = defaultSchema {
            query("data") {
                resolver { oldOptional: String?, new: String -> "" }.withArgs {
                    arg(String::class, typeOf<String?>()) { name = "oldOptional"; deprecate(expected) }
                    arg<String> { name = "new" }
                }
            }
        }

        val response =
            deserialize(schema.executeBlocking("{__schema{queryType{fields{name, args(includeDeprecated: true){name deprecationReason isDeprecated}}}}}"))
        response.extract<Map<String, Any?>>("data/__schema/queryType/fields[0]/args[0]") shouldContainAll mapOf(
            "name" to "oldOptional",
            "deprecationReason" to expected,
            "isDeprecated" to true
        )
        response.extract<Map<String, Any?>>("data/__schema/queryType/fields[0]/args[1]") shouldContainAll mapOf(
            "name" to "new",
            "deprecationReason" to null,
            "isDeprecated" to false
        )
    }

    @Test
    fun `required field args may not be deprecated`() {
        @Suppress("UNUSED_ANONYMOUS_PARAMETER")
        expect<SchemaException>("Unable to handle 'query(\"data\")': Required argument 'oldRequired' cannot be marked as deprecated") {
            defaultSchema {
                query("data") {
                    resolver { oldRequired: String, new: String -> "" }.withArgs {
                        arg<String> { name = "oldRequired"; deprecate("deprecated field arg") }
                        arg<String> { name = "new" }
                    }
                }
            }
        }
    }

    @Test
    fun `deprecated field args should not be returned by default`() {
        val expected = "deprecated input value"

        @Suppress("UNUSED_ANONYMOUS_PARAMETER")
        val schema = defaultSchema {
            query("data") {
                resolver { oldOptional: String?, new: String -> "" }.withArgs {
                    arg(String::class, typeOf<String?>()) { name = "oldOptional"; deprecate(expected) }
                    arg<String> { name = "new" }
                }
            }
        }

        val response =
            deserialize(schema.executeBlocking("{__schema{queryType{fields{name, args{name deprecationReason isDeprecated}}}}}"))
        response.extract<Map<String, Any?>>("data/__schema/queryType/fields[0]/args[0]") shouldContainAll mapOf(
            "name" to "new",
            "deprecationReason" to null,
            "isDeprecated" to false
        )
        // oldOptional should not be returned
        response.contains("data/__schema/queryType/fields[0]/args[1]") shouldBe false
    }
}
