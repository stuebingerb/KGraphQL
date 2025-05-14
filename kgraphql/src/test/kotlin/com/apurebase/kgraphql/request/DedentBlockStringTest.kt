package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.schema.structure.dedentBlockStringValue
import com.apurebase.kgraphql.schema.structure.getBlockStringIndentation
import com.apurebase.kgraphql.schema.structure.printBlockString
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

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
        dedentBlockStringValue(rawValue) shouldBe joinLines(
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
        dedentBlockStringValue(rawValue) shouldBe joinLines(
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
        dedentBlockStringValue(rawValue) shouldBe joinLines(
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
        dedentBlockStringValue(rawValue) shouldBe joinLines(
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
        dedentBlockStringValue(rawValue) shouldBe joinLines(
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
        getBlockStringIndentation(listOf()) shouldBe 0
    }

    @Test
    fun `do not take first line into account`() {
        getBlockStringIndentation(listOf("  a")) shouldBe 0
        getBlockStringIndentation(listOf(" a", "  b")) shouldBe 2
    }

    @Test
    fun `returns minimal indentation length`() {
        getBlockStringIndentation(listOf("", " a", "  b")) shouldBe 1
        getBlockStringIndentation(listOf("", "  a", " b")) shouldBe 1
        getBlockStringIndentation(listOf("", "  a", " b", "c")) shouldBe 0
    }

    @Test
    fun `count both tab and space as single character`() {
        getBlockStringIndentation(listOf("", "\ta", "          b")) shouldBe 1
        getBlockStringIndentation(
            listOf(
                "",
                "\t a",
                "          b"
            )
        ) shouldBe 2
        getBlockStringIndentation(
            listOf(
                "",
                " \t a",
                "          b"
            )
        ) shouldBe 3
    }

    @Test
    fun `do not take empty lines into account`() {
        getBlockStringIndentation(listOf("a", "\t")) shouldBe 0
        getBlockStringIndentation(listOf("a", " ")) shouldBe 0
        getBlockStringIndentation(listOf("a", " ", "  b")) shouldBe 2
        getBlockStringIndentation(listOf("a", " ", "  b")) shouldBe 2
        getBlockStringIndentation(listOf("a", "", " b")) shouldBe 1
    }

    //////////////////////////////////////////
    //////////// printBlockString ////////////
    //////////////////////////////////////////
    @Test
    fun `by default print block strings as single line`() {
        val str = "one liner"
        printBlockString(str) shouldBe "\"\"\"one liner\"\"\""
        printBlockString(str, "", true) shouldBe "\"\"\"\none liner\n\"\"\""
    }

    @Test
    fun `correctly prints single-line with leading space`() {
        val str = "    space-led string"
        printBlockString(str) shouldBe "\"\"\"    space-led string\"\"\""
        printBlockString(str, "", true) shouldBe "\"\"\"    space-led string\n\"\"\""
    }

    @Test
    fun `correctly prints single-line with leading space and quotation`() {
        val str = "    space-led value \"quoted string\""

        printBlockString(str) shouldBe "\"\"\"    space-led value \"quoted string\"\n\"\"\""
        printBlockString(str, "", true) shouldBe "\"\"\"    space-led value \"quoted string\"\n\"\"\""
    }

    @Test
    fun `correctly prints string with a first line indentation`() {
        val str = joinLines(
            "    first  ",
            "  line     ",
            "indentation",
            "     string"
        )

        printBlockString(str) shouldBe joinLines(
            "\"\"\"",
            "    first  ",
            "  line     ",
            "indentation",
            "     string",
            "\"\"\""
        )
    }

}
