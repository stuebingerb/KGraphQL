package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.InvalidSyntaxException
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.schema.model.ast.Source
import com.apurebase.kgraphql.schema.model.ast.Token
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.AT
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.BANG
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.BLOCK_STRING
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.BRACE_L
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.BRACE_R
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.BRACKET_L
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.BRACKET_R
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.COLON
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.COMMENT
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.DOLLAR
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.EOF
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.EQUALS
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.FLOAT
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.INT
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.NAME
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.PAREN_L
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.PAREN_R
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.PIPE
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.SOF
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.SPREAD
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.STRING
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

internal class LexerTest {

    private fun lexOne(str: String, block: Token.() -> Unit = { }) {
        block(Lexer(str).advance())
    }

    private fun lexSecond(str: String, block: Token.() -> Unit = { }) {
        block(Lexer(str).also { it.advance() }.advance())
    }

    private fun shouldThrowSyntaxError(str: String, expect: InvalidSyntaxException.() -> Unit) {
        val exception = shouldThrowExactly<InvalidSyntaxException> { lexSecond(str) }
        expect(exception)
    }

    @Test
    fun `disallows uncommon control characters`() {
        expect<InvalidSyntaxException>("Syntax Error: Cannot contain the invalid character \"\\u0007\".") {
            lexSecond("\u0007")
        }
    }

    @Test
    fun `accepts BOM header`() {
        lexOne("\uFEFF foo") {
            kind shouldBe NAME
            start shouldBe 2
            end shouldBe 5
            value shouldBe "foo"
        }
    }

    @Test
    fun `tracks line breaks`() {
        lexOne("foo") {
            kind shouldBe NAME
            start shouldBe 0
            end shouldBe 3
            line shouldBe 1
            column shouldBe 1
            value shouldBe "foo"
        }
        lexOne("\nfoo") {
            kind shouldBe NAME
            start shouldBe 1
            end shouldBe 4
            line shouldBe 2
            column shouldBe 1
            value shouldBe "foo"
        }
        lexOne("\rfoo") {
            kind shouldBe NAME
            start shouldBe 1
            end shouldBe 4
            line shouldBe 2
            column shouldBe 1
            value shouldBe "foo"
        }
        lexOne("\r\nfoo") {
            kind shouldBe NAME
            start shouldBe 2
            end shouldBe 5
            line shouldBe 2
            column shouldBe 1
            value shouldBe "foo"
        }
        lexOne("\n\rfoo") {
            kind shouldBe NAME
            start shouldBe 2
            end shouldBe 5
            line shouldBe 3
            column shouldBe 1
            value shouldBe "foo"
        }
        lexOne("\r\r\n\nfoo") {
            kind shouldBe NAME
            start shouldBe 4
            end shouldBe 7
            line shouldBe 4
            column shouldBe 1
            value shouldBe "foo"
        }
        lexOne("\n\n\r\rfoo") {
            kind shouldBe NAME
            start shouldBe 4
            end shouldBe 7
            line shouldBe 5
            column shouldBe 1
            value shouldBe "foo"
        }
    }

    @Test
    fun `records line and column`() {
        lexOne("\n \r\n \r  foo\n") {
            kind shouldBe NAME
            start shouldBe 8
            end shouldBe 11
            line shouldBe 4
            column shouldBe 3
            value shouldBe "foo"
        }
    }

    @Test
    fun `skips whitespace and comments`() {
        lexOne(
            """

    foo


"""
        ) {
            kind shouldBe NAME
            start shouldBe 6
            end shouldBe 9
            value shouldBe "foo"
        }
        lexOne(
            """
    #comment
    foo#comment
"""
        ) {
            kind shouldBe NAME
            start shouldBe 18
            end shouldBe 21
            value shouldBe "foo"
        }
        lexOne(",,,foo,,,") {
            kind shouldBe NAME
            start shouldBe 3
            end shouldBe 6
            value shouldBe "foo"
        }
    }

