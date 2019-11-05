package com.apurebase.kgraphql.jol

import com.apurebase.kgraphql.schema.jol.dedentBlockStringValue
import com.apurebase.kgraphql.schema.jol.getBlockStringIndentation
import com.apurebase.kgraphql.schema.jol.printBlockString
import org.amshove.kluent.shouldEqual
import org.junit.Test

class DedentBlockStringTest {

    private fun joinLines(vararg lines: String) = lines.joinToString("\n")

    ////////////////////////////////////////////
    ////////// dedentBlockStringValue //////////
    ////////////////////////////////////////////
    @Test
    fun `removes uniform indentation from a string`() {
        val rawValue = joinLines(
            "",
            "    Hello,",
            "      World!",
            "",
            "    Yours,",
            "      GraphQL."
        )
        dedentBlockStringValue(rawValue) shouldEqual joinLines(
            "Hello,",
            "  World!",
            "",
            "Yours,",
            "  GraphQL."
        )
    }

    @Test
    fun `removes empty leading and trailing lines`() {
        val rawValue = joinLines(
            "",
            "",
            "    Hello,",
            "      World!",
            "",
            "    Yours,",
            "      GraphQL.",
            "",
            ""
        )
        dedentBlockStringValue(rawValue) shouldEqual joinLines(
            "Hello,",
            "  World!",
            "",
            "Yours,",
            "  GraphQL."
        )
    }

    @Test
    fun `removes blank leading and trailing lines`() {
        val rawValue = joinLines(
            "  ",
            "        ",
            "    Hello,",
            "      World!",
            "",
            "    Yours,",
            "      GraphQL.",
            "        ",
            "  "
        )
        dedentBlockStringValue(rawValue) shouldEqual joinLines(
            "Hello,",
            "  World!",
            "",
            "Yours,",
            "  GraphQL."
        )
    }

    @Test
    fun `retains indentation from first line`() {
        val rawValue = joinLines(
            "    Hello,",
            "      World!",
            "",
            "    Yours,",
            "      GraphQL."
        )
        dedentBlockStringValue(rawValue) shouldEqual joinLines(
            "    Hello,",
            "  World!",
            "",
            "Yours,",
            "  GraphQL."
        )
    }

    @Test
    fun `does not alter trailing spaces`() {
        val rawValue = joinLines(
            "               ",
            "    Hello,     ",
            "      World!   ",
            "               ",
            "    Yours,     ",
            "      GraphQL. ",
            "               "
        )
        dedentBlockStringValue(rawValue) shouldEqual joinLines(
            "Hello,     ",
            "  World!   ",
            "           ",
            "Yours,     ",
            "  GraphQL. "
        )
    }



    ///////////////////////////////////////////
    //////// getBlockStringIndentation ////////
    ///////////////////////////////////////////
    @Test
    fun `returns zero for an empty array`() {
        getBlockStringIndentation(listOf()) shouldEqual 0
    }

    @Test
    fun `do not take first line into account`() {
        getBlockStringIndentation(listOf("  a")) shouldEqual 0
        getBlockStringIndentation(listOf(" a", "  b")) shouldEqual 2
    }

    @Test
    fun `returns minimal indentation length`() {
        getBlockStringIndentation(listOf("", " a", "  b")) shouldEqual 1
        getBlockStringIndentation(listOf("", "  a", " b")) shouldEqual 1
        getBlockStringIndentation(listOf("", "  a", " b", "c")) shouldEqual 0
    }

    @Test
    fun `count both tab and space as single character`() {
        getBlockStringIndentation(listOf("", "\ta", "          b")) shouldEqual 1
        getBlockStringIndentation(listOf("", "\t a", "          b")) shouldEqual 2
        getBlockStringIndentation(listOf("", " \t a", "          b")) shouldEqual 3
    }

    @Test
    fun `do not take empty lines into account`() {
        getBlockStringIndentation(listOf("a", "\t")) shouldEqual 0
        getBlockStringIndentation(listOf("a", " ")) shouldEqual 0
        getBlockStringIndentation(listOf("a", " ", "  b")) shouldEqual 2
        getBlockStringIndentation(listOf("a", " ", "  b")) shouldEqual 2
        getBlockStringIndentation(listOf("a", "", " b")) shouldEqual 1
    }

    //////////////////////////////////////////
    //////////// printBlockString ////////////
    //////////////////////////////////////////
    @Test
    fun `by default print block strings as single line`() {
        val str = "one liner"
        printBlockString(str) shouldEqual "\"\"\"one liner\"\"\""
        printBlockString(str, "", true) shouldEqual "\"\"\"\none liner\n\"\"\""
    }

    @Test
    fun `correctly prints single-line with leading space`() {
        val str = "    space-led string"
        printBlockString(str) shouldEqual "\"\"\"    space-led string\"\"\""
        printBlockString(str, "", true) shouldEqual "\"\"\"    space-led string\n\"\"\""
    }

    @Test
    fun `correctly prints single-line with leading space and quotation`() {
        val str = "    space-led value \"quoted string\""

        printBlockString(str) shouldEqual "\"\"\"    space-led value \"quoted string\"\n\"\"\""
        printBlockString(str, "", true) shouldEqual "\"\"\"    space-led value \"quoted string\"\n\"\"\""
    }

    @Test
    fun `correctly prints string with a first line indentation`() {
        val str = joinLines(
            "    first  ",
            "  line     ",
            "indentation",
            "     string"
        )

        printBlockString(str) shouldEqual joinLines(
            "\"\"\"",
            "    first  ",
            "  line     ",
            "indentation",
            "     string",
            "\"\"\""
        )
    }

}
