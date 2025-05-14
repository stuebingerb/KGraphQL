package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.KGraphQL.Companion.schema
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.schema.SchemaException
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Specification("3 Type System")
class TypeSystemSpecificationTest {

    class String(val value: kotlin.String)
    class Type(val name: kotlin.String)
    class TypeInput(val name: kotlin.String)
    class InputType(val name: kotlin.String)
    class ParentType(val parentName: kotlin.String, val child: ChildType)
    class ChildType(val childName: kotlin.String)
    class __Type(val name: kotlin.String)

    @Test
    fun `type names should be case sensitive and underscores should be significant`() {
        val schema = schema {
            query("queryType") {
                resolver { -> Type("type") }
            }
            type<Type> {
                name = "Type"
            }
            type<TypeInput> {
                name = "type"
            }
            type<InputType> {
                name = "TYPE"
            }
            type<String> {
                // Underscores are significant
                name = "ty_pe"
            }
            inputType<Type> {
                name = "TypeInput"
            }
            inputType<TypeInput> {
                name = "typeInput"
            }
            inputType<InputType> {
                name = "TYPEInput"
            }
            inputType<String> {
                name = "type_input"
            }
        }

        val sdl = schema.printSchema()
        sdl shouldBe """
            type Query {
              queryType: Type!
            }
            
            type ty_pe {
              value: String!
            }
            
            type Type {
              name: String!
            }
            
            type type {
              name: String!
            }
            
            type TYPE {
              name: String!
            }
            
            input type_input {
              value: String!
            }

            input TypeInput {
              name: String!
            }

            input typeInput {
              name: String!
            }

            input TYPEInput {
              name: String!
            }
            
        """.trimIndent()
    }

    @ParameterizedTest
    @ValueSource(strings = ["name", "Name", "NAME", "legal_name", "name1", "nameWithNumber_42", "_underscore", "_more_under_scores_", "even__", "more__underscores"])
    @Specification("2.1.9 Names")
    fun `legal names should be possible`(legalName: kotlin.String) {
        val schema = schema {
            type<Type> {
                name = legalName
            }
            query("queryType") {
                resolver { -> Type("type") }
            }
        }
        schema.types.map { it.name } shouldContain legalName
    }

    @ParameterizedTest
    @ValueSource(strings = ["special!", "1special", "big$", "ßpecial", "<UNK>pecial", "42", "speciäl"])
    @Specification("2.1.9 Names")
    fun `illegal names should result in an appropriate exception`(illegalName: kotlin.String) {
        expect<SchemaException>("Illegal name '$illegalName'. Names must start with a letter or underscore, and may only contain [_a-zA-Z0-9]") {
            schema {
                type<Type> {
                    name = illegalName
                }
                query("queryType") {
                    resolver { -> Type("type") }
                }
            }
        }
    }

    @Test
    fun `all types within a GraphQL schema must have unique names`() {
        expect<SchemaException>("Cannot add Object type with duplicated name String") {
            schema {
                type<String>()
                query("getString") {
                    resolver { -> String("string") }
                }
            }
        }
        expect<SchemaException>("Cannot add Object type with duplicated name String") {
            schema {
                type<Type> {
                    name = "String"
                }
            }
        }
        expect<SchemaException>("Cannot add Input type with duplicated name String") {
            schema {
                inputType<Type> {
                    name = "String"
                }
            }
        }
        expect<SchemaException>("Cannot add Input type with duplicated name Type") {
            schema {
                type<Type>()
                inputType<Type>()
                query("getString") {
                    resolver { -> Type("string") }
                }
            }
        }
        expect<SchemaException>("Cannot add Input type with duplicated name Type") {
            schema {
                type<Type>()
                inputType<TypeInput> {
                    name = "Type"
                }
            }
        }
        expect<SchemaException>("Cannot add Input type with duplicated name TypeInput") {
            schema {
                type<Type> {
                    name = "TypeInput"
                }
                inputType<TypeInput>()
            }
        }
        expect<SchemaException>("Cannot add Input type with duplicated name TypeInput") {
            schema {
                query("test") {
                    resolver { input: TypeInput -> input }
                }
            }
        }
    }