    @Test
    fun `errors respect whitespace`() {
        val exception = shouldThrowExactly<InvalidSyntaxException> {
            lexOne(listOf("", "", "    ?", "").joinToString("\n"))
        }

        exception.prettyPrint() shouldBe """
                  Syntax Error: Cannot parse the unexpected character "?".
                  
                  GraphQL request:3:5
                  2 |
                  3 |     ?
                    |     ^
                  4 |
            """.trimIndent()
    }

    @Test
    fun `updates line numbers in error for file context`() {
        val str = listOf("", "", "     ?", "").joinToString("\n")
        val testSource = Source(
            str,
            "foo.graphql",
            Source.LocationSource(11, 12)
        )
        shouldThrowExactly<InvalidSyntaxException> {
            Lexer(testSource).advance()
        }.run {
            source shouldBe testSource
            locations shouldNotBe null
            locations!! shouldHaveSize 1
            extensions shouldBe mapOf(
                "type" to "GRAPHQL_PARSE_FAILED"
            )
            prettyPrint() shouldBe """
                Syntax Error: Cannot parse the unexpected character "?".
                
                foo.graphql:13:6
                12 |
                13 |      ?
                   |      ^
                14 |
            """.trimIndent()
        }
    }

    @Test
    fun `updates column numbers in error for file context`() {
        val exception = shouldThrowExactly<InvalidSyntaxException> {
            val source = Source(
                "?",
                "foo.graphql",
                Source.LocationSource(1, 5)
            )
            Lexer(source).advance()
        }

        exception.prettyPrint() shouldBe """
                Syntax Error: Cannot parse the unexpected character "?".
                
                foo.graphql:1:5
                1 |     ?
                  |     ^
            """.trimIndent()
    }

    @Test
    fun `lexes strings`() {
        lexOne("\"\"") {
            kind shouldBe STRING
            start shouldBe 0
            end shouldBe 2
            value shouldBe ""
        }

        lexOne("\"simple\"") {
            kind shouldBe STRING
            start shouldBe 0
            end shouldBe 8
            value shouldBe "simple"
        }

        lexOne("\" white space \"") {
            kind shouldBe STRING
            start shouldBe 0
            end shouldBe 15
            value shouldBe " white space "
        }

        lexOne("\"quote \\\"\"") {
            kind shouldBe STRING
            start shouldBe 0
            end shouldBe 10
            value shouldBe "quote \""
        }

        lexOne("\"escaped \\n\\r\\b\\t\\f\"") {
            kind shouldBe STRING
            start shouldBe 0
            end shouldBe 20
            value shouldBe "escaped \n\r\b\tf"
        }

        lexOne("\"slashes \\\\ \\/\"") {
            kind shouldBe STRING
            start shouldBe 0
            end shouldBe 15
            value shouldBe "slashes \\ /"
        }

        lexOne("\"unicode \\u1234\\u5678\\u90AB\\uCDEF\"") {
            kind shouldBe STRING
            start shouldBe 0
            end shouldBe 34
            value shouldBe "unicode \u1234\u5678\u90AB\uCDEF"
        }
    }

    @Test
    fun `lex reports useful string errors`() {
        shouldThrowSyntaxError("\"") {
            message shouldBe "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 2
            }
        }

