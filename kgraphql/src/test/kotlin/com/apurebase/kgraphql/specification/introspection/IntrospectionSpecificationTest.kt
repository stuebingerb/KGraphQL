package com.apurebase.kgraphql.specification.introspection

import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.assertNoErrors
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.request.Introspection
import com.apurebase.kgraphql.schema.introspection.TypeKind
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldNotStartWith
import org.junit.jupiter.api.Test

class IntrospectionSpecificationTest {

    @Test
    fun `simple introspection`() {
        val schema = defaultSchema {
            query("sample") {
                resolver { -> "Ronaldinho" }
            }
        }

        schema.findTypeByName("String") shouldNotBe null
    }

    data class Data(val string: String)

    @Test
    fun `__typename field can be used to obtain type of object`() {
        val schema = defaultSchema {
            query("sample") {
                resolver { -> Data("Ronaldingo") }
            }
        }

        val response = deserialize(schema.executeBlocking("{sample{string, __typename}}"))
        response.extract<String>("data/sample/__typename") shouldBe "Data"
    }

    @Test
    fun `__typename field can be used to obtain type of query`() {
        val schema = defaultSchema {
            query("dummy") {
                resolver { -> "dummy" }
            }
        }

        val response = deserialize(schema.executeBlocking("{__typename}"))
        response.extract<String>("data/__typename") shouldBe "Query"
    }

    @Test
    fun `__typename field can be used to obtain type of mutation`() {
        val schema = defaultSchema {
            query("dummy") {
                resolver { -> "dummy" }
            }
            mutation("sample") {
                resolver { -> Data("Ronaldingo") }
            }
        }

        val response = deserialize(schema.executeBlocking("mutation {__typename}"))
        response.extract<String>("data/__typename") shouldBe "Mutation"
    }

    @Test
    fun `__typename field cannot be used on scalars`() {
        val schema = defaultSchema {
            query("sample") {
                resolver { -> Data("Ronaldingo") }
            }
        }

        expect<ValidationException>("Property __typename on String does not exist") {
            schema.executeBlocking("{sample{string{__typename}}}")
        }
    }

    enum class SampleEnum {
        VALUE
    }

    data class EnumData(val enum: SampleEnum)

    @Test
    fun `__typename field cannot be used on enums`() {
        val schema = defaultSchema {
            query("sample") {
                resolver { -> EnumData(SampleEnum.VALUE) }
            }
        }

        expect<ValidationException>("Property __typename on SampleEnum does not exist") {
            schema.executeBlocking("{sample{enum{__typename}}}")
        }
    }

    data class Union1(val one: String)

    data class Union2(val two: String)

    @Test
    fun `__typename field can be used to obtain type of union member in runtime`() {
        val schema = defaultSchema {
            type<Data> {
                unionProperty("union") {
                    returnType = unionType("UNION") {
                        type<Union1>()
                        type<Union2>()
                    }

                    resolver { (string) ->
                        if (string.isEmpty()) {
                            Union1("!!")
                        } else {
                            Union2("??")
                        }
                    }
                }
            }

            query("data") {
                resolver { input: String -> Data(input) }
            }
        }

        val response = deserialize(
            schema.executeBlocking(
                """
                {
                  data(input: "") {
                    string, 
                    union {
                      ...on Union1 { one, __typename }
                      ...on Union2 { two }
                    }
                  }
                }     
                """.trimIndent()
            )
        )

        response.extract<String>("data/data/union/__typename") shouldBe "Union1"
    }

    @Test
    fun `list and nonnull types are wrapping regular types in introspection system`() {
        val schema = defaultSchema {
            query("data") {
                resolver { -> listOf("BABA") }
            }
        }

        val dataReturnType = schema.queryType.fields?.find { it.name == "data" }?.type!!
        dataReturnType.kind shouldBe TypeKind.NON_NULL
        dataReturnType.ofType?.kind shouldBe TypeKind.LIST
        dataReturnType.ofType?.ofType?.kind shouldBe TypeKind.NON_NULL
    }

    @Test
    fun `field __schema is accessible from the type of the root of a query operation`() {
        val schema = defaultSchema {
            query("data") {
                resolver<String> { "DADA" }
            }
        }

        val response = deserialize(schema.executeBlocking("{__schema{queryType{fields{name}}}}"))
        response.extract<String>("data/__schema/queryType/fields[0]/name") shouldBe "data"
    }

