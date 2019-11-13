package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.schema.model.ast.Source
import com.apurebase.kgraphql.schema.model.ast.Token
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.*
import com.apurebase.kgraphql.GraphQLError
import org.amshove.kluent.*
import org.junit.Test

internal class LexerTest {

    private fun lexOne(str: String, block: Token.() -> Unit = { }) {
        block(Lexer(str).advance())
    }

    private fun lexSecond(str: String, block: Token.() -> Unit = { }) {
        block(Lexer(str).also { it.advance() }.advance())
    }

    private fun shouldThrowSyntaxError(str: String, expect: GraphQLError.() -> Unit) {
        val result = invoking { lexSecond(str) } shouldThrow GraphQLError::class
        expect(result.exception)
    }

    @Test
    fun `disallows uncommon control characters`() {
        invoking { lexSecond("\u0007") }
            .shouldThrow(GraphQLError::class)
            .withMessage("Syntax Error: Cannot contain the invalid character \"\\u0007\".")
    }

    @Test
    fun `accepts BOM header`() {
        lexOne("\uFEFF foo") {
            kind shouldEqual NAME
            start shouldEqual 2
            end shouldEqual 5
            value shouldEqual "foo"
        }
    }

    @Test
    fun `tracks line breaks`() {
        lexOne("foo") {
            kind shouldEqual NAME
            start shouldEqual 0
            end shouldEqual 3
            line shouldEqual 1
            column shouldEqual 1
            value shouldEqual "foo"
        }
        lexOne("\nfoo") {
            kind shouldEqual NAME
            start shouldEqual 1
            end shouldEqual 4
            line shouldEqual 2
            column shouldEqual 1
            value shouldEqual "foo"
        }
        lexOne("\rfoo") {
            kind shouldEqual NAME
            start shouldEqual 1
            end shouldEqual 4
            line shouldEqual 2
            column shouldEqual 1
            value shouldEqual "foo"
        }
        lexOne("\r\nfoo") {
            kind shouldEqual NAME
            start shouldEqual 2
            end shouldEqual 5
            line shouldEqual 2
            column shouldEqual 1
            value shouldEqual "foo"
        }
        lexOne("\n\rfoo") {
            kind shouldEqual NAME
            start shouldEqual 2
            end shouldEqual 5
            line shouldEqual 3
            column shouldEqual 1
            value shouldEqual "foo"
        }
        lexOne("\r\r\n\nfoo") {
            kind shouldEqual NAME
            start shouldEqual 4
            end shouldEqual 7
            line shouldEqual 4
            column shouldEqual 1
            value shouldEqual "foo"
        }
        lexOne("\n\n\r\rfoo") {
            kind shouldEqual NAME
            start shouldEqual 4
            end shouldEqual 7
            line shouldEqual 5
            column shouldEqual 1
            value shouldEqual "foo"
        }
    }

    @Test
    fun `records line and column`() {
        lexOne("\n \r\n \r  foo\n") {
            kind shouldEqual NAME
            start shouldEqual 8
            end shouldEqual 11
            line shouldEqual 4
            column shouldEqual 3
            value shouldEqual "foo"
        }
    }

    @Test
    fun `skips whitespace and comments`() {
        lexOne("""

    foo


""") {
            kind shouldEqual NAME
            start shouldEqual 6
            end shouldEqual 9
            value shouldEqual "foo"
        }
        lexOne("""
    #comment
    foo#comment
""") {
            kind shouldEqual NAME
            start shouldEqual 18
            end shouldEqual 21
            value shouldEqual "foo"
        }
        lexOne(",,,foo,,,") {
            kind shouldEqual NAME
            start shouldEqual 3
            end shouldEqual 6
            value shouldEqual "foo"
        }
    }

