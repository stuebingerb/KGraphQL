package de.stuebingerb.kgraphql.specification.validation

import de.stuebingerb.kgraphql.KGraphQL.Companion.schema
import de.stuebingerb.kgraphql.Specification
import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.expectRequestError
import de.stuebingerb.kgraphql.specification.validation.SpecSchemas.Companion.baseSchema
import org.junit.jupiter.api.Test

@Specification("5.1 Documents")
class DocumentValidationTest {

    @Specification("5.1.1 Executable Definitions")
    @Test
    fun `document must only contain executable definitions`() {
        val testedSchema = schema { baseSchema() }

        // Counter Example 108 - https://spec.graphql.org/September2026/#example-12752
        expectRequestError<ValidationException>("Document must only contain executable definitions") {
            testedSchema.executeBlocking(
                """
                    query getDogName {
                      dog {
                        name
                        color
                      }
                    }
                    
                    # Our current parser does not support "extend"
                    # extend type Dog {
                    type Dog {
                      color: String
                    }
                """.trimIndent()
            )
        }
    }
}