    @Test
    fun `field __types is accessible from the type of the root of a query operation`() {
        val schema = defaultSchema {
            query("data") {
                resolver<String> { "DADA" }
            }
        }

        val response = deserialize(schema.executeBlocking("{__type(name: \"String\"){kind, name, description}}"))
        response.extract<String>("data/__type/name") shouldBe "String"
        response.extract<String>("data/__type/kind") shouldBe "SCALAR"
    }

    data class IntString(val int: Int, val string: String)

    @Test
    fun `operation args are introspected`() {
        val schema = defaultSchema {
            query("data") {
                resolver { int: Int, string: String -> IntString(int, string) }
            }
        }

        val inputValues = schema.queryType.fields?.first()?.args
            ?: throw AssertionError("Expected non null field")

        inputValues[0].name shouldBe "int"
        inputValues[0].type.ofType?.name shouldBe "Int"
        inputValues[1].name shouldBe "string"
        inputValues[1].type.ofType?.name shouldBe "String"
    }

    @Test
    fun `fields args are introspected`() {
        val schema = defaultSchema {
            query("data") {
                resolver { int: Int, string: String -> IntString(int, string) }
            }

            type<IntString> {
                property("float") {
                    resolver { (int), doubleIt: Boolean -> int.toDouble() * if (doubleIt) 2 else 1 }
                }
            }
        }

        val inputValues = schema.findTypeByName("IntString")?.fields?.find { it.name == "float" }?.args
            ?: throw AssertionError("Expected non null field")

        inputValues[0].name shouldBe "doubleIt"
        inputValues[0].type.ofType?.name shouldBe "Boolean"
    }

    interface Inter {
        val value: String
    }

    class Face(override val value: String, override val value2: Boolean = false) : InterInter

    @Test
    fun `__typename returns actual type of object`() {
        val schema = defaultSchema {
            query("interface") {
                resolver { ->
                    @Suppress("USELESS_CAST")
                    Face("~~MOCK~~") as Inter
                }
            }

            type<Inter>()
            type<Face>()
        }

        val response = deserialize(schema.executeBlocking("{interface{value, __typename ... on Face{value2}}}"))
        response.extract<String>("data/interface/__typename") shouldBe "Face"
        response.extract<Boolean>("data/interface/value2") shouldBe false
    }

    interface InterInter : Inter {
        val value2: Boolean
    }

    @Test
    fun `Interfaces are supported in introspection`() {
        val schema = defaultSchema {
            query("interface") {
                resolver { -> Face("~~MOCK~~") }
            }

            type<Inter>()
            type<InterInter>()
            type<Face>()
        }

        val possibleTypesOfInter = schema.findTypeByName("Inter")?.possibleTypes?.map { it.name }
        possibleTypesOfInter shouldBe listOf("Face")

        val possibleTypesOfInterInter = schema.findTypeByName("InterInter")?.possibleTypes?.map { it.name }
        possibleTypesOfInterInter shouldBe listOf("Face")

        val interfacesOfFace = schema.findTypeByName("Face")?.interfaces?.map { it.name }
        interfacesOfFace shouldBe listOf("Inter", "InterInter")

        val interfacesOfInterInter = schema.findTypeByName("InterInter")?.interfaces?.map { it.name }
        interfacesOfInterInter shouldBe listOf("Inter")

        val interfacesOfInter = schema.findTypeByName("Inter")?.interfaces?.map { it.name }
        interfacesOfInter.shouldBeEmpty()
    }

    data class Book(val id: String)

    private val unionSchema = defaultSchema {
        query("interface") {
            resolver { -> Face("~~MOCK~~") }
        }

        type<Face> {
            unionProperty("union") {
                returnType = unionType("FaceBook") {
                    type<Face>()
                    type<Book>()
                }

                resolver { Book(it.value) }
            }
        }
    }

    @Test
    fun `union types possible types are supported`() {
        val possibleTypes = unionSchema.findTypeByName("FaceBook")?.possibleTypes?.map { it.name }
        possibleTypes shouldBe listOf("Face", "Book")
    }

    @Test
    fun `union types should not be duplicated`() {
        val facebookCount = unionSchema.model.allTypes.map { it.name }.count { it == "FaceBook" }
        facebookCount shouldBe 1
    }

    @Test
    fun `introspection field __typename must not leak into schema introspection`() {
        val schema = defaultSchema {
            query("interface") {
                resolver { -> Face("~~MOCK~~") }
            }
        }

        val map = deserialize(schema.executeBlocking(Introspection.query()))
        val fields = map.extract<List<Map<String, *>>>("data/__schema/types[0]/fields")

        fields.forAll { field ->
            field["name"] as String shouldNotStartWith "__"
        }
    }

