package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.InvalidSyntaxException
import com.apurebase.kgraphql.ResourceFiles.kitchenSinkQuery
import com.apurebase.kgraphql.schema.model.ast.DefinitionNode.ExecutableDefinitionNode.OperationDefinitionNode
import com.apurebase.kgraphql.schema.model.ast.DocumentNode
import com.apurebase.kgraphql.schema.model.ast.OperationTypeNode.QUERY
import com.apurebase.kgraphql.schema.model.ast.SelectionNode.FieldNode
import com.apurebase.kgraphql.schema.model.ast.SelectionSetNode
import com.apurebase.kgraphql.schema.model.ast.Source
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.EOF
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.SOF
import com.apurebase.kgraphql.schema.model.ast.TypeNode
import com.apurebase.kgraphql.schema.model.ast.TypeNode.ListTypeNode
import com.apurebase.kgraphql.schema.model.ast.TypeNode.NamedTypeNode
import com.apurebase.kgraphql.schema.model.ast.TypeNode.NonNullTypeNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode.ListValueNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode.NullValueNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode.NumberValueNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode.StringValueNode
import com.apurebase.kgraphql.shouldBeInstanceOf
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ParserTest {

    private fun parse(source: String, options: Parser.Options? = null) = Parser(source, options).parseDocument()

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
        block: InvalidSyntaxException.() -> Pair<Int, Int>?
    ) = shouldThrowExactly<InvalidSyntaxException> { parse(src) }.run {
        block()?.let {
            locations!!.size shouldBe 1
            locations!!.first().run {
                line shouldBe it.first
                column shouldBe it.second
            }
        }
    }

    private fun shouldThrowSyntaxError(src: String, block: InvalidSyntaxException.() -> Pair<Int, Int>?) =
        shouldThrowSyntaxError(
            Source(src), block
        )

    @Test
    fun `parse provides useful errors`() {
        shouldThrowExactly<InvalidSyntaxException> { parse("{") }.run {
            message shouldBe "Syntax Error: Expected Name, found <EOF>."
            positions!!.size shouldBe 1
            locations!!.size shouldBe 1
            positions!!.first() shouldBe 1
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 2
            }

            prettyPrint() shouldBe """
                        |Syntax Error: Expected Name, found <EOF>.
                        |
                        |GraphQL request:1:2
                        |1 | {
                        |  |  ^
                    """.trimMargin()
        }

        shouldThrowSyntaxError(
            """
            |
            |      { ...MissingOn }
            |      fragment MissingOn Type
            |
        """.trimMargin()
        ) {
            message shouldBe "Syntax Error: Expected \"on\", found Name \"Type\"."
            3 to 26
        }

        shouldThrowSyntaxError("{ field: {} }") {
            message shouldBe "Syntax Error: Expected Name, found \"{\"."
            1 to 10
        }

        shouldThrowSyntaxError("notanoperation Foo { field }") {
            message shouldBe "Syntax Error: Unexpected Name \"notanoperation\"."
            1 to 1
        }

        shouldThrowSyntaxError("...") {
            message shouldBe "Syntax Error: Unexpected \"...\"."
            1 to 1
        }

        shouldThrowSyntaxError("{ \"\"") {
            message shouldBe "Syntax Error: Expected Name, found String \"\"."
            1 to 3
        }
    }

    @Test
    fun `parse provides useful error when using source`() {
        shouldThrowSyntaxError(Source("query", "MyQuery.graphql")) {
            prettyPrint() shouldBe """
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
        parse("{ field(complex: { a: { b: [ \$var ] } }) }")
    }

    @Test
    fun `parses constant default values`() {
        shouldThrowSyntaxError("query Foo(\$x: Complex = { a: { b: [ \$var ] } }) { field }") {
            message shouldBe "Syntax Error: Unexpected \"\$\"."
            1 to 37
        }
    }

    @Test
    fun `parses variable definition directives`() {
        parse("query Foo(\$x: Boolean = false @bar) { field }")
    }

    @Test
    fun `does not accept fragments named 'on'`() {
        shouldThrowSyntaxError("fragment on on on { on }") {
            message shouldBe "Syntax Error: Unexpected Name \"on\"."
            1 to 10
        }
    }

    @Test
    fun `does not accept fragments spread of 'on'`() {
        shouldThrowSyntaxError("{ ...on }") {
            message shouldBe "Syntax Error: Expected Name, found \"}\"."
            1 to 9
        }
    }

    @Test
    fun `parses multi-byte characters`() {
        // Note: \u0A0A could be naively interpreted as two line-feed chars.
        parse(
            """
            |# This comment has a ${'\u0A0A'} multi-byte character.
            |{ field(arg: "Has a ${'\u0A0A'} multi-byte character.") }
        """.trimMargin()
        ).run {
            (definitions[0] as OperationDefinitionNode).selectionSet.run {
                (selections.first() as FieldNode).run {
                    (arguments!!.first().value as StringValueNode).run {
                        value shouldBe "Has a \u0A0A multi-byte character."
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
                |  $keyword($keyword: ${'$'}$keyword)
                |    @$keyword($keyword: $keyword)
                |}
            """.trimMargin()

            parse(document)
        }
    }

    @Test
    fun `parses anonymous mutation operations`() {
        parse(
            """
            |mutation {
            |  mutationField
            |}
        """.trimMargin()
        )
    }

    @Test
    fun `parses anonymous subscription operations`() {
        parse(
            """
            |subscription {
            |  subscriptionField
            |}
        """.trimMargin()
        )
    }

    @Test
    fun `parses named mutation operations`() {
        parse(
            """
            |mutation Foo {
            |  mutationField
            |}
        """.trimMargin()
        )
    }

    @Test
    fun `parses named subscription operations`() {
        parse(
            """
            |subscription Foo {
            |  subscriptionField
            |}
        """.trimMargin()
        )
    }

    @Test
    fun `creates ast`() {
        parse(
            """
            |{
            |  node(id: 4) {
            |    id,
            |    name
            |  }
            |}
            |
        """.trimMargin()
        ).run {
            loc!!.run {
                start shouldBe 0
                end shouldBe 41
            }
            definitions.size shouldBe 1
            (definitions.first() as OperationDefinitionNode).run {
                loc!!.run {
                    start shouldBe 0
                    end shouldBe 40
                }
                operation shouldBe QUERY
                name shouldBe null
                variableDefinitions!!.size shouldBe 0
                directives!!.size shouldBe 0
                selectionSet.run {
                    loc!!.run {
                        start shouldBe 0
                        end shouldBe 40
                    }
                    selections.size shouldBe 1
                    (selections.first() as FieldNode).run {
                        loc!!.run {
                            start shouldBe 4
                            end shouldBe 38
                        }
                        alias shouldBe null
                        name.loc!!.run {
                            start shouldBe 4
                            end shouldBe 8
                        }
                        name.value shouldBe "node"
                        arguments!!.size shouldBe 1
                        arguments!!.first().run {
                            name.value shouldBe "id"
                            name.loc!!.run {
                                start shouldBe 9
                                end shouldBe 11
                            }
                            (value as NumberValueNode).run {
                                this shouldBeInstanceOf NumberValueNode::class
                                loc!!.run {
                                    start shouldBe 13
                                    end shouldBe 14
                                }
                                value shouldBe 4
                            }
                            loc!!.run {
                                start shouldBe 9
                                end shouldBe 14
                            }
                        }

                        directives!!.size shouldBe 0
                        selectionSet!!.run {
                            this shouldBeInstanceOf SelectionSetNode::class
                            loc!!.run {
                                start shouldBe 16
                                end shouldBe 38
                            }
                            selections.size shouldBe 2
                            (selections.first() as FieldNode).run {
                                this shouldBeInstanceOf FieldNode::class
                                loc!!.run {
                                    start shouldBe 22
                                    end shouldBe 24
                                }
                                alias shouldBe null
                                name.run {
                                    loc!!.run {
                                        start shouldBe 22
                                        end shouldBe 24
                                    }
                                    value shouldBe "id"
                                }
                                arguments!!.size shouldBe 0
                                directives!!.size shouldBe 0
                                selectionSet shouldBe null
                            }

                            (selections[1] as FieldNode).run {
                                this shouldBeInstanceOf FieldNode::class
                                loc!!.run {
                                    start shouldBe 30
                                    end shouldBe 34
                                }
                                alias shouldBe null
                                name.run {
                                    loc!!.run {
                                        start shouldBe 30
                                        end shouldBe 34
                                        value shouldBe "name"
                                    }
                                    arguments!!.size shouldBe 0
                                    directives!!.size shouldBe 0
                                    selectionSet shouldBe null
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
        parse(
            """
            |query {
            |  node {
            |    id
            |  }
            |}
            |
        """.trimMargin()
        ).run {
            this shouldBeInstanceOf DocumentNode::class
            loc!!.run {
                start shouldBe 0
                end shouldBe 30
            }
            definitions.size shouldBe 1
            definitions.first().run {
                this shouldBeInstanceOf OperationDefinitionNode::class
                this as OperationDefinitionNode
                loc!!.run {
                    start shouldBe 0
                    end shouldBe 29
                }
                operation shouldBe QUERY
                name shouldBe null
                variableDefinitions!!.size shouldBe 0
                directives!!.size shouldBe 0
                selectionSet.run {
                    loc!!.run {
                        start shouldBe 6
                        end shouldBe 29
                    }
                    selections.size shouldBe 1
                    (selections.first() as FieldNode).run {
                        this shouldBeInstanceOf FieldNode::class
                        loc!!.run {
                            start shouldBe 10
                            end shouldBe 27
                        }
                        alias shouldBe null
                        name.run {
                            loc!!.run {
                                start shouldBe 10
                                end shouldBe 14
                            }
                            value shouldBe "node"
                        }
                        arguments!!.size shouldBe 0
                        directives!!.size shouldBe 0
                        selectionSet!!.run {
                            loc!!.run {
                                start shouldBe 15
                                end shouldBe 27
                            }
                            selections.size shouldBe 1
                            (selections.first() as FieldNode).run {
                                this shouldBeInstanceOf FieldNode::class
                                loc!!.run {
                                    start shouldBe 21
                                    end shouldBe 23
                                }
                                alias shouldBe null
                                name.run {
                                    loc!!.run {
                                        start shouldBe 21
                                        end shouldBe 23
                                    }
                                    value shouldBe "id"
                                }
                                arguments!!.size shouldBe 0
                                directives!!.size shouldBe 0
                                selectionSet shouldBe null
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
        result.loc shouldBe null
    }

    @Test
    fun `contains references to source`() {
        val source = Source("{ id }")
        val result = parse(source)

        result.loc!!.source shouldBe source
    }

    @Test
    fun `contains references to start and end tokens`() {
        val result = parse("{ id }")

        result.loc!!.startToken.kind shouldBe SOF
        result.loc!!.endToken.kind shouldBe EOF
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
                start shouldBe 0
                end shouldBe 4
            }
        }
    }

    @Test
    fun `parses list values`() {
        (parseValue("[123 \"abc\"]") as ListValueNode).run {
            this shouldBeInstanceOf ListValueNode::class
            loc!!.run {
                start shouldBe 0
                end shouldBe 11
            }
            values.size shouldBe 2
            (values[0] as NumberValueNode).run {
                this shouldBeInstanceOf NumberValueNode::class
                loc!!.run {
                    start shouldBe 1
                    end shouldBe 4
                }
                value shouldBe 123
            }
            (values[1] as StringValueNode).run {
                this shouldBeInstanceOf StringValueNode::class
                loc!!.run {
                    start shouldBe 5
                    end shouldBe 10
                }
                value shouldBe "abc"
                block shouldBe false
            }
        }
    }

    @Test
    fun `parses block strings`() {
        (parseValue("[\"\"\"long\"\"\" \"short\"]") as ListValueNode).run {
            this shouldBeInstanceOf ListValueNode::class
            loc!!.run {
                start shouldBe 0
                end shouldBe 20
            }
            values.size shouldBe 2
            (values[0] as StringValueNode).run {
                this shouldBeInstanceOf StringValueNode::class
                loc!!.run {
                    start shouldBe 1
                    end shouldBe 11
                }
                value shouldBe "long"
                block shouldBe true
            }
            (values[1] as StringValueNode).run {
                this shouldBeInstanceOf StringValueNode::class
                loc!!.run {
                    start shouldBe 12
                    end shouldBe 19
                }
                value shouldBe "short"
                block shouldBe false
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
                start shouldBe 0
                end shouldBe 6
            }
            name.run {
                loc!!.run {
                    start shouldBe 0
                    end shouldBe 6
                }
                value shouldBe "String"
            }
        }
    }

    @Test
    fun `parses custom types`() {
        (parseType("MyType") as NamedTypeNode).run {
            this shouldBeInstanceOf NamedTypeNode::class
            loc!!.run {
                start shouldBe 0
                end shouldBe 6
            }
            name.run {
                loc!!.run {
                    start shouldBe 0
                    end shouldBe 6
                }
                value shouldBe "MyType"
            }
        }
    }

    @Test
    fun `parses list types`() {
        (parseType("[MyType]") as ListTypeNode).run {
            this shouldBeInstanceOf ListTypeNode::class
            loc!!.run {
                start shouldBe 0
                end shouldBe 8
            }
            (type as NamedTypeNode).run {
                this shouldBeInstanceOf NamedTypeNode::class
                loc!!.run {
                    start shouldBe 1
                    end shouldBe 7
                }
                name.run {
                    loc!!.run {
                        start shouldBe 1
                        end shouldBe 7
                    }
                    value shouldBe "MyType"
                }
            }
        }
    }

    @Test
    fun `parses non-null types`() {
        (parseType("MyType!") as NonNullTypeNode).run {
            this shouldBeInstanceOf NonNullTypeNode::class
            loc!!.run {
                start shouldBe 0
                end shouldBe 7
            }
            (type as NamedTypeNode).run {
                this shouldBeInstanceOf NamedTypeNode::class
                loc!!.run {
                    start shouldBe 0
                    end shouldBe 6
                }
                name.run {
                    loc!!.run {
                        start shouldBe 0
                        end shouldBe 6
                    }
                    value shouldBe "MyType"
                }
            }
        }
    }

    @Test
    fun `parses nested types`() {
        (parseType("[MyType!]") as ListTypeNode).run {
            this shouldBeInstanceOf ListTypeNode::class
            loc!!.run {
                start shouldBe 0
                end shouldBe 9
            }
            (type as NonNullTypeNode).run {
                this shouldBeInstanceOf NonNullTypeNode::class
                loc!!.run {
                    start shouldBe 1
                    end shouldBe 8
                }
                (type as NamedTypeNode).run {
                    this shouldBeInstanceOf NamedTypeNode::class
                    loc!!.run {
                        start shouldBe 1
                        end shouldBe 7
                    }
                    name.run {
                        loc!!.run {
                            start shouldBe 1
                            end shouldBe 7
                        }
                        value shouldBe "MyType"
                    }
                }
            }
        }
    }
}
