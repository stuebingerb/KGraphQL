package com.apurebase.kgraphql.specification.introspection

import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.assertNoErrors
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.request.Introspection
import com.apurebase.kgraphql.schema.introspection.TypeKind
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldNotBeEqualTo
import org.amshove.kluent.shouldNotContain
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.hamcrest.collection.IsEmptyCollection.empty
import org.junit.jupiter.api.Test

class IntrospectionSpecificationTest {

    @Test
    fun `simple introspection`() {
        val schema = defaultSchema {
            query("sample") {
                resolver { -> "Ronaldinho" }
            }
        }

        assertThat(schema.findTypeByName("String"), notNullValue())
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
        assertThat(response.extract("data/sample/__typename"), equalTo("Data"))
    }

    @Test
    fun `__typename field can be used to obtain type of query`() {
        val schema = defaultSchema {
            query("dummy") {
                resolver { -> "dummy" }
            }
        }

        val response = deserialize(schema.executeBlocking("{__typename}"))
        assertThat(response.extract("data/__typename"), equalTo("Query"))
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
        assertThat(response.extract("data/__typename"), equalTo("Mutation"))
    }

    @Test
    fun `__typename field cannot be used on scalars`() {
        val schema = defaultSchema {
            query("sample") {
                resolver { -> Data("Ronaldingo") }
            }
        }

        invoking {
            schema.executeBlocking("{sample{string{__typename}}}")
        } shouldThrow GraphQLError::class withMessage "Property __typename on String does not exist"
    }

    enum class SampleEnum {
        VALUE
    }
    data class EnumData(val enum: SampleEnum)

    @Test
    fun `__typename field cannot be used on enums`() {
        val schema = defaultSchema {
            enum<SampleEnum>()

            query("sample") {
                resolver { -> EnumData(SampleEnum.VALUE) }
            }
        }

        invoking {
            schema.executeBlocking("{sample{enum{__typename}}}")
        } shouldThrow GraphQLError::class withMessage "Property __typename on SampleEnum does not exist"
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

        assertThat(response.extract("data/data/union/__typename"), equalTo("Union1"))
    }

    @Test
    fun `list and nonnull types are wrapping regular types in introspection system`() {
        val schema = defaultSchema {
            query("data") {
                resolver { -> listOf("BABA") }
            }
        }

        val dataReturnType = schema.queryType.fields?.find { it.name == "data" }?.type!!
        assertThat(dataReturnType.kind, equalTo(TypeKind.NON_NULL))
        assertThat(dataReturnType.ofType?.kind, equalTo(TypeKind.LIST))
        assertThat(dataReturnType.ofType?.ofType?.kind, equalTo(TypeKind.NON_NULL))
    }

    @Test
    fun `field __schema is accessible from the type of the root of a query operation`() {
        val schema = defaultSchema {
            query("data") {
                resolver<String> { "DADA" }
            }
        }

        val response = deserialize(schema.executeBlocking("{__schema{queryType{fields{name}}}}"))
        assertThat(response.extract("data/__schema/queryType/fields[0]/name"), equalTo("data"))
    }

