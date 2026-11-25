package de.stuebingerb.kgraphql.specification.validation

import de.stuebingerb.kgraphql.KGraphQL.Companion.schema
import de.stuebingerb.kgraphql.schema.SchemaPrinter
import de.stuebingerb.kgraphql.schema.dsl.SchemaBuilder
import de.stuebingerb.kgraphql.schema.scalar.ID
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Collection of schema definitions used in the GraphQL specification in order to be able to implement validation
 * tests as close to the spec as possible.
 */
@Suppress("unused")
class SpecSchemas {
    interface Sentient {
        val name: String
    }

    interface Pet {
        val name: String
    }

    data class Alien(
        override val name: String,
        val homePlanet: String?
    ) : Sentient, HumanOrAlien

    data class Human(
        override val name: String,
        val pets: List<Pet>?
    ) : Sentient, DogOrHuman, HumanOrAlien

    data class Cat(
        override val name: String,
        val nickname: String?,
        val meowVolume: Int?
    ) : CatOrDog, Pet

    data class Dog(
        override val name: String,
        val nickname: String?,
        val barkVolume: Int?,
        val owner: Human?
    ) : CatOrDog, DogOrHuman, Pet

    enum class DogCommand {
        SIT, DOWN, HEEL
    }

    enum class CatCommand {
        JUMP
    }

    sealed interface CatOrDog
    sealed interface DogOrHuman
    sealed interface HumanOrAlien

    data class FindDogInput(
        val name: String?,
        val owner: String?
    )

    data class CatInput(
        val name: String,
        val nickname: String?,
        val meowVolume: Int?
    ) : PetInput

    data class DogInput(
        val name: String,
        val nickname: String?,
        val barkVolume: Int?
    ) : PetInput

    sealed interface PetInput

    interface Node {
        val id: ID
    }

    interface Resource : Node {
        val url: String?
    }

    data class ResourceImpl(
        override val id: ID,
        override val url: String?
    ) : Resource

    class Arguments

    companion object {
        // Base schema from https://spec.graphql.org/September2026/#sec-Validation.Examples
        // https://spec.graphql.org/September2026/#example-14b1a
        val baseSchema: SchemaBuilder.() -> Unit = {
            query("dog") {
                resolver<Dog?> { null }
            }
            query("findDog") {
                resolver<Dog?, FindDogInput?> { searchBy: FindDogInput? -> null }
            }

            mutation("addPet") {
                resolver<Pet?, PetInput> { pet: PetInput ->
                    when (pet) {
                        is CatInput -> Cat(pet.name, pet.nickname, pet.meowVolume)
                        is DogInput -> Dog(pet.name, pet.nickname, pet.barkVolume, null)
                    }
                }
            }
            mutation("addPets") {
                resolver<List<Pet?>?, List<PetInput>> { pets: List<PetInput> -> null }
            }

            enum<DogCommand>()
            enum<CatCommand>()

            type<Alien>()
            type<Human>()
            type<Sentient>()
            type<Pet>()

            inputType<CatInput>()
            inputType<DogInput>()

            inputType<PetInput> {
                CatInput::class.configure {
                    name = "cat"
                }
                DogInput::class.configure {
                    name = "dog"
                }
            }

            type<Cat> {
                property("doesKnowCommand") {
                    resolver { cat: Cat, catCommand: CatCommand -> false }
                }
            }
            type<Dog> {
                property("doesKnowCommand") {
                    resolver { dog: Dog, dogCommand: DogCommand -> true }
                }
                property("isHouseTrained") {
                    resolver { dog: Dog, atOtherHomes: Boolean? -> true }
                }
            }

            unionType<CatOrDog>()
            unionType<DogOrHuman>()
            unionType<HumanOrAlien>()
        }

        // https://spec.graphql.org/September2026/#example-3aac3
        val withBooleanList: SchemaBuilder.() -> Unit = {
            query("booleanList") {
                resolver { booleanListArg: List<Boolean>? -> true }
            }
        }

        // https://spec.graphql.org/September2026/#example-73706
        val withArguments: SchemaBuilder.() -> Unit = {
            query("arguments") {
                resolver<Arguments?> { Arguments() }
            }
            type<Arguments> {
                property("multipleRequirements") {
                    resolver { _: Arguments, x: Int, y: Int -> x + y }
                }
                property("booleanArgField") {
                    resolver { _: Arguments, booleanArg: Boolean? -> booleanArg }
                }
                property("floatArgField") {
                    resolver { _: Arguments, floatArg: Float? -> floatArg }
                }
                property("intArgField") {
                    resolver { _: Arguments, intArg: Int? -> intArg }
                }
                property("nonNullBooleanArgField") {
                    resolver { _: Arguments, nonNullBooleanArg: Boolean -> nonNullBooleanArg }
                }
                property<List<Boolean?>?>("booleanListArgField") {
                    resolver { _: Arguments, booleanListArg: List<Boolean?> -> booleanListArg }
                }
                property("optionalNonNullBooleanArgField") {
                    resolver { _: Arguments, optionalBooleanArg: Boolean -> optionalBooleanArg }.withArgs {
                        arg<Boolean> {
                            name = "optionalBooleanArg"; defaultValue = false
                        }
                    }
                }
            }
        }

        // https://spec.graphql.org/September2026/#example-ba6cb
        // https://spec.graphql.org/September2026/#example-bc12a
        val nodeSchema: SchemaBuilder.() -> Unit = {
            query("resources") {
                resolver { -> listOf<Resource>() }
            }
            type<Node>()
            type<ResourceImpl>()
        }
    }