    @Test
    fun `all types and directives defined within a schema must not have a name which begins with __`() {
        expect<SchemaException>("Illegal name '__Type'. Names starting with '__' are reserved for introspection system") {
            schema {
                type<__Type>()
            }
        }
        expect<SchemaException>("Illegal name '__Type'. Names starting with '__' are reserved for introspection system") {
            schema {
                type<Type> {
                    name = "__Type"
                }
            }
        }
        expect<SchemaException>("Illegal name '__Type'. Names starting with '__' are reserved for introspection system") {
            schema {
                inputType<__Type>()
            }
        }
        expect<SchemaException>("Illegal name '__Type'. Names starting with '__' are reserved for introspection system") {
            schema {
                inputType<Type> {
                    name = "__Type"
                }
            }
        }
        expect<SchemaException>("Illegal name '__Type'. Names starting with '__' are reserved for introspection system") {
            schema {
                query("testQuery") {
                    resolver { -> __Type("name") }
                }
            }
        }
        expect<SchemaException>("Illegal name '__TypeInput'. Names starting with '__' are reserved for introspection system") {
            schema {
                query("testQuery") {
                    resolver { -> Type("name") }
                }
                mutation("testMutation") {
                    resolver { input: __Type -> Type(input.name) }
                }
            }
        }
    }

    @Test
    fun `input types should automatically get Input suffix if needed`() {
        val schema = schema {
            query("queryType") {
                resolver { -> Type("type") }
            }
            query("queryParent") {
                resolver { -> ParentType("parent", ChildType("child")) }
            }
            mutation("addType") {
                resolver { input: Type -> input }
            }
            mutation("addParent") {
                resolver { input: ParentType -> input }
            }
        }

        val sdl = schema.printSchema()
        sdl shouldBe """
            type ChildType {
              childName: String!
            }
            
            type Mutation {
              addParent(input: ParentTypeInput!): ParentType!
              addType(input: TypeInput!): Type!
            }
            
            type ParentType {
              child: ChildType!
              parentName: String!
            }
            
            type Query {
              queryParent: ParentType!
              queryType: Type!
            }
            
            type Type {
              name: String!
            }
            
            input ChildTypeInput {
              childName: String!
            }
            
            input ParentTypeInput {
              child: ChildTypeInput!
              parentName: String!
            }
            
            input TypeInput {
              name: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `input types should not get an additional Input suffix if already present`() {
        val schema = schema {
            query("queryType") {
                resolver { -> Type("type") }
            }
            mutation("addType") {
                // input type already ends with "Input" and should not become "TypeInputInput"
                resolver { input: TypeInput -> Type(input.name) }
            }
        }

        val sdl = schema.printSchema()
        sdl shouldBe """
            type Mutation {
              addType(input: TypeInput!): Type!
            }
            
            type Query {
              queryType: Type!
            }
            
            type Type {
              name: String!
            }
            
            input TypeInput {
              name: String!
            }
            
        """.trimIndent()
    }

    @Test
    fun `input types should not get an Input suffix when they are explicitly configured`() {
        val schema = schema {
            query("queryType") {
                resolver { -> Type("type") }
            }
            query("queryParent") {
                resolver { -> ParentType("parent", ChildType("child")) }
            }
            mutation("addType") {
                resolver { input: InputType -> Type(input.name) }
            }
            mutation("addParent") {
                resolver { input: ParentType -> input }
            }
            // Input type does not even have to specify a custom name; being configured is enough
            // to keep its original name ("InputType" in this case)
            inputType<InputType>()
            inputType<ParentType> {
                // Name does not end with "Input" but is explicitly configured and should stay as-is
                // Child name inside ParentType should still get the "Input" suffix
                name = "MyParentInputType"
            }
        }

        val sdl = schema.printSchema()
        sdl shouldBe """
            type ChildType {
              childName: String!
            }
            
            type Mutation {
              addParent(input: MyParentInputType!): ParentType!
              addType(input: InputType!): Type!
            }
            
            type ParentType {
              child: ChildType!
              parentName: String!
            }
            
            type Query {
              queryParent: ParentType!
              queryType: Type!
            }
            
            type Type {
              name: String!
            }
            
            input ChildTypeInput {
              childName: String!
            }
            
            input InputType {
              name: String!
            }
            
            input MyParentInputType {
              child: ChildTypeInput!
              parentName: String!
            }
            
        """.trimIndent()
    }
}