    @Test
    fun `field __types is accessible from the type of the root of a query operation`() {
        val schema = defaultSchema {
            query("data") {
                resolver<String> { "DADA" }
            }
        }

        val response = deserialize(schema.executeBlocking("{__type(name: \"String\"){kind, name, description}}"))
        assertThat(response.extract("data/__type/name"), equalTo("String"))
        assertThat(response.extract("data/__type/kind"), equalTo("SCALAR"))
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

        assertThat(inputValues[0].name, equalTo("int"))
        assertThat(inputValues[0].type.ofType?.name, equalTo("Int"))
        assertThat(inputValues[1].name, equalTo("string"))
        assertThat(inputValues[1].type.ofType?.name, equalTo("String"))
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

        assertThat(inputValues[0].name, equalTo("doubleIt"))
        assertThat(inputValues[0].type.ofType?.name, equalTo("Boolean"))
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
        assertThat(response.extract("data/interface/__typename"), equalTo("Face"))
        assertThat(response.extract("data/interface/value2"), equalTo(false))
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
        assertThat(possibleTypesOfInter, equalTo(listOf("Face")))

        val possibleTypesOfInterInter = schema.findTypeByName("InterInter")?.possibleTypes?.map { it.name }
        assertThat(possibleTypesOfInterInter, equalTo(listOf("Face")))

        val interfacesOfFace = schema.findTypeByName("Face")?.interfaces?.map { it.name }
        assertThat(interfacesOfFace, equalTo(listOf("Inter", "InterInter")))

        val interfacesOfInterInter = schema.findTypeByName("InterInter")?.interfaces?.map { it.name }
        assertThat(interfacesOfInterInter, equalTo(listOf("Inter")))

        val interfacesOfInter = schema.findTypeByName("Inter")?.interfaces?.map { it.name }
        assertThat(interfacesOfInter, equalTo(emptyList()))
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
        assertThat(possibleTypes, equalTo(listOf<String?>("Face", "Book")))
    }

    @Test
    fun `union types should not be duplicated`() {
        val facebookCount = unionSchema.model.allTypes.map { it.name }.count { it == "FaceBook" }
        assertThat(facebookCount, equalTo(1))
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

        fields.forEach { field ->
            assertThat(field["name"] as String, not(startsWith("__")))
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

        val typenames = types.map { type -> type["name"] as String }.sorted()

        for (i in typenames.indices) {
            typenames[i] shouldNotBeEqualTo typenames.getOrNull(i + 1)
        }
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

        assertThat(response.extract<List<*>>("data/__schema/directives"), hasSize(3))

        assertThat(response.extract("data/__schema/directives[0]/name"), equalTo("skip"))
        assertThat(response.extract("data/__schema/directives[0]/isRepeatable"), equalTo(false))
        assertThat(response.extract<List<*>>("data/__schema/directives[0]/args"), hasSize(1))
        assertThat(response.extract("data/__schema/directives[0]/args[0]/name"), equalTo("if"))
        assertThat(response.extract("data/__schema/directives[0]/args[0]/type/kind"), equalTo("NON_NULL"))
        assertThat(response.extract("data/__schema/directives[0]/args[0]/type/ofType/name"), equalTo("Boolean"))
        assertThat(response.extract("data/__schema/directives[0]/args[0]/type/ofType/kind"), equalTo("SCALAR"))

        assertThat(response.extract("data/__schema/directives[1]/name"), equalTo("include"))
        assertThat(response.extract("data/__schema/directives[1]/isRepeatable"), equalTo(false))
        assertThat(response.extract<List<*>>("data/__schema/directives[1]/args"), hasSize(1))
        assertThat(response.extract("data/__schema/directives[1]/args[0]/name"), equalTo("if"))
        assertThat(response.extract("data/__schema/directives[1]/args[0]/type/kind"), equalTo("NON_NULL"))
        assertThat(response.extract("data/__schema/directives[1]/args[0]/type/ofType/name"), equalTo("Boolean"))
        assertThat(response.extract("data/__schema/directives[1]/args[0]/type/ofType/kind"), equalTo("SCALAR"))

        assertThat(response.extract("data/__schema/directives[2]/name"), equalTo("deprecated"))
        assertThat(response.extract("data/__schema/directives[2]/isRepeatable"), equalTo(false))
        assertThat(response.extract<List<*>>("data/__schema/directives[2]/args"), hasSize(1))
        assertThat(response.extract("data/__schema/directives[2]/args[0]/name"), equalTo("reason"))
        assertThat(response.extract("data/__schema/directives[2]/args[0]/type/kind"), equalTo("SCALAR"))
        assertThat(response.extract("data/__schema/directives[2]/args[0]/type/name"), equalTo("String"))
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
        assertThat(response.extract<List<*>>("data/__schema/queryType/interfaces"), empty())
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
        directives.forEach { directive ->
            assertThat(directive["onField"], notNullValue())
            assertThat(directive["onFragment"], notNullValue())
            assertThat(directive["onOperation"], notNullValue())
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
