package com.apurebase.kgraphql.jol

import com.apurebase.kgraphql.jol.ResourceFiles.kitchenSinkQuery
import com.apurebase.kgraphql.schema.jol.Parser
import com.apurebase.kgraphql.schema.jol.ast.*
import com.apurebase.kgraphql.schema.jol.ast.DefinitionNode.ExecutableDefinitionNode.OperationDefinitionNode
import com.apurebase.kgraphql.schema.jol.ast.OperationTypeNode.QUERY
import com.apurebase.kgraphql.schema.jol.ast.SelectionNode.FieldNode
import com.apurebase.kgraphql.schema.jol.ast.TokenKindEnum.EOF
import com.apurebase.kgraphql.schema.jol.ast.TokenKindEnum.SOF
import com.apurebase.kgraphql.schema.jol.ast.TypeNode.NonNullTypeNode
import com.apurebase.kgraphql.schema.jol.ast.TypeNode.ListTypeNode
import com.apurebase.kgraphql.schema.jol.ast.TypeNode.NamedTypeNode
import com.apurebase.kgraphql.schema.jol.ast.ValueNode.*
import com.apurebase.kgraphql.schema.jol.error.GraphQLError
import org.amshove.kluent.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParserTest {

    private fun parse(source: String, options: Parser.Options? = null) = try {
        Parser(source, options).parseDocument()
    } catch (e: GraphQLError) {
        println(e.prettyPrint())
        throw e
    }
    private fun parse(source: Source) = Parser(source).parseDocument()

    private fun parseValue(source: String): ValueNode {
        val parser = Parser(source)
        parser.expectToken(SOF)
        val value = parser.parseValueLiteral(false)
        parser.expectToken(EOF)
        return value
    }

    private fun parseType(source: String): TypeNode {
        val parser = Parser(source)
        parser.expectToken(SOF)
        val type = parser.parseTypeReference()
        parser.expectToken(EOF)
        return type
    }

    private fun shouldThrowSyntaxError(
        src: Source,
        block: GraphQLError.() -> Pair<Int, Int>?
    ) = invoking { parse(src) } shouldThrow GraphQLError::class with {
        block()?.let {
            locations!!.size shouldEqual 1
            locations!!.first().run {
                line shouldEqual it.first
                column shouldEqual it.second
            }
        }
    }

    private fun shouldThrowSyntaxError(src: String, block: GraphQLError.() -> Pair<Int, Int>?) = shouldThrowSyntaxError(Source(src), block)

    @Test
    fun `parse provides useful errors`() {
        invoking { parse("{") } shouldThrow GraphQLError::class with {
            message shouldEqual "Syntax Error: Expected Name, found <EOF>."
            positions!!.size shouldEqual 1
            locations!!.size shouldEqual 1
            positions!!.first() shouldEqual 1
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 2
            }

            prettyPrint() shouldEqual """
                |Syntax Error: Expected Name, found <EOF>.
                |
                |GraphQL request:1:2
                |1 | {
                |  |  ^
            """.trimMargin()
        }

        shouldThrowSyntaxError("""
            |
            |      { ...MissingOn }
            |      fragment MissingOn Type
            |
        """.trimMargin()) {
            message shouldEqual "Syntax Error: Expected \"on\", found Name \"Type\"."
            3 to 26
        }

        shouldThrowSyntaxError("{ field: {} }") {
            message shouldEqual "Syntax Error: Expected Name, found \"{\"."
            1 to 10
        }

        shouldThrowSyntaxError("notanoperation Foo { field }") {
            message shouldEqual "Syntax Error: Unexpected Name \"notanoperation\"."
            1 to 1
        }

        shouldThrowSyntaxError("...") {
            message shouldEqual "Syntax Error: Unexpected \"...\"."
            1 to 1
        }

        shouldThrowSyntaxError("{ \"\"") {
            message shouldEqual "Syntax Error: Expected Name, found String \"\"."
            1 to 3
        }
    }

    @Test
    fun `parse provides useful error when using source`() {
        shouldThrowSyntaxError(Source("query", "MyQuery.graphql")) {
            prettyPrint() shouldEqual """
                |Syntax Error: Expected "{", found <EOF>.
                |
                |MyQuery.graphql:1:6
                |1 | query
                |  |      ^
                """.trimMargin()
            null
        }
    }

    @Test
    fun `parses variable inline values`() {
        parse("{ field(complex: { a: { b: [ ${d}var ] } }) }")
    }

    @Test
    fun `parses constant default values`() {
        shouldThrowSyntaxError("query Foo(${d}x: Complex = { a: { b: [ ${d}var ] } }) { field }") {
            message shouldEqual "Syntax Error: Unexpected \"${d}\"."
            1 to 37
        }
    }

    @Test
    fun `parses variable definition directives`() {
        parse("query Foo(${d}x: Boolean = false @bar) { field }")
    }

    @Test
    fun `does not accept fragments named 'on'`() {
        shouldThrowSyntaxError("fragment on on on { on }") {
            message shouldEqual "Syntax Error: Unexpected Name \"on\"."
            1 to 10
        }
    }

    @Test
    fun `does not accept fragments spread of 'on'`() {
        shouldThrowSyntaxError("{ ...on }") {
            message shouldEqual "Syntax Error: Expected Name, found \"}\"."
            1 to 9
        }
    }

    @Test
    fun `parses multi-byte characters`() {
        // Note: \u0A0A could be naively interpreted as two line-feed chars.
        parse("""
            |# This comment has a ${'\u0A0A'} multi-byte character.
            |{ field(arg: "Has a ${'\u0A0A'} multi-byte character.") }
        """.trimMargin()).run {
            (definitions[0] as OperationDefinitionNode).selectionSet.run {
                (selections.first() as FieldNode).run {
                    (arguments!!.first().value as StringValueNode).run {
                        value shouldEqual "Has a \u0A0A multi-byte character."
                    }
                }
            }
        }
    }

    @Test
    fun `parses kitchen sink`() {
        parse(kitchenSinkQuery)
    }

    @Test
    fun `allows non-keywords anywhere a Name is allowed`() {
        val nonKeywords = listOf(
            "on",
            "fragment",
            "query",
            "mutation",
            "subscription",
            "true",
            "false"
        )

        for (keyword in nonKeywords) {
            val fragmentName = if (keyword != "on") keyword else "a"
            val document = """
                |query $keyword {
                |  ... $fragmentName
                |  ... on $keyword { field }
                |}
                |fragment $fragmentName on Type {
                |  $keyword($keyword: ${d}$keyword)
                |    @$keyword($keyword: $keyword)
                |}
            """.trimMargin()

            parse(document)
        }
    }

    @Test
    fun `parses anonymous mutation operations`() {
        parse("""
            |mutation {
            |  mutationField
            |}
        """.trimMargin())
    }

    @Test
    fun `parses anonymous subscription operations`() {
        parse("""
            |subscription {
            |  subscriptionField
            |}
        """.trimMargin())
    }

    @Test
    fun `parses named mutation operations`() {
        parse("""
            |mutation Foo {
            |  mutationField
            |}
        """.trimMargin())
    }

    @Test
    fun `parses named subscription operations`() {
        parse("""
            |subscription Foo {
            |  subscriptionField
            |}
        """.trimMargin())
    }

    @Test
    fun `creates ast`() {
        parse("""
            |{
            |  node(id: 4) {
            |    id,
            |    name
            |  }
            |}
            |
        """.trimMargin()).run {
            loc!!.run {
                start shouldEqual 0
                end shouldEqual 41
            }
            definitions.size shouldEqual 1
            (definitions.first() as OperationDefinitionNode).run {
                loc!!.run {
                    start shouldEqual 0
                    end shouldEqual 40
                }
                operation shouldEqual QUERY
                name shouldEqual null
                variableDefinitions!!.size shouldEqual 0
                directives!!.size shouldEqual 0
                selectionSet.run {
                    loc!!.run {
                        start shouldEqual 0
                        end shouldEqual 40
                    }
                    selections.size shouldEqual 1
                    (selections.first() as FieldNode).run {
                        loc!!.run {
                            start shouldEqual 4
                            end shouldEqual 38
                        }
                        alias shouldEqual null
                        name.loc!!.run {
                            start shouldEqual 4
                            end shouldEqual 8
                        }
                        name.value shouldEqual "node"
                        arguments!!.size shouldEqual 1
                        arguments!!.first().run {
                            name.value shouldEqual "id"
                            name.loc!!.run {
                                start shouldEqual 9
                                end shouldEqual 11
                            }
                            (value as NumberValueNode).run {
                                this shouldBeInstanceOf NumberValueNode::class
                                loc!!.run {
                                    start shouldEqual 13
                                    end shouldEqual 14
                                }
                                value shouldEqual 4
                            }
                            loc!!.run {
                                start shouldEqual 9
                                end shouldEqual 14
                            }
                        }

                        directives!!.size shouldEqual 0
                        selectionSet!!.run {
                            this shouldBeInstanceOf SelectionSetNode::class
                            loc!!.run {
                                start shouldEqual 16
                                end shouldEqual 38
                            }
                            selections.size shouldEqual 2
                            (selections.first() as FieldNode).run {
                                this shouldBeInstanceOf FieldNode::class
                                loc!!.run {
                                    start shouldEqual 22
                                    end shouldEqual 24
                                }
                                alias shouldEqual null
                                name.run {
                                    loc!!.run {
                                        start shouldEqual 22
                                        end shouldEqual 24
                                    }
                                    value shouldEqual "id"
                                }
                                arguments!!.size shouldEqual 0
                                directives!!.size shouldEqual 0
                                selectionSet shouldEqual null
                            }

                            (selections[1] as FieldNode).run {
                                this shouldBeInstanceOf FieldNode::class
                                loc!!.run {
                                    start shouldEqual 30
                                    end shouldEqual 34
                                }
                                alias shouldEqual null
                                name.run {
                                    loc!!.run {
                                        start shouldEqual 30
                                        end shouldEqual 34
                                        value shouldEqual "name"
                                    }
                                    arguments!!.size shouldEqual 0
                                    directives!!.size shouldEqual 0
                                    selectionSet shouldEqual null
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `creates ast from nameless query without variables`() {
        parse("""
            |query {
            |  node {
            |    id
            |  }
            |}
            |
        """.trimMargin()).run {
            this shouldBeInstanceOf DocumentNode::class
            loc!!.run {
                start shouldEqual 0
                end shouldEqual 30
            }
            definitions.size shouldEqual 1
            (definitions.first() as OperationDefinitionNode).run {
                this shouldBeInstanceOf OperationDefinitionNode::class
                loc!!.run {
                    start shouldEqual 0
                    end shouldEqual 29
                }
                operation shouldEqual QUERY
                name shouldEqual null
                variableDefinitions!!.size shouldEqual 0
                directives!!.size shouldEqual 0
                selectionSet.run {
                    loc!!.run {
                        start shouldEqual 6
                        end shouldEqual 29
                    }
                    selections.size shouldEqual 1
                    (selections.first() as FieldNode).run {
                        this shouldBeInstanceOf FieldNode::class
                        loc!!.run {
                            start shouldEqual 10
                            end shouldEqual 27
                        }
                        alias shouldEqual null
                        name.run {
                            loc!!.run {
                                start shouldEqual 10
                                end shouldEqual 14
                            }
                            value shouldEqual "node"
                        }
                        arguments!!.size shouldEqual 0
                        directives!!.size shouldEqual 0
                        selectionSet!!.run {
                            loc!!.run {
                                start shouldEqual 15
                                end shouldEqual 27
                            }
                            selections.size shouldEqual 1
                            (selections.first() as FieldNode).run {
                                this shouldBeInstanceOf FieldNode::class
                                loc!!.run {
                                    start shouldEqual 21
                                    end shouldEqual 23
                                }
                                alias shouldEqual null
                                name.run {
                                    loc!!.run {
                                        start shouldEqual 21
                                        end shouldEqual 23
                                    }
                                    value shouldEqual "id"
                                }
                                arguments!!.size shouldEqual 0
                                directives!!.size shouldEqual 0
                                selectionSet shouldEqual null
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `allows parsing without source location information`() {
        val result = parse("{ id }", Parser.Options(noLocation = true))
        result.loc shouldEqual null
    }

    @Test
    fun `contains references to source`() {
        val source = Source("{ id }")
        val result = parse(source)

        result.loc!!.source shouldEqual source
    }

    @Test
    fun `contains references to start and end tokens`() {
        val result = parse("{ id }")

        result.loc!!.startToken.kind shouldEqual SOF
        result.loc!!.endToken.kind shouldEqual EOF
    }

    //================================================//
    ////////////////////////////////////////////////////
    //////////////////   parseValue   //////////////////
    ////////////////////////////////////////////////////
    //================================================//

    @Test
    fun `parses null value`() {
        parseValue("null").run {
            this shouldBeInstanceOf NullValueNode::class
            loc!!.run {
                start shouldEqual 0
                end shouldEqual 4
            }
        }
    }

    @Test
    fun `parses list values`() {
        (parseValue("[123 \"abc\"]") as ListValueNode).run {
            this shouldBeInstanceOf ListValueNode::class
            loc!!.run {
                start shouldEqual 0
                end shouldEqual 11
            }
            values.size shouldEqual 2
            (values[0] as NumberValueNode).run {
                this shouldBeInstanceOf NumberValueNode::class
                loc!!.run {
                    start shouldEqual 1
                    end shouldEqual 4
                }
                value shouldEqual 123
            }
            (values[1] as StringValueNode).run {
                this shouldBeInstanceOf StringValueNode::class
                loc!!.run {
                    start shouldEqual 5
                    end shouldEqual 10
                }
                value shouldEqual "abc"
                block shouldEqual false
            }
        }

    }

    @Test
    fun `parses block strings`() {
        (parseValue("[\"\"\"long\"\"\" \"short\"]") as ListValueNode).run {
            this shouldBeInstanceOf ListValueNode::class
            loc!!.run {
                start shouldEqual 0
                end shouldEqual 20
            }
            values.size shouldEqual 2
            (values[0] as StringValueNode).run {
                this shouldBeInstanceOf StringValueNode::class
                loc!!.run {
                    start shouldEqual 1
                    end shouldEqual 11
                }
                value shouldEqual "long"
                block shouldEqual true
            }
            (values[1] as StringValueNode).run {
                this shouldBeInstanceOf StringValueNode::class
                loc!!.run {
                    start shouldEqual 12
                    end shouldEqual 19
                }
                value shouldEqual "short"
                block shouldEqual false
            }
        }
    }

    //===============================================//
    ///////////////////////////////////////////////////
    /////////////////   parseType   ///////////////////
    ///////////////////////////////////////////////////
    //===============================================//

    @Test
    fun `parses well known types`() {
        (parseType("String") as NamedTypeNode).run {
            this shouldBeInstanceOf NamedTypeNode::class
            loc!!.run {
                start shouldEqual 0
                end shouldEqual 6
            }
            name.run {
                loc!!.run {
                    start shouldEqual 0
                    end shouldEqual 6
                }
                value shouldEqual "String"
            }
        }
    }

    @Test
    fun `parses custom types`() {
        (parseType("MyType") as NamedTypeNode).run {
            this shouldBeInstanceOf NamedTypeNode::class
            loc!!.run {
                start shouldEqual 0
                end shouldEqual 6
            }
            name.run {
                loc!!.run {
                    start shouldEqual 0
                    end shouldEqual 6
                }
                value shouldEqual "MyType"
            }
        }
    }

    @Test
    fun `parses list types`() {
        (parseType("[MyType]") as ListTypeNode).run {
            this shouldBeInstanceOf ListTypeNode::class
            loc!!.run {
                start shouldEqual 0
                end shouldEqualTo 8
            }
            (type as NamedTypeNode).run {
                this shouldBeInstanceOf NamedTypeNode::class
                loc!!.run {
                    start shouldEqual 1
                    end shouldEqual 7
                }
                name.run {
                    loc!!.run {
                        start shouldEqual 1
                        end shouldEqual 7
                    }
                    value shouldEqual "MyType"
                }
            }
        }
    }

    @Test
    fun `parses non-null types`() {
        (parseType("MyType!") as NonNullTypeNode).run {
            this shouldBeInstanceOf NonNullTypeNode::class
            loc!!.run {
                start shouldEqual 0
                end shouldEqual 7
            }
            (type as NamedTypeNode).run {
                this shouldBeInstanceOf NamedTypeNode::class
                loc!!.run {
                    start shouldEqual 0
                    end shouldEqual 6
                }
                name.run {
                    loc!!.run {
                        start shouldEqual 0
                        end shouldEqual 6
                    }
                    value shouldEqual "MyType"
                }
            }
        }
    }

    @Test
    fun `parses nested types`() {
        (parseType("[MyType!]") as ListTypeNode).run {
            this shouldBeInstanceOf ListTypeNode::class
            loc!!.run {
                start shouldEqual 0
                end shouldEqual 9
            }
            (type as NonNullTypeNode).run {
                this shouldBeInstanceOf NonNullTypeNode::class
                loc!!.run {
                    start shouldEqual 1
                    end shouldEqual 8
                }
                (type as NamedTypeNode).run {
                    this shouldBeInstanceOf NamedTypeNode::class
                    loc!!.run {
                        start shouldEqual 1
                        end shouldEqual 7
                    }
                    name.run {
                        loc!!.run {
                            start shouldEqual 1
                            end shouldEqual 7
                        }
                        value shouldEqual "MyType"
                    }
                }
            }
        }
    }

}
