package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.*
import com.apurebase.kgraphql.GraphQLError
import org.amshove.kluent.*
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

@Specification("3.1.8 Non-null")
class NonNullSpecificationTest {

    @Test
    fun `if the result of non-null type is null, error should be raised`(){
        val schema = KGraphQL.schema {
            query("nonNull"){
                resolver { string : String? -> string!! }
            }
        }
        expect<NullPointerException> {
            schema.executeBlocking("{nonNull}")
        }
    }

    @Test
    fun `nullable input types are always optional`(){
        val schema = KGraphQL.schema {
            query("nullable"){
                resolver { input: String? -> input }
            }
        }

        val responseOmittedInput = deserialize(schema.executeBlocking("{nullable}"))
        assertThat(responseOmittedInput.extract<Any?>("data/nullable"), nullValue())

        val responseNullInput = deserialize(schema.executeBlocking("{nullable(input: null)}"))
        assertThat(responseNullInput.extract<Any?>("data/nullable"), nullValue())
    }

    @Test
    fun `non-null types are always required`() {
        val schema = KGraphQL.schema {
            query("nonNull"){
                resolver { input: String -> input }
            }
        }
        invoking {
            schema.executeBlocking("{nonNull}")
        } shouldThrow GraphQLError::class with {
            message shouldEqual "Missing value for non-nullable argument input on the field 'nonNull'"
        }
    }

    @Test
    fun `variable of a nullable type cannot be provided to a non-null argument`(){
        val schema = KGraphQL.schema {
            query("nonNull"){
                resolver { input: String -> input }
            }
        }

        schema.executeBlocking("query(\$arg: String!){nonNull(input: \$arg)}", "{\"arg\":\"SAD\"}")
    }

}
