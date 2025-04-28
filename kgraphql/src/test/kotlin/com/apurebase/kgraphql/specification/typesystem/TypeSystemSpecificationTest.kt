package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.KGraphQL.Companion.schema
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.schema.SchemaException
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

@Specification("3 Type System")
class TypeSystemSpecificationTest {

    class String
    class Type(val name: kotlin.String)
    class TypeInput(val name: kotlin.String)
    class InputType(val name: kotlin.String)
    class ParentType(val parentName: kotlin.String, val child: ChildType)
    class ChildType(val childName: kotlin.String)
    class __Type

    @Test
    fun `all types within a GraphQL schema must have unique names`() {
        expect<SchemaException>("Cannot add Object type with duplicated name String") {
            schema {
                type<String>()
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
            }
        }
    }

    @Test
    fun `all types and directives defined within a schema must not have a name which begins with __`() {
        expect<SchemaException>("Type name starting with \"__\" are excluded for introspection system") {
            schema {
                type<__Type>()
            }
        }
        expect<SchemaException>("Type name starting with \"__\" are excluded for introspection system") {
            schema {
                type<Type> {
                    name = "__Type"
                }
            }
        }
        expect<SchemaException>("Type name starting with \"__\" are excluded for introspection system") {
            schema {
                inputType<__Type>()
            }
        }
        expect<SchemaException>("Type name starting with \"__\" are excluded for introspection system") {
            schema {
                inputType<Type> {
                    name = "__Type"
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
        sdl shouldBeEqualTo """
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
        sdl shouldBeEqualTo """
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
        sdl shouldBeEqualTo """
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