    @Test
    fun `errors respect whitespace`() {
        val result = invoking {
            lexOne(listOf("", "","    ?", "").joinToString("\n"))
        } shouldThrow GraphQLError::class

        result.exception.prettyPrint() shouldEqual """
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
        val result = invoking {
            val str = listOf("", "", "     ?", "").joinToString("\n")
            val source = Source(
                str,
                "foo.graphql",
                Source.LocationSource(11, 12)
            )
            Lexer(source).advance()
        } shouldThrow GraphQLError::class

        result.exception.prettyPrint() shouldEqual """
            Syntax Error: Cannot parse the unexpected character "?".
            
            foo.graphql:13:6
            12 |
            13 |      ?
               |      ^
            14 |
        """.trimIndent()

    }

    @Test
    fun `updates column numbers in error for file context`() {
        val result = invoking {
            val source = Source(
                "?",
                "foo.graphql",
                Source.LocationSource(1, 5)
            )
            Lexer(source).advance()
        } shouldThrow GraphQLError::class

        result.exception.prettyPrint() shouldEqual """
            Syntax Error: Cannot parse the unexpected character "?".
            
            foo.graphql:1:5
            1 |     ?
              |     ^
        """.trimIndent()
    }

    @Test
    fun `lexes strings`() {
        lexOne("\"\"") {
            kind shouldEqual STRING
            start shouldEqual 0
            end shouldEqual 2
            value shouldEqual ""
        }

        lexOne("\"simple\"") {
            kind shouldEqual STRING
            start shouldEqual 0
            end shouldEqual 8
            value shouldEqual "simple"
        }

        lexOne("\" white space \"") {
            kind shouldEqual STRING
            start shouldEqual 0
            end shouldEqual 15
            value shouldEqual " white space "
        }

        lexOne("\"quote \\\"\"") {
            kind shouldEqual STRING
            start shouldEqual 0
            end shouldEqual 10
            value shouldEqual "quote \""
        }

        lexOne("\"escaped \\n\\r\\b\\t\\f\"") {
            kind shouldEqual STRING
            start shouldEqual 0
            end shouldEqual 20
            value shouldEqual "escaped \n\r\b\tf"
        }

        lexOne("\"slashes \\\\ \\/\"") {
            kind shouldEqual STRING
            start shouldEqual 0
            end shouldEqual 15
            value shouldEqual "slashes \\ /"
        }

        lexOne("\"unicode \\u1234\\u5678\\u90AB\\uCDEF\"") {
            kind shouldEqual STRING
            start shouldEqual 0
            end shouldEqual 34
            value shouldEqual "unicode \u1234\u5678\u90AB\uCDEF"
        }
    }

    @Test
    fun `lex reports useful string errors`() {
        shouldThrowSyntaxError("\"") {
            message shouldEqual "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 2
            }
        }

        shouldThrowSyntaxError("\"\"\"") {
            message shouldEqual "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 4
            }
        }

        shouldThrowSyntaxError("\"\"\"\"") {
            message shouldEqual "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 5
            }
        }

        shouldThrowSyntaxError("\"no end quote") {
            message shouldEqual "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 14
            }
        }

        shouldThrowSyntaxError("'single quotes'") {
            message shouldEqual "Syntax Error: Unexpected single quote character (\'), did you mean to use a double quote (\")?"
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 1
            }
        }

        shouldThrowSyntaxError("\"contains unescaped \u0007 control char\"") {
            message shouldEqual "Syntax Error: Invalid character within String: \"\\u0007\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 21
            }
        }

        shouldThrowSyntaxError("\"null-byte is not \u0000 end of file\"") {
            message shouldEqual "Syntax Error: Invalid character within String: \"\\u0000\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 19
            }
        }

        shouldThrowSyntaxError("\"multi\nline\"") {
            message shouldEqual "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 7
            }
        }

        shouldThrowSyntaxError("\"multi\rline\"") {
            message shouldEqual "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 7
            }
        }

        shouldThrowSyntaxError("\"bad \\z esc\"") {
            message shouldEqual "Syntax Error: Invalid character escape sequence: \\z."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 7
            }
        }

        shouldThrowSyntaxError("\"bad \\x esc\"") {
            message shouldEqual "Syntax Error: Invalid character escape sequence: \\x."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 7
            }
        }

        shouldThrowSyntaxError("\"bad \\u1 esc\"") {
            message shouldEqual "Syntax Error: Invalid character escape sequence: \\u1 es."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 7
            }
        }

        shouldThrowSyntaxError("\"bad \\u0XX1 esc\"") {
            message shouldEqual "Syntax Error: Invalid character escape sequence: \\u0XX1."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 7
            }
        }

        shouldThrowSyntaxError("\"bad \\uXXXX esc\"") {
            message shouldEqual "Syntax Error: Invalid character escape sequence: \\uXXXX."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 7
            }
        }

        shouldThrowSyntaxError("\"bad \\uFXXX esc\"") {
            message shouldEqual "Syntax Error: Invalid character escape sequence: \\uFXXX."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 7
            }
        }

        shouldThrowSyntaxError("\"bad \\uXXXF esc\"") {
            message shouldEqual "Syntax Error: Invalid character escape sequence: \\uXXXF."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 7
            }
        }
    }

    @Test
    fun `lexes block strings`() {
        lexOne("\"\"\"\"\"\"") {
            kind shouldEqual BLOCK_STRING
            start shouldEqual 0
            end shouldEqual 6
            value shouldEqual ""
        }

        lexOne("\"\"\"simple\"\"\"") {
            kind shouldEqual BLOCK_STRING
            start shouldEqual 0
            end shouldEqual 12
            value shouldEqual "simple"
        }

        lexOne("\"\"\" white space \"\"\"") {
            kind shouldEqual BLOCK_STRING
            start shouldEqual 0
            end shouldEqual 19
            value shouldEqual " white space "
        }

        lexOne("\"\"\"contains \" quote\"\"\"") {
            kind shouldEqual BLOCK_STRING
            start shouldEqual 0
            end shouldEqual 22
            value shouldEqual "contains \" quote"
        }

        lexOne("\"\"\"contains \\\"\"\" triplequote\"\"\"") {
            kind shouldEqual BLOCK_STRING
            start shouldEqual 0
            end shouldEqual 31
            value shouldEqual "contains \"\"\" triplequote"
        }

        lexOne("\"\"\"multi\nline\"\"\"") {
            kind shouldEqual BLOCK_STRING
            start shouldEqual 0
            end shouldEqual 16
            value shouldEqual "multi\nline"
        }

        lexOne("\"\"\"multi\rline\r\nnormalized\"\"\"") {
            kind shouldEqual BLOCK_STRING
            start shouldEqual 0
            end shouldEqual 28
            value shouldEqual "multi\nline\nnormalized"
        }

        lexOne("\"\"\"unescaped \\n\\r\\b\\t\\f\\u1234\"\"\"") {
            kind shouldEqual BLOCK_STRING
            start shouldEqual 0
            end shouldEqual 32
            value shouldEqual "unescaped \\n\\r\\b\\t\\f\\u1234"
        }

        lexOne("\"\"\"slashes \\\\ \\/\"\"\"") {
            kind shouldEqual BLOCK_STRING
            start shouldEqual 0
            end shouldEqual 19
            value shouldEqual "slashes \\\\ \\/"
        }


        lexOne("""""${'"'}

        spans
          multiple
            lines

        ""${'"'}""") {
            kind shouldEqual BLOCK_STRING
            start shouldEqual 0
            end shouldEqual 68
            value shouldEqual "spans\n  multiple\n    lines"
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
            kind shouldEqual NAME
            start shouldEqual 71
            end shouldEqual 83
            line shouldEqual 8
            column shouldEqual 6
            value shouldEqual "second_token"
        }


        val str2 = listOf(
            "\"\"\" \n",
            "spans \r\n",
            "multiple \n\r",
            "lines \n\n",
            "\"\"\"\n second_token"
        ).joinToString("")
        lexSecond(str2) {
            kind shouldEqual NAME
            start shouldEqual 37
            end shouldEqual 49
            line shouldEqual 8
            column shouldEqual 2
            value shouldEqual "second_token"
        }
    }

    @Test
    fun `lex reports useful block string errors`() {
        shouldThrowSyntaxError("\"\"\"") {
            message shouldEqual "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 4
            }
        }

        shouldThrowSyntaxError("\"\"\"no end quote") {
            message shouldEqual "Syntax Error: Unterminated string."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 16
            }
        }

        shouldThrowSyntaxError("\"\"\"contains unescaped \u0007 control char\"\"\"") {
            message shouldEqual "Syntax Error: Invalid character within String: \"\\u0007\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 23
            }
        }

        shouldThrowSyntaxError("\"\"\"null-byte is not \u0000 end of file\"\"\"") {
            message shouldEqual "Syntax Error: Invalid character within String: \"\\u0000\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 21
            }
        }
    }

    @Test
    fun `lexes numbers`() {
        lexOne("4") {
            kind shouldEqual INT
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual "4"
        }

        lexOne("4.123") {
            kind shouldEqual FLOAT
            start shouldEqual 0
            end shouldEqual 5
            value shouldEqual "4.123"
        }

        lexOne("-4") {
            kind shouldEqual INT
            start shouldEqual 0
            end shouldEqual 2
            value shouldEqual "-4"
        }

        lexOne("9") {
            kind shouldEqual INT
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual "9"
        }

        lexOne("0") {
            kind shouldEqual INT
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual "0"
        }

        lexOne("-4.123") {
            kind shouldEqual FLOAT
            start shouldEqual 0
            end shouldEqual 6
            value shouldEqual "-4.123"
        }

        lexOne("0.123") {
            kind shouldEqual FLOAT
            start shouldEqual 0
            end shouldEqual 5
            value shouldEqual "0.123"
        }

        lexOne("123e4") {
            kind shouldEqual FLOAT
            start shouldEqual 0
            end shouldEqual 5
            value shouldEqual "123e4"
        }

        lexOne("123E4") {
            kind shouldEqual FLOAT
            start shouldEqual 0
            end shouldEqual 5
            value shouldEqual "123E4"
        }

        lexOne("123e-4") {
            kind shouldEqual FLOAT
            start shouldEqual 0
            end shouldEqual 6
            value shouldEqual "123e-4"
        }

        lexOne("123e+4") {
            kind shouldEqual FLOAT
            start shouldEqual 0
            end shouldEqual 6
            value shouldEqual "123e+4"
        }

        lexOne("-1.123e4") {
            kind shouldEqual FLOAT
            start shouldEqual 0
            end shouldEqual 8
            value shouldEqual "-1.123e4"
        }

        lexOne("-1.123E4") {
            kind shouldEqual FLOAT
            start shouldEqual 0
            end shouldEqual 8
            value shouldEqual "-1.123E4"
        }

        lexOne("-1.123e-4") {
            kind shouldEqual FLOAT
            start shouldEqual 0
            end shouldEqual 9
            value shouldEqual "-1.123e-4"
        }

        lexOne("-1.123e+4") {
            kind shouldEqual FLOAT
            start shouldEqual 0
            end shouldEqual 9
            value shouldEqual "-1.123e+4"
        }

        lexOne("-1.123e4567") {
            kind shouldEqual FLOAT
            start shouldEqual 0
            end shouldEqual 11
            value shouldEqual "-1.123e4567"
        }
    }

    @Test
    fun `lex reports useful number errors`() {
        shouldThrowSyntaxError("00") {
            message shouldEqual "Syntax Error: Invalid number, unexpected digit after 0: \"0\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 2
            }
        }

        shouldThrowSyntaxError("01") {
            message shouldEqual "Syntax Error: Invalid number, unexpected digit after 0: \"1\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 2
            }
        }

        shouldThrowSyntaxError("01.23") {
            message shouldEqual "Syntax Error: Invalid number, unexpected digit after 0: \"1\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 2
            }
        }

        shouldThrowSyntaxError("+1") {
            message shouldEqual "Syntax Error: Cannot parse the unexpected character \"+\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 1
            }
        }

        shouldThrowSyntaxError("1.") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: <EOF>."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 3
            }
        }

        shouldThrowSyntaxError("1e") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: <EOF>."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 3
            }
        }

        shouldThrowSyntaxError("1E") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: <EOF>."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 3
            }
        }

        shouldThrowSyntaxError("1.e1") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \"e\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 3
            }
        }

        shouldThrowSyntaxError(".123") {
            message shouldEqual "Syntax Error: Cannot parse the unexpected character \".\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 1
            }
        }

        shouldThrowSyntaxError("1.A") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \"A\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 3
            }
        }

        shouldThrowSyntaxError("-A") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \"A\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 2
            }
        }

        shouldThrowSyntaxError("1.0e") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: <EOF>."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 5
            }
        }

        shouldThrowSyntaxError("1.0eA") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \"A\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 5
            }
        }

        shouldThrowSyntaxError("1.2e3e") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \"e\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 6
            }
        }

        shouldThrowSyntaxError("1.2e3.4") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \".\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 6
            }
        }

        shouldThrowSyntaxError("1.23.4") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \".\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 5
            }
        }
    }

    @Test
    fun `lex does not allow name-start after a number`() {
        shouldThrowSyntaxError("0xF1") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \"x\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 2
            }
        }

        shouldThrowSyntaxError("0b10") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \"b\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 2
            }
        }

        shouldThrowSyntaxError("123abc") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \"a\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 4
            }
        }

        shouldThrowSyntaxError("1_234") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \"_\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 2
            }
        }

        shouldThrowSyntaxError("1ß") {
            message shouldEqual "Syntax Error: Cannot parse the unexpected character \"\\u00DF\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 2
            }
        }

        shouldThrowSyntaxError("1.23f") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \"f\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 5
            }
        }

        shouldThrowSyntaxError("1.234_5") {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \"_\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 6
            }
        }

        shouldThrowSyntaxError("1ß") {
            message shouldEqual "Syntax Error: Cannot parse the unexpected character \"\\u00DF\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 2
            }
        }
    }

    @Test
    fun `lexes punctuation`() {
        lexOne("!") {
            kind shouldEqual BANG
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual null
        }

        lexOne("$") {
            kind shouldEqual DOLLAR
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual null
        }

        lexOne("(") {
            kind shouldEqual PAREN_L
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual null
        }

        lexOne(")") {
            kind shouldEqual PAREN_R
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual null
        }

        lexOne("...") {
            kind shouldEqual SPREAD
            start shouldEqual 0
            end shouldEqual 3
            value shouldEqual null
        }

        lexOne(":") {
            kind shouldEqual COLON
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual null
        }

        lexOne("=") {
            kind shouldEqual EQUALS
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual null
        }

        lexOne("@") {
            kind shouldEqual AT
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual null
        }

        lexOne("[") {
            kind shouldEqual BRACKET_L
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual null
        }

        lexOne("]") {
            kind shouldEqual BRACKET_R
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual null
        }

        lexOne("{") {
            kind shouldEqual BRACE_L
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual null
        }

        lexOne("|") {
            kind shouldEqual PIPE
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual null
        }

        lexOne("}") {
            kind shouldEqual BRACE_R
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual null
        }
    }

    @Test
    fun `lex reports useful unknown character error`() {
        shouldThrowSyntaxError("..") {
            message shouldEqual "Syntax Error: Cannot parse the unexpected character \".\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 1
            }
        }

        shouldThrowSyntaxError("?") {
            message shouldEqual "Syntax Error: Cannot parse the unexpected character \"?\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 1
            }
        }

        shouldThrowSyntaxError("\u203B") {
            message shouldEqual "Syntax Error: Cannot parse the unexpected character \"\\u203B\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 1
            }
        }

        shouldThrowSyntaxError("\u200b") {
            message shouldEqual "Syntax Error: Cannot parse the unexpected character \"\\u200B\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 1
            }
        }
    }

    @Test
    fun `lex reports useful information for dashes in names`() {
        val source = Source("a-b")
        val lexer = Lexer(source)
        val firstToken = lexer.advance()

        firstToken.run {
            kind shouldEqual NAME
            start shouldEqual 0
            end shouldEqual 1
            value shouldEqual "a"
        }

        val result = invoking { lexer.advance() } shouldThrow GraphQLError::class

        result.exception.run {
            message shouldEqual "Syntax Error: Invalid number, expected digit but got: \"b\"."
            locations!!.first().run {
                line shouldEqual 1
                column shouldEqual 3
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
            endToken.kind shouldNotEqual COMMENT
        } while (endToken?.kind != EOF)

        startToken.prev shouldEqual null
        endToken.next shouldEqual null

        val tokens = mutableListOf<Token>()
        var tok: Token? = startToken
        while (tok != null) {
            tokens.add(tok)
            tok = tok.next ?: break
            if (tokens.size != 0) {
                tok.prev shouldEqual tokens[tokens.size - 1]
            }
        }

        tokens.map { it.kind } shouldEqual listOf(
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
        isPunctuatorToken("!") shouldEqual true
        isPunctuatorToken("$") shouldEqual true
        isPunctuatorToken("&") shouldEqual true
        isPunctuatorToken("(") shouldEqual true
        isPunctuatorToken(")") shouldEqual true
        isPunctuatorToken("...") shouldEqual true
        isPunctuatorToken(":") shouldEqual true
        isPunctuatorToken("=") shouldEqual true
        isPunctuatorToken("@") shouldEqual true
        isPunctuatorToken("[") shouldEqual true
        isPunctuatorToken("]") shouldEqual true
        isPunctuatorToken("{") shouldEqual true
        isPunctuatorToken("|") shouldEqual true
        isPunctuatorToken("}") shouldEqual true
    }

    @Test
    fun `returns false for non-punctuator tokens`() {
        isPunctuatorToken("") shouldEqual false
        isPunctuatorToken("name") shouldEqual false
        isPunctuatorToken("1") shouldEqual false
        isPunctuatorToken("3.14") shouldEqual false
        isPunctuatorToken("\"str\"") shouldEqual false
        isPunctuatorToken("\"\"\"str\"\"\"") shouldEqual false
    }

}
