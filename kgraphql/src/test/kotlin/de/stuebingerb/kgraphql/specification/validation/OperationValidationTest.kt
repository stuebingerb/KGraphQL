package de.stuebingerb.kgraphql.specification.validation

import de.stuebingerb.kgraphql.KGraphQL
import de.stuebingerb.kgraphql.Specification
import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.expectRequestError
import de.stuebingerb.kgraphql.specification.validation.SpecSchemas.Companion.baseSchema
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Specification("5.2 Operations")
class OperationValidationTest {

    @Specification("5.2.1.1 Operation Type Existence")
    @Test
    fun `operation type must exist`() {
        val testedSchema = KGraphQL.schema {
            query("hello") {
                resolver { -> "world" }
            }
        }

        // Example 110 - https://spec.graphql.org/September2026/#example-ed01f
        testedSchema.executeBlocking(
            """
                query helloQuery {
                  hello
                }
            """.trimIndent()
        ) shouldBe """
            {"data":{"hello":"world"}}
        """.trimIndent()

        // Counter Example 111 - https://spec.graphql.org/September2026/#example-55de2
        expectRequestError<ValidationException>("Mutations are not supported on this schema") {
            testedSchema.executeBlocking(
                """
                    mutation goodbyeMutation {
                      goodbye
                    }
                """.trimIndent()
            )
        }
    }

    @Specification("5.2.2.1 Operation Name Uniqueness")
    @Test
    fun `operation name must be unique`() {
        val testedSchema = KGraphQL.schema { baseSchema() }

        // Example 112 - https://spec.graphql.org/September2026/#example-069e1
        testedSchema.executeBlocking(
            request = """
                query getDogName {
                  dog {
                    name
                  }
                }
                
                query getOwnerName {
                  dog {
                    owner {
                      name
                    }
                  }
                }
            """.trimIndent(),
            operationName = "getDogName"
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Counter Example 113 - https://spec.graphql.org/September2026/#example-5e409
        expectRequestError<ValidationException>("Operation 'getName' is not unique in document") {
            testedSchema.executeBlocking(
                """
                    query getName {
                      dog {
                        name
                      }
                    }
                    
                    query getName {
                      dog {
                        owner {
                          name
                        }
                      }
                    }
                """.trimIndent()
            )
        }

        // Counter Example 114 - https://spec.graphql.org/September2026/#example-77c2e
        expectRequestError<ValidationException>("Operation 'dogOperation' is not unique in document") {
            testedSchema.executeBlocking(
                """
                    query dogOperation {
                      dog {
                        name
                      }
                    }
                    
                    mutation dogOperation {
                      mutateDog {
                        id
                      }
                    }
                """.trimIndent()
            )
        }
    }

    @Specification("5.2.3.1 Lone Anonymous Operation")
    @Test
    fun `anonymous operation must be the only defined operation`() {
        val testedSchema = KGraphQL.schema { baseSchema() }

        // Example 115 - https://spec.graphql.org/September2026/#example-be853
        testedSchema.executeBlocking(
            """
            {
              dog {
                name
              }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Counter Example 116 - https://spec.graphql.org/September2026/#example-44b85
        expectRequestError<ValidationException>("Anonymous operation must be the only defined operation") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    name
                  }
                }
                
                query getName {
                  dog {
                    owner {
                      name
                    }
                  }
                }
            """.trimIndent()
            )
        }
    }

    @Specification("5.2.4.1 Single Root Field")
    @Test
    @Disabled("Subscriptions are not yet supported (#299)")
    fun `subscription operations must have exactly one root field`() {
        // Example 117 - https://spec.graphql.org/September2026/#example-5bbc3
        // Example 118 - https://spec.graphql.org/September2026/#example-13061
        // Counter Example 119 - https://spec.graphql.org/September2026/#example-3997d
        // Counter Example 120 - https://spec.graphql.org/September2026/#example-18466
        // Counter Example 121 - https://spec.graphql.org/September2026/#example-53457
        // Counter Example 122 - https://spec.graphql.org/September2026/#example-a8fa1
    }
}
