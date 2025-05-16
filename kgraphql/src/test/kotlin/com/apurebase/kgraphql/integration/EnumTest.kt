package com.apurebase.kgraphql.integration

import com.apurebase.kgraphql.FilmType
import com.apurebase.kgraphql.assertNoErrors
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EnumTest : BaseSchemaTest() {

    @Test
    fun `query with enum field`() {
        val map = execute("{film{type}}")
        assertNoErrors(map)
        map.extract<String>("data/film/type") shouldBe "FULL_LENGTH"
    }

    @Test
    fun `query with enum argument`() {
        val map = execute("{ films: filmsByType(type: FULL_LENGTH){title, type}}")
        assertNoErrors(map)
        map.extract<String>("data/films[0]/type") shouldBe "FULL_LENGTH"
        map.extract<String>("data/films[1]/type") shouldBe "FULL_LENGTH"
    }

    @Test
    fun `query with enum array variables`() {
        val schema = defaultSchema {
            configure {
                wrapErrors = false
            }
            enum<FilmType>()
            query("search") {
                description = "film categorized by type"
                resolver { types: List<FilmType> ->
                    "You searched for: ${types.joinToString { it.name }}"
                }
            }
        }

        val map = schema.executeBlocking(
            request = "query Search(${'$'}types: [FilmType!]!) { search(types: ${'$'}types)}",
            variables = "{\"types\":[\"FULL_LENGTH\"]}"
        ).deserialize()

        assertNoErrors(map)
        map.extract<String>("data/search") shouldBe "You searched for: FULL_LENGTH"
    }
}