        shouldThrowSyntaxError("\"\"\"") {
            message shouldBe "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 4
            }
        }

        shouldThrowSyntaxError("\"\"\"\"") {
            message shouldBe "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 5
            }
        }

        shouldThrowSyntaxError("\"no end quote") {
            message shouldBe "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 14
            }
        }

        shouldThrowSyntaxError("'single quotes'") {
            message shouldBe "Syntax Error: Unexpected single quote character (\'), did you mean to use a double quote (\")?"
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 1
            }
        }

        shouldThrowSyntaxError("\"contains unescaped \u0007 control char\"") {
            message shouldBe "Syntax Error: Invalid character within String: \"\\u0007\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 21
            }
        }

        shouldThrowSyntaxError("\"null-byte is not \u0000 end of file\"") {
            message shouldBe "Syntax Error: Invalid character within String: \"\\u0000\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 19
            }
        }

        shouldThrowSyntaxError("\"multi\nline\"") {
            message shouldBe "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 7
            }
        }

        shouldThrowSyntaxError("\"multi\rline\"") {
            message shouldBe "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 7
            }
        }

        shouldThrowSyntaxError("\"bad \\z esc\"") {
            message shouldBe "Syntax Error: Invalid character escape sequence: \\z."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 7
            }
        }

        shouldThrowSyntaxError("\"bad \\x esc\"") {
            message shouldBe "Syntax Error: Invalid character escape sequence: \\x."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 7
            }
        }

        shouldThrowSyntaxError("\"bad \\u1 esc\"") {
            message shouldBe "Syntax Error: Invalid character escape sequence: \\u1 es."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 7
            }
        }

        shouldThrowSyntaxError("\"bad \\u0XX1 esc\"") {
            message shouldBe "Syntax Error: Invalid character escape sequence: \\u0XX1."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 7
            }
        }

        shouldThrowSyntaxError("\"bad \\uXXXX esc\"") {
            message shouldBe "Syntax Error: Invalid character escape sequence: \\uXXXX."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 7
            }
        }

        shouldThrowSyntaxError("\"bad \\uFXXX esc\"") {
            message shouldBe "Syntax Error: Invalid character escape sequence: \\uFXXX."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 7
            }
        }

        shouldThrowSyntaxError("\"bad \\uXXXF esc\"") {
            message shouldBe "Syntax Error: Invalid character escape sequence: \\uXXXF."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 7
            }
        }
    }

    @Test
    fun `lexes block strings`() {
        lexOne("\"\"\"\"\"\"") {
            kind shouldBe BLOCK_STRING
            start shouldBe 0
            end shouldBe 6
            value shouldBe ""
        }

        lexOne("\"\"\"simple\"\"\"") {
            kind shouldBe BLOCK_STRING
            start shouldBe 0
            end shouldBe 12
            value shouldBe "simple"
        }

        lexOne("\"\"\" white space \"\"\"") {
            kind shouldBe BLOCK_STRING
            start shouldBe 0
            end shouldBe 19
            value shouldBe " white space "
        }

        lexOne("\"\"\"contains \" quote\"\"\"") {
            kind shouldBe BLOCK_STRING
            start shouldBe 0
            end shouldBe 22
            value shouldBe "contains \" quote"
        }

        lexOne("\"\"\"contains \\\"\"\" triplequote\"\"\"") {
            kind shouldBe BLOCK_STRING
            start shouldBe 0
            end shouldBe 31
            value shouldBe "contains \"\"\" triplequote"
        }

        lexOne("\"\"\"multi\nline\"\"\"") {
            kind shouldBe BLOCK_STRING
            start shouldBe 0
            end shouldBe 16
            value shouldBe "multi\nline"
        }

        lexOne("\"\"\"multi\rline\r\nnormalized\"\"\"") {
            kind shouldBe BLOCK_STRING
            start shouldBe 0
            end shouldBe 28
            value shouldBe "multi\nline\nnormalized"
        }

        lexOne("\"\"\"unescaped \\n\\r\\b\\t\\f\\u1234\"\"\"") {
            kind shouldBe BLOCK_STRING
            start shouldBe 0
            end shouldBe 32
            value shouldBe "unescaped \\n\\r\\b\\t\\f\\u1234"
        }

        lexOne("\"\"\"slashes \\\\ \\/\"\"\"") {
            kind shouldBe BLOCK_STRING
            start shouldBe 0
            end shouldBe 19
            value shouldBe "slashes \\\\ \\/"
        }


        lexOne(
            """""${'"'}

        spans
          multiple
            lines

        ""${'"'}"""
        ) {
            kind shouldBe BLOCK_STRING
            start shouldBe 0
            end shouldBe 68
            value shouldBe "spans\n  multiple\n    lines"
        }
    }

    @Test
    fun `advance line after lexing multiline block string`() {
        val str = """""${'"'}

        spans
          multiple
            lines

        ${'\n'} ""${'"'} second_token"""
        lexSecond(str) {
            kind shouldBe NAME
            start shouldBe 71
            end shouldBe 83
            line shouldBe 8
            column shouldBe 6
            value shouldBe "second_token"
        }


        val str2 = listOf(
            "\"\"\" \n",
            "spans \r\n",
            "multiple \n\r",
            "lines \n\n",
            "\"\"\"\n second_token"
        ).joinToString("")
        lexSecond(str2) {
            kind shouldBe NAME
            start shouldBe 37
            end shouldBe 49
            line shouldBe 8
            column shouldBe 2
            value shouldBe "second_token"
        }
    }

    @Test
    fun `lex reports useful block string errors`() {
        shouldThrowSyntaxError("\"\"\"") {
            message shouldBe "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 4
            }
        }

        shouldThrowSyntaxError("\"\"\"no end quote") {
            message shouldBe "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 16
            }
        }

        shouldThrowSyntaxError("\"\"\"contains unescaped \u0007 control char\"\"\"") {
            message shouldBe "Syntax Error: Invalid character within String: \"\\u0007\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 23
            }
        }

        shouldThrowSyntaxError("\"\"\"null-byte is not \u0000 end of file\"\"\"") {
            message shouldBe "Syntax Error: Invalid character within String: \"\\u0000\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 21
            }
        }
    }

    @Test
    fun `lexes numbers`() {
        lexOne("4") {
            kind shouldBe INT
            start shouldBe 0
            end shouldBe 1
            value shouldBe "4"
        }

        lexOne("4.123") {
            kind shouldBe FLOAT
            start shouldBe 0
            end shouldBe 5
            value shouldBe "4.123"
        }

        lexOne("-4") {
            kind shouldBe INT
            start shouldBe 0
            end shouldBe 2
            value shouldBe "-4"
        }

        lexOne("9") {
            kind shouldBe INT
            start shouldBe 0
            end shouldBe 1
            value shouldBe "9"
        }

        lexOne("0") {
            kind shouldBe INT
            start shouldBe 0
            end shouldBe 1
            value shouldBe "0"
        }

        lexOne("-4.123") {
            kind shouldBe FLOAT
            start shouldBe 0
            end shouldBe 6
            value shouldBe "-4.123"
        }

        lexOne("0.123") {
            kind shouldBe FLOAT
            start shouldBe 0
            end shouldBe 5
            value shouldBe "0.123"
        }

        lexOne("123e4") {
            kind shouldBe FLOAT
            start shouldBe 0
            end shouldBe 5
            value shouldBe "123e4"
        }

        lexOne("123E4") {
            kind shouldBe FLOAT
            start shouldBe 0
            end shouldBe 5
            value shouldBe "123E4"
        }

        lexOne("123e-4") {
            kind shouldBe FLOAT
            start shouldBe 0
            end shouldBe 6
            value shouldBe "123e-4"
        }

        lexOne("123e+4") {
            kind shouldBe FLOAT
            start shouldBe 0
            end shouldBe 6
            value shouldBe "123e+4"
        }

        lexOne("-1.123e4") {
            kind shouldBe FLOAT
            start shouldBe 0
            end shouldBe 8
            value shouldBe "-1.123e4"
        }

        lexOne("-1.123E4") {
            kind shouldBe FLOAT
            start shouldBe 0
            end shouldBe 8
            value shouldBe "-1.123E4"
        }

        lexOne("-1.123e-4") {
            kind shouldBe FLOAT
            start shouldBe 0
            end shouldBe 9
            value shouldBe "-1.123e-4"
        }

        lexOne("-1.123e+4") {
            kind shouldBe FLOAT
            start shouldBe 0
            end shouldBe 9
            value shouldBe "-1.123e+4"
        }

        lexOne("-1.123e4567") {
            kind shouldBe FLOAT
            start shouldBe 0
            end shouldBe 11
            value shouldBe "-1.123e4567"
        }
    }

    @Test
    fun `lex reports useful number errors`() {
        shouldThrowSyntaxError("00") {
            message shouldBe "Syntax Error: Invalid number, unexpected digit after 0: \"0\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 2
            }
        }

        shouldThrowSyntaxError("01") {
            message shouldBe "Syntax Error: Invalid number, unexpected digit after 0: \"1\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 2
            }
        }

        shouldThrowSyntaxError("01.23") {
            message shouldBe "Syntax Error: Invalid number, unexpected digit after 0: \"1\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 2
            }
        }

        shouldThrowSyntaxError("+1") {
            message shouldBe "Syntax Error: Cannot parse the unexpected character \"+\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 1
            }
        }

        shouldThrowSyntaxError("1.") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: <EOF>."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 3
            }
        }

        shouldThrowSyntaxError("1e") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: <EOF>."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 3
            }
        }

        shouldThrowSyntaxError("1E") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: <EOF>."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 3
            }
        }

        shouldThrowSyntaxError("1.e1") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \"e\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 3
            }
        }

        shouldThrowSyntaxError(".123") {
            message shouldBe "Syntax Error: Cannot parse the unexpected character \".\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 1
            }
        }

        shouldThrowSyntaxError("1.A") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \"A\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 3
            }
        }

        shouldThrowSyntaxError("-A") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \"A\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 2
            }
        }

        shouldThrowSyntaxError("1.0e") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: <EOF>."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 5
            }
        }

        shouldThrowSyntaxError("1.0eA") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \"A\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 5
            }
        }

        shouldThrowSyntaxError("1.2e3e") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \"e\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 6
            }
        }

        shouldThrowSyntaxError("1.2e3.4") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \".\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 6
            }
        }

        shouldThrowSyntaxError("1.23.4") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \".\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 5
            }
        }
    }

    @Test
    fun `lex does not allow name-start after a number`() {
        shouldThrowSyntaxError("0xF1") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \"x\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 2
            }
        }

        shouldThrowSyntaxError("0b10") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \"b\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 2
            }
        }

        shouldThrowSyntaxError("123abc") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \"a\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 4
            }
        }

        shouldThrowSyntaxError("1_234") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \"_\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 2
            }
        }

        shouldThrowSyntaxError("1ß") {
            message shouldBe "Syntax Error: Cannot parse the unexpected character \"\\u00DF\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 2
            }
        }

        shouldThrowSyntaxError("1.23f") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \"f\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 5
            }
        }

        shouldThrowSyntaxError("1.234_5") {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \"_\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 6
            }
        }

        shouldThrowSyntaxError("1ß") {
            message shouldBe "Syntax Error: Cannot parse the unexpected character \"\\u00DF\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 2
            }
        }
    }

    @Test
    fun `lexes punctuation`() {
        lexOne("!") {
            kind shouldBe BANG
            start shouldBe 0
            end shouldBe 1
            value shouldBe null
        }

        lexOne("$") {
            kind shouldBe DOLLAR
            start shouldBe 0
            end shouldBe 1
            value shouldBe null
        }

        lexOne("(") {
            kind shouldBe PAREN_L
            start shouldBe 0
            end shouldBe 1
            value shouldBe null
        }

        lexOne(")") {
            kind shouldBe PAREN_R
            start shouldBe 0
            end shouldBe 1
            value shouldBe null
        }

        lexOne("...") {
            kind shouldBe SPREAD
            start shouldBe 0
            end shouldBe 3
            value shouldBe null
        }

        lexOne(":") {
            kind shouldBe COLON
            start shouldBe 0
            end shouldBe 1
            value shouldBe null
        }

        lexOne("=") {
            kind shouldBe EQUALS
            start shouldBe 0
            end shouldBe 1
            value shouldBe null
        }

        lexOne("@") {
            kind shouldBe AT
            start shouldBe 0
            end shouldBe 1
            value shouldBe null
        }

        lexOne("[") {
            kind shouldBe BRACKET_L
            start shouldBe 0
            end shouldBe 1
            value shouldBe null
        }

        lexOne("]") {
            kind shouldBe BRACKET_R
            start shouldBe 0
            end shouldBe 1
            value shouldBe null
        }

        lexOne("{") {
            kind shouldBe BRACE_L
            start shouldBe 0
            end shouldBe 1
            value shouldBe null
        }

        lexOne("|") {
            kind shouldBe PIPE
            start shouldBe 0
            end shouldBe 1
            value shouldBe null
        }

        lexOne("}") {
            kind shouldBe BRACE_R
            start shouldBe 0
            end shouldBe 1
            value shouldBe null
        }
    }

    @Test
    fun `lex reports useful unknown character error`() {
        shouldThrowSyntaxError("..") {
            message shouldBe "Syntax Error: Cannot parse the unexpected character \".\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 1
            }
        }

        shouldThrowSyntaxError("?") {
            message shouldBe "Syntax Error: Cannot parse the unexpected character \"?\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 1
            }
        }

        shouldThrowSyntaxError("\u203B") {
            message shouldBe "Syntax Error: Cannot parse the unexpected character \"\\u203B\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 1
            }
        }

        shouldThrowSyntaxError("\u200b") {
            message shouldBe "Syntax Error: Cannot parse the unexpected character \"\\u200B\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 1
            }
        }
    }

    @Test
    fun `lex reports useful information for dashes in names`() {
        val source = Source("a-b")
        val lexer = Lexer(source)
        val firstToken = lexer.advance()

        firstToken.run {
            kind shouldBe NAME
            start shouldBe 0
            end shouldBe 1
            value shouldBe "a"
        }

        val exception = shouldThrowExactly<InvalidSyntaxException> { lexer.advance() }
        exception.run {
            message shouldBe "Syntax Error: Invalid number, expected digit but got: \"b\"."
            locations!!.first().run {
                line shouldBe 1
                column shouldBe 3
            }
        }
    }

    @Test
    fun `produces double linked list of tokens, including comments`() {
        val source = Source(
            """
            {
                #comment
                field
            }
        """
        )

        val lexer = Lexer(source)
        val startToken = lexer.token
        var endToken: Token?

        do {
            endToken = lexer.advance()
            // Lexer advances over ignored comment tokens to make writing parsers
            // easier, but will include them in the linked list result.
            endToken.kind shouldNotBe COMMENT
        } while (endToken?.kind != EOF)

        startToken.prev shouldBe null
        endToken.next shouldBe null

        val tokens = mutableListOf<Token>()
        var tok: Token? = startToken
        while (tok != null) {
            tokens.add(tok)
            tok = tok.next ?: break
            if (tokens.size != 0) {
                tok.prev shouldBe tokens[tokens.size - 1]
            }
        }

        tokens.map { it.kind } shouldBe listOf(
            SOF,
            BRACE_L,
            COMMENT,
            NAME,
            BRACE_R,
            EOF
        )
    }


    // ---------------------------------------------//
    // ----------- isPunctuatorTokenKind -----------//
    // ---------------------------------------------//
    private fun isPunctuatorToken(text: String): Boolean {
        return Lexer(text).advance().kind.isPunctuatorTokenKind
    }

    @Test
    fun `returns true for punctuator tokens`() {
        isPunctuatorToken("!") shouldBe true
        isPunctuatorToken("$") shouldBe true
        isPunctuatorToken("&") shouldBe true
        isPunctuatorToken("(") shouldBe true
        isPunctuatorToken(")") shouldBe true
        isPunctuatorToken("...") shouldBe true
        isPunctuatorToken(":") shouldBe true
        isPunctuatorToken("=") shouldBe true
        isPunctuatorToken("@") shouldBe true
        isPunctuatorToken("[") shouldBe true
        isPunctuatorToken("]") shouldBe true
        isPunctuatorToken("{") shouldBe true
        isPunctuatorToken("|") shouldBe true
        isPunctuatorToken("}") shouldBe true
    }

    @Test
    fun `returns false for non-punctuator tokens`() {
        isPunctuatorToken("") shouldBe false
        isPunctuatorToken("name") shouldBe false
        isPunctuatorToken("1") shouldBe false
        isPunctuatorToken("3.14") shouldBe false
        isPunctuatorToken("\"str\"") shouldBe false
        isPunctuatorToken("\"\"\"str\"\"\"") shouldBe false
    }

}