    @Test
    fun `introspection types should not contain duplicated float type for kotlin Double and Float`() {
        val schema = defaultSchema {
            query("interface") {
                resolver { -> Face("~~MOCK~~") }
            }
        }

        val map = deserialize(schema.executeBlocking(Introspection.query()))
        val types = map.extract<List<Map<Any, *>>>("data/__schema/types")

        val typenames = types.map { type -> type["name"] as String }
        typenames.shouldBeUnique()
    }

    @Test
    fun `introspection shouldn't contain LookupSchema nor SchemaProxy`() {
        unionSchema.executeBlocking(Introspection.query()).run {
            this shouldNotContain "LookupSchema"
            this shouldNotContain "SchemaProxy"
        }
    }

    @Test
    fun `__Directive introspection should return all built-in directives as expected`() {
        val schema = defaultSchema {
            query("interface") {
                resolver { -> Face("~~MOCK~~") }
            }
        }

        val response = deserialize(
            schema.executeBlocking(
                "{__schema{directives{name isRepeatable args{name type{name kind ofType{name kind}}}}}}"
            )
        )

        response.extract<List<*>>("data/__schema/directives") shouldHaveSize 3

        response.extract<String>("data/__schema/directives[0]/name") shouldBe "skip"
        response.extract<Boolean>("data/__schema/directives[0]/isRepeatable") shouldBe false
        response.extract<List<*>>("data/__schema/directives[0]/args") shouldHaveSize 1
        response.extract<String>("data/__schema/directives[0]/args[0]/name") shouldBe "if"
        response.extract<String>("data/__schema/directives[0]/args[0]/type/kind") shouldBe "NON_NULL"
        response.extract<String>("data/__schema/directives[0]/args[0]/type/ofType/name") shouldBe "Boolean"
        response.extract<String>("data/__schema/directives[0]/args[0]/type/ofType/kind") shouldBe "SCALAR"

        response.extract<String>("data/__schema/directives[1]/name") shouldBe "include"
        response.extract<Boolean>("data/__schema/directives[1]/isRepeatable") shouldBe false
        response.extract<List<*>>("data/__schema/directives[1]/args") shouldHaveSize 1
        response.extract<String>("data/__schema/directives[1]/args[0]/name") shouldBe "if"
        response.extract<String>("data/__schema/directives[1]/args[0]/type/kind") shouldBe "NON_NULL"
        response.extract<String>("data/__schema/directives[1]/args[0]/type/ofType/name") shouldBe "Boolean"
        response.extract<String>("data/__schema/directives[1]/args[0]/type/ofType/kind") shouldBe "SCALAR"

        response.extract<String>("data/__schema/directives[2]/name") shouldBe "deprecated"
        response.extract<Boolean>("data/__schema/directives[2]/isRepeatable") shouldBe false
        response.extract<List<*>>("data/__schema/directives[2]/args") shouldHaveSize 1
        response.extract<String>("data/__schema/directives[2]/args[0]/name") shouldBe "reason"
        response.extract<String>("data/__schema/directives[2]/args[0]/type/name") shouldBe "String"
        response.extract<String>("data/__schema/directives[2]/args[0]/type/kind") shouldBe "SCALAR"
    }

    /**
     * Not part of spec, but assumption of many graphql tools
     */
    @Test
    fun `query type should have non null, empty interface list`() {
        val schema = defaultSchema {
            query("interface") {
                resolver { -> Face("~~MOCK~~") }
            }
        }

        val response = deserialize(schema.executeBlocking("{__schema{queryType{interfaces{name}}}}"))
        response.extract<List<*>>("data/__schema/queryType/interfaces").shouldBeEmpty()
    }

    /**
     * Not part of spec, but assumption of many graphql tools
     */
    @Test
    fun `__Directive introspection type should have onField, onFragment, onOperation fields`() {
        val schema = defaultSchema {
            query("interface") {
                resolver { -> Face("~~MOCK~~") }
            }
        }

        val response =
            deserialize(schema.executeBlocking("{__schema{directives{name, onField, onFragment, onOperation}}}"))
        val directives = response.extract<List<Map<String, *>>>("data/__schema/directives")
        directives.forAll { directive ->
            directive["onField"] shouldNotBe null
            directive["onFragment"] shouldNotBe null
            directive["onOperation"] shouldNotBe null
        }
    }

    @Test
    fun `all available SpecLevels of the introspection query should return without errors`() {
        Introspection.SpecLevel.entries.forEach { _ ->
            val schema = defaultSchema {
                query("sample") {
                    resolver { -> "Ronaldinho" }
                }
            }

            val response = deserialize(schema.executeBlocking(Introspection.query()))
            assertNoErrors(response)
        }
    }
}
