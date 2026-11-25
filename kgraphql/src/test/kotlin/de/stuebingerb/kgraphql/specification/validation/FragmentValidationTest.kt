package de.stuebingerb.kgraphql.specification.validation

import de.stuebingerb.kgraphql.KGraphQL
import de.stuebingerb.kgraphql.Specification
import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.expectRequestError
import de.stuebingerb.kgraphql.specification.validation.SpecSchemas.Companion.baseSchema
import de.stuebingerb.kgraphql.specification.validation.SpecSchemas.Companion.nodeSchema
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@Specification("5.5 Fragments")
class FragmentValidationTest {

    @Specification("5.5.1.1 Fragment Name Uniqueness")
    @Test
    fun `fragment names must be unique`() {
        val testedSchema = KGraphQL.schema { baseSchema() }

        // Example 150 - https://spec.graphql.org/September2026/#example-3703b
        testedSchema.executeBlocking(
            """
            {
              dog {
                ...fragmentOne
                ...fragmentTwo
              }
            }

            fragment fragmentOne on Dog {
              name
            }

            fragment fragmentTwo on Dog {
              owner {
                name
              }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Counter Example 151 - https://spec.graphql.org/September2026/#example-2c3e3
        expectRequestError<ValidationException>("There can be only one fragment named 'fragmentOne'") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    ...fragmentOne
                  }
                }
                
                fragment fragmentOne on Dog {
                  name
                }
                
                fragment fragmentOne on Dog {
                  owner {
                    name
                  }
                }
            """.trimIndent()
            )
        }
    }

    @Specification("5.5.1.2 Fragment Spread Type Existence")
    @Test
    fun `fragment spread types must exist`() {
        val testedSchema = KGraphQL.schema { baseSchema() }

        // Example 152 - https://spec.graphql.org/September2026/#example-1b2da
        testedSchema.executeBlocking(
            """
            {
              dog {
                ...correctType
              }
            }

            fragment correctType on Dog {
              name
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Example 152 - https://spec.graphql.org/September2026/#example-1b2da
        testedSchema.executeBlocking(
            """
            {
              dog {
                ...inlineFragment
              }
            }

            fragment inlineFragment on Dog {
              ... on Dog {
                name
              }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Example 152 - https://spec.graphql.org/September2026/#example-1b2da
        testedSchema.executeBlocking(
            """
            {
              dog {
                ...inlineFragment2
              }
            }

            fragment inlineFragment2 on Dog {
              ... @include(if: true) {
                name
              }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Counter Example 153 - https://spec.graphql.org/September2026/#example-463f6
        expectRequestError<ValidationException>("Unknown type 'NotInSchema' in type condition on fragment 'notOnExistingType'") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    ...notOnExistingType
                  }
                }
    
                fragment notOnExistingType on NotInSchema {
                  name
                }
            """.trimIndent()
            )
        }

        // Counter Example 153 - https://spec.graphql.org/September2026/#example-463f6
        expectRequestError<ValidationException>("Unknown type 'NotInSchema' in type condition on inline fragment") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    ...inlineNotExistingType
                  }
                }
    
                fragment inlineNotExistingType on Dog {
                  ... on NotInSchema {
                    name
                  }
                }
            """.trimIndent()
            )
        }
    }

    @Specification("5.5.1.3 Fragments on Object, Interface or Union Types")
    @Test
    fun `fragments may only be defined on Object, Interface or Union Types`() {
        val testedSchema = KGraphQL.schema { baseSchema() }

        // Example 154 - https://spec.graphql.org/September2026/#example-3c8d4
        testedSchema.executeBlocking(
            """
            {
              dog {
                ...fragOnObject
              }
            }

            fragment fragOnObject on Dog {
              name
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Example 154 - https://spec.graphql.org/September2026/#example-3c8d4
        testedSchema.executeBlocking(
            """
            {
              dog {
                ...fragOnInterface
              }
            }

            fragment fragOnInterface on Pet {
              name
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Example 154 - https://spec.graphql.org/September2026/#example-3c8d4
        testedSchema.executeBlocking(
            """
            {
              dog {
                ...fragOnUnion
              }
            }

            fragment fragOnUnion on CatOrDog {
              ... on Dog {
                name
              }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Counter Example 155 - https://spec.graphql.org/September2026/#example-4d5e5
        expectRequestError<ValidationException>("Fragments can only be specified on object types, interfaces, and unions but 'Int' is SCALAR on fragment 'fragOnScalar'") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    ...fragOnScalar
                  }
                }
    
                fragment fragOnScalar on Int {
                  something
                }
            """.trimIndent()
            )
        }

        // Counter Example 155 - https://spec.graphql.org/September2026/#example-4d5e5
        expectRequestError<ValidationException>("Fragments can only be specified on object types, interfaces, and unions but 'Boolean' is SCALAR on inline fragment") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    ...inlineFragOnScalar
                  }
                }
    
                fragment inlineFragOnScalar on Dog {
                  ... on Boolean {
                    somethingElse
                  }
                }
            """.trimIndent()
            )
        }
    }

    @Specification("5.5.1.4 Fragments Must Be Used")
    @Test
    fun `fragments must be used`() {
        val testedSchema = KGraphQL.schema { baseSchema() }

        // Counter Example 156 - https://spec.graphql.org/September2026/#example-9e1e3
        expectRequestError<ValidationException>("Found unused fragments: [nameFragment]") {
            testedSchema.executeBlocking(
                """
                fragment nameFragment on Dog { # unused
                  name
                }
                
                {
                  dog {
                    name
                  }
                }
            """.trimIndent()
            )
        }
    }

    @Specification("5.5.2.1 Fragment Spread Target Defined")
    @Test
    fun `fragment spread target must be defined`() {
        val testedSchema = KGraphQL.schema { baseSchema() }

        // Counter Example 157 - https://spec.graphql.org/September2026/#example-28421
        expectRequestError<ValidationException>("Fragment 'undefinedFragment' not found") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    ...undefinedFragment
                  }
                }
            """.trimIndent()
            )
        }
    }

    @Specification("5.5.2.2 Fragment Spreads Must Not Form Cycles")
    @Test
    fun `fragment spreads must not form cycles`() {
        val testedSchema = KGraphQL.schema { baseSchema() }

        // Counter Example 158 - https://spec.graphql.org/September2026/#example-9ceb4
        expectRequestError<ValidationException>("Fragment spread circular references are not allowed") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    ...nameFragment
                  }
                }

                fragment nameFragment on Dog {
                  name
                  ...barkVolumeFragment
                }

                fragment barkVolumeFragment on Dog {
                  barkVolume
                  ...nameFragment
                }
            """.trimIndent()
            )
        }

        // Example 159 - https://spec.graphql.org/September2026/#example-08734
        testedSchema.executeBlocking(
            """
            {
              dog {
                name
                barkVolume
                name
                barkVolume
                name
                barkVolume
                name
                # forever...
              }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Counter Example 160 - https://spec.graphql.org/September2026/#example-cd11c
        expectRequestError<ValidationException>("Fragment spread circular references are not allowed") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    ...dogFragment
                  }
                }

                fragment dogFragment on Dog {
                  name
                  owner {
                    ...ownerFragment
                  }
                }

                fragment ownerFragment on Human {
                  name
                  pets {
                    ...dogFragment
                  }
                }
            """.trimIndent()
            )
        }
    }

    @Specification("5.5.2.3 Fragment Spread Is Possible")
    @Test
    fun `fragment spreads must be possible`() {
        val testedSchema = KGraphQL.schema { baseSchema() }

        // 5.5.2.3.1 Object Spreads in Object Scope
        // Example 161 - https://spec.graphql.org/September2026/#example-0fc38
        testedSchema.executeBlocking(
            """
            {
              dog {
                ...dogFragment
              }
            }

            fragment dogFragment on Dog {
              ... on Dog {
                barkVolume
              }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Counter Example 162 - https://spec.graphql.org/September2026/#example-4d411
        expectRequestError<ValidationException>("Invalid type 'Cat' in type condition on inline fragment of type 'Dog'; must be one of '[Dog]'") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    ...catInDogFragmentInvalid
                  }
                }
    
                fragment catInDogFragmentInvalid on Dog {
                  ... on Cat {
                    meowVolume
                  }
                }
            """.trimIndent()
            )
        }

        // 5.5.2.3.2 Abstract Spreads in Object Scope
        // Example 163 - https://spec.graphql.org/September2026/#example-2c8d0
        testedSchema.executeBlocking(
            """
            {
              dog {
                ...interfaceWithinObjectFragment
              }
            }

            fragment petNameFragment on Pet {
              name
            }
            
            fragment interfaceWithinObjectFragment on Dog {
              ...petNameFragment
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Example 164 - https://spec.graphql.org/September2026/#example-41843
        testedSchema.executeBlocking(
            """
            {
              dog {
                ...unionWithObjectFragment
              }
            }

            fragment catOrDogNameFragment on CatOrDog {
              ... on Cat {
                meowVolume
              }
            }
            
            fragment unionWithObjectFragment on Dog {
              ...catOrDogNameFragment
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // 5.5.2.3.3 Object Spreads in Abstract Scope
        // Example 165 - https://spec.graphql.org/September2026/#example-85110
        testedSchema.executeBlocking(
            """
            {
              dog {
                ...petFragment
                ...catOrDogFragment
              }
            }

            fragment petFragment on Pet {
              name
              ... on Dog {
                barkVolume
              }
            }
            
            fragment catOrDogFragment on CatOrDog {
              ... on Cat {
                meowVolume
              }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Counter Example 166 - https://spec.graphql.org/September2026/#example-a8dcc
        expectRequestError<ValidationException>("Invalid type 'Dog' in type condition on inline fragment of type 'Sentient'; must be one of '[Human, Alien]'") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    ...sentientFragment
                  }
                }
    
                fragment sentientFragment on Sentient {
                  ... on Dog {
                    barkVolume
                  }
                }
            """.trimIndent()
            )
        }

        // Counter Example 166 - https://spec.graphql.org/September2026/#example-a8dcc
        expectRequestError<ValidationException>("Invalid type 'Cat' in type condition on inline fragment of type 'HumanOrAlien'; must be one of '[Alien, Human]'") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    ...humanOrAlienFragment
                  }
                }
    
                fragment humanOrAlienFragment on HumanOrAlien {
                  ... on Cat {
                    meowVolume
                  }
                }
            """.trimIndent()
            )
        }

        // 5.5.2.3.4 Abstract Spreads in Abstract Scope
        // Example 167 - https://spec.graphql.org/September2026/#example-dc875
        testedSchema.executeBlocking(
            """
            {
              dog {
                ...unionWithInterface
              }
            }

            fragment unionWithInterface on Pet {
              ...dogOrHumanFragment
            }
            
            fragment dogOrHumanFragment on DogOrHuman {
              ... on Dog {
                barkVolume
              }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"dog":null}}
        """.trimIndent()

        // Counter Example 168 - https://spec.graphql.org/September2026/#example-c9c63
        expectRequestError<ValidationException>("Invalid type 'Sentient' in type condition on fragment 'sentientFragment' of type 'Pet'; must be one of '[Cat, Dog]'") {
            testedSchema.executeBlocking(
                """
                {
                  dog {
                    ...nonIntersectingInterfaces
                  }
                }
    
                fragment nonIntersectingInterfaces on Pet {
                  ...sentientFragment
                }
                
                fragment sentientFragment on Sentient {
                  name
                }
            """.trimIndent()
            )
        }

        // Example 169 - https://spec.graphql.org/September2026/#example-bc12a
        val nodeSchema = KGraphQL.schema { nodeSchema() }
        nodeSchema.executeBlocking(
            """
            {
                resources {
                    ...interfaceWithInterface
                }
            }
            fragment interfaceWithInterface on Node {
              ...resourceFragment
            }
            
            fragment resourceFragment on Resource {
              url
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"resources":[]}}
        """.trimIndent()
    }
}