    @Test
    fun `validate tested schemas`() {
        SchemaPrinter().print(schema { baseSchema() }) shouldBe """
            type Alien implements Sentient {
              homePlanet: String
              name: String!
            }
            
            type Cat implements Pet {
              doesKnowCommand(catCommand: CatCommand!): Boolean!
              meowVolume: Int
              name: String!
              nickname: String
            }
            
            type Dog implements Pet {
              barkVolume: Int
              doesKnowCommand(dogCommand: DogCommand!): Boolean!
              isHouseTrained(atOtherHomes: Boolean): Boolean!
              name: String!
              nickname: String
              owner: Human
            }

            type Human implements Sentient {
              name: String!
              pets: [Pet!]
            }
            
            type Mutation {
              addPet(pet: PetInput!): Pet
              addPets(pets: [PetInput!]!): [Pet]
            }
            
            type Query {
              dog: Dog
              findDog(searchBy: FindDogInput): Dog
            }

            interface Pet {
              name: String!
            }

            interface Sentient {
              name: String!
            }
            
            union CatOrDog = Cat | Dog

            union DogOrHuman = Dog | Human

            union HumanOrAlien = Alien | Human
            
            enum CatCommand {
              JUMP
            }

            enum DogCommand {
              DOWN
              HEEL
              SIT
            }

            input CatInput {
              meowVolume: Int
              name: String!
              nickname: String
            }

            input DogInput {
              barkVolume: Int
              name: String!
              nickname: String
            }
            
            input FindDogInput {
              name: String
              owner: String
            }

            input PetInput @oneOf {
              cat: CatInput
              dog: DogInput
            }
            
        """.trimIndent()

        SchemaPrinter().print(schema { withBooleanList() }) shouldBe """
            type Query {
              booleanList(booleanListArg: [Boolean!]): Boolean!
            }
            
        """.trimIndent()

        SchemaPrinter().print(schema { withArguments() }) shouldBe """
            type Arguments {
              booleanArgField(booleanArg: Boolean): Boolean
              booleanListArgField(booleanListArg: [Boolean]!): [Boolean]
              floatArgField(floatArg: Float): Float
              intArgField(intArg: Int): Int
              multipleRequirements(x: Int!, y: Int!): Int!
              nonNullBooleanArgField(nonNullBooleanArg: Boolean!): Boolean!
              optionalNonNullBooleanArgField(optionalBooleanArg: Boolean! = false): Boolean!
            }
            
            type Query {
              arguments: Arguments
            }
            
        """.trimIndent()

        SchemaPrinter().print(schema { nodeSchema() }) shouldBe """
            type Query {
              resources: [Resource!]!
            }
            
            type ResourceImpl implements Node & Resource {
              id: ID!
              url: String
            }
            
            interface Node {
              id: ID!
            }
            
            interface Resource implements Node {
              id: ID!
              url: String
            }
            
        """.trimIndent()
    }
}
