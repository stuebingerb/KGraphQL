package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.*
import com.apurebase.kgraphql.integration.BaseSchemaTest
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.jol.error.GraphQLError
import org.amshove.kluent.*
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Test


@Specification("3.1.4 Unions")
class UnionsSpecificationTest : BaseSchemaTest() {

    @Test
    fun `query union property`(){
        val map = execute("{actors{name, favourite{ ... on Actor {name}, ... on Director {name age}, ... on Scenario{content(uppercase: false)}}}}", null)
        for(i in 0..4){
            val name = map.extract<String>("data/actors[$i]/name")
            val favourite = map.extract<Map<String, String>>("data/actors[$i]/favourite")
            when(name){
                "Brad Pitt" -> MatcherAssert.assertThat(favourite, CoreMatchers.equalTo(mapOf("name" to "Tom Hardy")))
                "Tom Hardy" -> MatcherAssert.assertThat(favourite, CoreMatchers.equalTo(mapOf("age" to 43, "name" to "Christopher Nolan")))
                "Morgan Freeman" -> MatcherAssert.assertThat(favourite, CoreMatchers.equalTo(mapOf("content" to "DUMB")))
            }
        }
    }

    @Test
    fun `query union property with external fragment`(){
        val map = execute("{actors{name, favourite{ ...actor, ...director, ...scenario }}}" +
                "fragment actor on Actor {name}" +
                "fragment director on Director {name age}" +
                "fragment scenario on Scenario{content(uppercase: false)} ", null)
        for(i in 0..4){
            val name = map.extract<String>("data/actors[$i]/name")
            val favourite = map.extract<Map<String, String>>("data/actors[$i]/favourite")
            when(name){
                "Brad Pitt" -> MatcherAssert.assertThat(favourite, CoreMatchers.equalTo(mapOf("name" to "Tom Hardy")))
                "Tom Hardy" -> MatcherAssert.assertThat(favourite, CoreMatchers.equalTo(mapOf("age" to 43, "name" to "Christopher Nolan")))
                "Morgan Freeman" -> MatcherAssert.assertThat(favourite, CoreMatchers.equalTo(mapOf("content" to "DUMB")))
            }
        }
    }

    @Test
    fun `query union property with invalid selection set`(){
        invoking {
            execute("{actors{name, favourite{ name }}}")
        } shouldThrow GraphQLError::class with {
            message shouldEqual "Invalid selection set with properties: [name] on union type property favourite : [Actor, Scenario, Director]"
        }
    }

    @Test
    fun `A Union type should allow requesting __typename`() {
        val result = execute("""{
            actors {
                name
                favourite {
                    ... on Actor { name }
                    ... on Director { name, age }
                    ... on Scenario { content(uppercase: false) }
                    __typename
                }
            }
        }""".trimIndent())
        println(result)
    }

    @Test
    fun `Nullable union types should be valid`() {
        val result = execute(
            """{
          actors(all: true) {
            name
            nullableFavourite {
              ... on Actor { name }
              ... on Director { name, age }
              ... on Scenario { content(uppercase: false) }
            }
          }
        }""".trimIndent())

        MatcherAssert.assertThat(
            result.extract<String>("data/actors[5]/name"),
            CoreMatchers.equalTo(rickyGervais.name)
        )
        MatcherAssert.assertThat(
            result.extract("data/actors[5]/nullableFavourite"),
            CoreMatchers.equalTo(null)
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[3]/name"),
            CoreMatchers.equalTo(tomHardy.name)
        )
        MatcherAssert.assertThat(
            result.extract<String>("data/actors[3]/nullableFavourite/name"),
            CoreMatchers.equalTo(christopherNolan.name)
        )
    }

    @Test
    fun `Non nullable union types should fail`() {
        invoking {
            execute("""{
                actors(all: true) {
                    name
                    favourite {
                        ... on Actor { name }
                        ... on Director { name, age }
                        ... on Scenario { content(uppercase: false) }
                    }
                }
            }""".trimIndent())
        } shouldThrow ExecutionException::class with {
            println(prettyPrint())
            message shouldEqual "Unexpected type of union property value, expected one of: [Actor, Scenario, Director]. value was null"
        }
    }

    @Test
    fun `The member types of a Union type must all be Object base types`(){
        expect<SchemaException>("The member types of a Union type must all be Object base types"){
            KGraphQL.schema {
                unionType("invalid") {
                    type<String>()
                }
            }
        }
    }

    @Test
    fun `A Union type must define one or more unique member types`(){
        expect<SchemaException>("A Union type must define one or more unique member types"){
            KGraphQL.schema {
                unionType("invalid") {}
            }
        }
    }

    /**
     * Kotlin is non-nullable by default (T!), so test covers only case for collections
     */
    @Test
    fun `List may not be member type of a Union`(){
        expect<SchemaException>("Collection may not be member type of a Union 'Invalid'"){
            KGraphQL.schema {
                unionType("Invalid") {
                    type<Collection<*>>()
                }
            }
        }

        expect<SchemaException>("Map may not be member type of a Union 'Invalid'"){
            KGraphQL.schema {
                unionType("Invalid") {
                    type<Map<String, String>>()
                }
            }
        }
    }

    @Test
    fun `Function type may not be member types of a Union`(){
        expect<SchemaException>("Cannot handle function class kotlin.Function as Object type"){
            KGraphQL.schema {
                unionType("invalid") {
                    type<Function<*>>()
                }
            }
        }
    }
}
