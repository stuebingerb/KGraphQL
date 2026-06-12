package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.expectRequestError
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.integration.BaseSchemaTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@Specification("3.2 Directives")
class DirectivesSpecificationTest : BaseSchemaTest() {

    @Test
    fun `query with @include directive on field`() {
        val map = execute("{film{title, year @include(if: false)}}")
        assertThrows<IllegalArgumentException> { map.extract("data/film/year") }
    }

    @Test
    fun `query with @skip directive on field`() {
        val map = execute("{film{title, year @skip(if: true)}}")
        assertThrows<IllegalArgumentException> { map.extract("data/film/year") }
    }

    @Test
    fun `query with @include and @skip directive on field`() {
        val mapBothSkip = execute("{film{title, year @include(if: false) @skip(if: true)}}")
        assertThrows<IllegalArgumentException> { mapBothSkip.extract("data/film/year") }

        val mapOnlySkip = execute("{film{title, year @include(if: true) @skip(if: true)}}")
        assertThrows<IllegalArgumentException> { mapOnlySkip.extract("data/film/year") }

        val mapOnlyInclude = execute("{film{title, year @include(if: false) @skip(if: false)}}")
        assertThrows<IllegalArgumentException> { mapOnlyInclude.extract("data/film/year") }

        val mapNeither = execute("{film{title, year @include(if: true) @skip(if: false)}}")
        mapNeither.extract<Int>("data/film/year") shouldBe 2006
    }

    @Test
    fun `query with @include and @skip directive on field object`() {
        val mapWithSkip = execute("{ number(big: true), film @skip(if: true) { title } }")
        assertThrows<IllegalArgumentException> { mapWithSkip.extract("data/film") }

        val mapWithoutSkip = execute("{ number(big: true), film @skip(if: false) { title } }")
        mapWithoutSkip.extract<String>("data/film/title") shouldBe "Prestige"

        val mapWithInclude = execute("{ number(big: true), film @include(if: true) { title } }")
        mapWithInclude.extract<String>("data/film/title") shouldBe "Prestige"

        val mapWithoutInclude = execute("{ number(big: true), film @include(if: false) { title } }")
        assertThrows<IllegalArgumentException> { mapWithoutInclude.extract("data/film") }
    }

    @Test
    fun `mutation with @include and @skip directive on field object`() {
        val mapWithSkip = execute("mutation { createActor(name: \"actor\", age: 42) @skip(if: true) { name age } }")
        assertThrows<IllegalArgumentException> { mapWithSkip.extract("data/createActor") }

        val mapWithoutSkip = execute("mutation { createActor(name: \"actor\", age: 42) @skip(if: false) { name age } }")
        mapWithoutSkip.extract<String>("data/createActor/name") shouldBe "actor"
        mapWithoutSkip.extract<Int>("data/createActor/age") shouldBe 42

        val mapWithInclude = execute("mutation { createActor(name: \"actor\", age: 42) @include(if: true) { name age } }")
        mapWithInclude.extract<String>("data/createActor/name") shouldBe "actor"
        mapWithInclude.extract<Int>("data/createActor/age") shouldBe 42

        val mapWithoutInclude = execute("mutation { createActor(name: \"actor\", age: 42) @include(if: false) { name age } }")
        assertThrows<IllegalArgumentException> { mapWithoutInclude.extract("data/createActor") }
    }

    @Test
    fun `query with @include directive on field with variable`() {
        val map = execute(
            "query film (\$include: Boolean!) {film{title, year @include(if: \$include)}}",
            "{\"include\":\"false\"}"
        )
        assertThrows<IllegalArgumentException> { map.extract("data/film/year") }
    }

    @Test
    fun `missing directive should result in an error`() {
        expectRequestError<ValidationException>("Directive 'nonExisting' does not exist") {
            testedSchema.executeBlocking("{film{title year @nonExisting}}")
        }
    }

    @Test
    fun `query with different @include and @skip directives on root level`() {
        // https://spec.graphql.org/September2025/#note-f3059
        // "Neither @skip nor @include has precedence over the other. In the case that both the @skip and @include
        // directives are provided on the same field or fragment, it must be queried only if the @skip condition is
        // false and the @include condition is true. Stated conversely, the field or fragment must not be queried if
        // either the @skip condition is true or the @include condition is false."
        val result = testedSchema.executeBlocking("""
            {
                number1: number(big: true)
                number2: number(big: true) @include(if: false)
                number3: number(big: true) @include(if: true)
                number4: number(big: true)
                number5: number(big: true) @skip(if: true)
                number6: number(big: true) @skip(if: false)
                number7: number(big: true) @skip(if: false) @include(if: true)
                number8: number(big: true) @skip(if: true) @include(if: true)
                number9: number(big: true) @skip(if: true) @include(if: false)
                number10: number(big: true) @skip(if: false) @include(if: false)
            }
        """.trimIndent())

        result shouldBe """
            {
              "data" : {
                "number1" : 10000,
                "number3" : 10000,
                "number4" : 10000,
                "number6" : 10000,
                "number7" : 10000
              }
            }
        """.trimIndent()
    }
}
