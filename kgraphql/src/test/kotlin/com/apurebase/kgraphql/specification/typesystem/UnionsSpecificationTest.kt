package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import com.apurebase.kgraphql.helpers.getFields
import com.apurebase.kgraphql.integration.BaseSchemaTest
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.execution.Execution
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

@Specification("3.1.4 Unions")
class UnionsSpecificationTest : BaseSchemaTest() {

    @Test
    fun `query union property`() {
        val map = execute(
            "{actors{name, favourite{ ... on Actor {name}, ... on Director {name age}, ... on Scenario{content(uppercase: false)}}}}",
            null
        )
        for (i in 0..4) {
            val name = map.extract<String>("data/actors[$i]/name")
            val favourite = map.extract<Map<String, String>>("data/actors[$i]/favourite")
            when (name) {
                "Brad Pitt" -> favourite shouldBe mapOf("name" to "Tom Hardy")
                "Tom Hardy" -> favourite shouldBe mapOf("age" to 43, "name" to "Christopher Nolan")
                "Morgan Freeman" -> favourite shouldBe mapOf("content" to "DUMB")
            }
        }
    }

    @Test
    fun `query union property with external fragment`() {
        val map = execute(
            """
            {
                actors{ name, favourite{ ...actor, ...director, ...scenario }}}
                
                fragment actor on Actor {name}
                fragment director on Director {name age}
                fragment scenario on Scenario{content(uppercase: false)
            }
        """.trimIndent()
        )
        for (i in 0..4) {
            val name = map.extract<String>("data/actors[$i]/name")
            val favourite = map.extract<Map<String, String>>("data/actors[$i]/favourite")
            when (name) {
                "Brad Pitt" -> favourite shouldBe mapOf("name" to "Tom Hardy")
                "Tom Hardy" -> favourite shouldBe mapOf("age" to 43, "name" to "Christopher Nolan")
                "Morgan Freeman" -> favourite shouldBe mapOf("content" to "DUMB")
            }
        }
    }

    @Test
    fun `query union property with invalid selection set`() {
        expect<ValidationException>("Invalid selection set with properties: [name] on union type property favourite : [Actor, Scenario, Director]") {
            testedSchema.executeBlocking("{actors{name, favourite{ name }}}")
        }
    }

    @Test
    fun `a union type should allow requesting __typename`() {
        val result = execute(
            """{
                actors {
                    name
                    favourite {
                        ... on Actor { name }
                        ... on Director { name, age }
                        ... on Scenario { content(uppercase: false) }
                        __typename
                    }
                }
            }""".trimIndent()
        )
        result.extract<String>("data/actors[0]/name/") shouldBe "Brad Pitt"
        result.extract<String>("data/actors[0]/favourite/__typename") shouldBe "Actor"
        result.extract<String>("data/actors[1]/name/") shouldBe "Morgan Freeman"
        result.extract<String>("data/actors[1]/favourite/__typename") shouldBe "Scenario"
        result.extract<String>("data/actors[2]/name/") shouldBe "Kevin Spacey"
        result.extract<String>("data/actors[2]/favourite/__typename") shouldBe "Actor"
        result.extract<String>("data/actors[3]/name/") shouldBe "Tom Hardy"
        result.extract<String>("data/actors[3]/favourite/__typename") shouldBe "Director"
        result.extract<String>("data/actors[4]/name/") shouldBe "Christian Bale"
        result.extract<String>("data/actors[4]/favourite/__typename") shouldBe "Actor"
    }

    @Test
    fun `a union type should allow requesting __typename only`() {
        val result = execute(
            """{
                actors {
                    name
                    favourite {
                        __typename
                    }
                }
            }""".trimIndent()
        )
        result.extract<String>("data/actors[0]/favourite/__typename") shouldBe "Actor"
        result.extract<String>("data/actors[1]/favourite/__typename") shouldBe "Scenario"
        result.extract<String>("data/actors[2]/favourite/__typename") shouldBe "Actor"
        result.extract<String>("data/actors[3]/favourite/__typename") shouldBe "Director"
        result.extract<String>("data/actors[4]/favourite/__typename") shouldBe "Actor"
    }

    @Test
    fun `a union type should require a selection for all potential types`() {
        expect<ValidationException>("Missing selection set for type 'Scenario'") {
            testedSchema.executeBlocking(
                """{
                actors {
                    name
                    favourite {
                        ... on Actor { __typename }
                        ... on Director { name, age }
                    }
                }
            }""".trimIndent()
            )
        }
    }

    @Test
    fun `Nullable union types should be valid`() {
        val result = execute(
            """{
              actors(all: true) {
                name
                nullableFavourite {
                  ... on Actor { name }
                  ... on Director { name, age }
                  ... on Scenario { content(uppercase: false) }
                }
              }
            }""".trimIndent()
        )

        result.extract<String>("data/actors[5]/name") shouldBe rickyGervais.name
        result.extract<Any?>("data/actors[5]/nullableFavourite") shouldBe null
        result.extract<String>("data/actors[3]/name") shouldBe tomHardy.name
        result.extract<String>("data/actors[3]/nullableFavourite/name") shouldBe christopherNolan.name
    }

    @Test
    fun `non-nullable union types should fail`() {
        expect<ExecutionException>("Unexpected type of union property value, expected one of [Actor, Scenario, Director] but was 'null'") {
            testedSchema.executeBlocking(
                """{
                    actors(all: true) {
                        name
                        favourite {
                            ... on Actor { name }
                            ... on Director { name, age }
                            ... on Scenario { content(uppercase: false) }
                        }
                    }
                }""".trimIndent()
            )
        }
    }

    @Test
    fun `the member types of a union type must all be object base types`() {
        expect<SchemaException>("The member types of a union type must all be object base types; scalar, interface and union types may not be member types of a union") {
            KGraphQL.schema {
                unionType("invalid") {
                    type<String>()
                }
            }
        }
    }

    @Test
    fun `a union type must define one or more unique member types`() {
        expect<SchemaException>("The union type 'invalid' has no possible types defined, requires at least one. Please refer to https://stuebingerb.github.io/KGraphQL/Reference/Type%20System/unions/") {
            KGraphQL.schema {
                unionType("invalid") {}
            }
        }
    }

    /**
     * Kotlin is non-nullable by default (T!), so test covers only case for collections
     */
    @Test
    fun `List may not be member type of a union`() {
        expect<SchemaException>("Collection may not be member type of a union 'Invalid'") {
            KGraphQL.schema {
                unionType("Invalid") {
                    type<Collection<*>>()
                }
            }
        }

        expect<SchemaException>("Map may not be member type of a union 'Invalid'") {
            KGraphQL.schema {
                unionType("Invalid") {
                    type<Map<String, String>>()
                }
            }
        }
    }

    @Test
    fun `function type may not be member types of a union`() {
        expect<SchemaException>("Unable to handle union type 'invalid': Cannot handle function class 'kotlin.Function' as object type") {
            KGraphQL.schema {
                unionType("invalid") {
                    type<Function<*>>()
                }
            }
        }
    }

    @Suppress("unused")
    sealed class AAA {
        data class BBB(val i: Int) : AAA()
        class CCC(val s: String) : AAA()
    }

    @Test
    fun `automatic unions out of sealed classes`() {
        defaultSchema {
            unionType<AAA>()

            query("returnUnion") {
                resolver { isB: Boolean ->
                    if (isB) {
                        AAA.BBB(1)
                    } else {
                        AAA.CCC("String")
                    }
                }
            }
        }.executeBlocking(
            """
            {
                f: returnUnion(isB: false) {
                    ... on BBB { i }
                    ... on CCC { s }
                }
                t: returnUnion(isB: true) {
                    ... on BBB { i }
                    ... on CCC { s }                
                }
            }
            """.trimIndent()
        ).deserialize().run {
            extract<String>("data/f/s") shouldBe "String"
            extract<Int>("data/t/i") shouldBe 1
        }
    }

    @Suppress("unused")
    sealed class WithFields {
        data class Value1(val i: Int, val fields: List<String>) : WithFields()
        data class Value2(val s: String, val fields: List<String>) : WithFields()
    }

    @Test
    fun `union types in lists`() {
        defaultSchema {
            unionType<WithFields>()

            query("returnUnion") {
                resolver { node: Execution.Node ->
                    WithFields.Value1(1, node.getFields())
                }
            }
        }.executeBlocking(
            """
            {
                returnUnion {
                    ... on Value1 { i, fields }
                    ... on Value2 { s, fields }
                }
            }
            """.trimIndent()
        ).deserialize().run {
            extract<Int>("data/returnUnion/i") shouldBe 1
            shouldThrowExactly<IllegalArgumentException> { extract("data/returnUnion/s") }
            extract<List<String>>("data/returnUnion/fields") shouldBe listOf("i", "fields", "s")
        }
    }

    @Test
    fun `union types with custom name def resolver`() {
        defaultSchema {
            unionType<WithFields> {
                subTypeBlock = {
                    name = "Prefix$name"
                }
            }

            query("returnUnion") {
                resolver { node: Execution.Node ->
                    listOf(
                        WithFields.Value1(1, node.getFields()),
                        WithFields.Value2("key", node.getFields()),
                    )
                }
            }
        }.executeBlocking(
            """
            {
                returnUnion {
                    __typename
                }
            }
        """.trimIndent()
        ).deserialize().run {
            extract<String>("data/returnUnion[0]/__typename") shouldBe "PrefixValue1"
            extract<String>("data/returnUnion[1]/__typename") shouldBe "PrefixValue2"
        }
    }

    @Suppress("unused")
    sealed class ContactStatus {
        data class NotInvited(
            val dummy: String = "dummy" // because no empty types in gql
        ) : ContactStatus()

        data class Invited(val invitationId: String, val invitedAt: Instant) : ContactStatus()

        data class Onboarded(val onboardedAt: Instant = Instant.now(), val userId: String) : ContactStatus()
    }

    data class Carrier(
        val contactStatus: ContactStatus
    )

    // https://github.com/aPureBase/KGraphQL/issues/105
    @Test
    fun `sealed classes unions should allow requesting __typename`() {
        val schema = KGraphQL.schema {
            longScalar<Instant> {
                serialize = { it.toEpochMilli() }
                deserialize = { Instant.ofEpochMilli(it) }
            }
            unionType<ContactStatus>()
            query("contactStatus") {
                resolver { -> ContactStatus.Onboarded(userId = "someUserId") }
            }
        }
        val results = schema.executeBlocking(
            """
            {
                contactStatus {
                    ... on NotInvited {
                        dummy
                    }
                    ... on Invited {
                        invitedAt
                        invitationId
                    }
                    ... on Onboarded {
                        onboardedAt
                        userId
                    }
                    __typename
                }
            }
            """.trimIndent()
        ).deserialize()

        results.extract<String>("data/contactStatus/userId") shouldBe "someUserId"
        results.extract<String>("data/contactStatus/__typename") shouldBe "Onboarded"
    }

    // https://github.com/aPureBase/KGraphQL/issues/105
    @Test
    fun `inner sealed classes unions should allow requesting __typename`() {
        val schema = KGraphQL.schema {
            longScalar<Instant> {
                serialize = { it.toEpochMilli() }
                deserialize = { Instant.ofEpochMilli(it) }
            }
            unionType<ContactStatus>()
            query("carrier") {
                resolver { -> Carrier(ContactStatus.Onboarded(userId = "someUserId")) }
            }
        }

        val results = schema.executeBlocking(
            """
            {
                carrier {
                    contactStatus {
                        ... on NotInvited {
                            dummy
                        }
                        ... on Invited {
                            invitedAt
                            invitationId
                        }
                        ... on Onboarded {
                            onboardedAt
                            userId
                        }
                        __typename
                    }
                }
            }
            """.trimIndent()
        ).deserialize()

        results.extract<String>("data/carrier/contactStatus/userId") shouldBe "someUserId"
        results.extract<String>("data/carrier/contactStatus/__typename") shouldBe "Onboarded"
    }

    data class Wrapper(
        val items: List<QualificationItem>
    )

    sealed class QualificationItem {
        data class Qual1(val id: String) : QualificationItem()
    }

    // https://github.com/aPureBase/KGraphQL/issues/109
    @Test
    fun `list of union type should work as expected`() {
        val schema = KGraphQL.schema {
            unionType<QualificationItem>()

            query("foo") {
                resolver { ->
                    Wrapper(listOf(QualificationItem.Qual1("12345")))
                }
            }
        }

        val result = schema.executeBlocking(
            """
                query IntrospectionQuery {
                    __schema {
                        types {
                            name
                            fields(includeDeprecated: true) {
                                name
                                type {
                                    name
                                }
                            }
                            possibleTypes {
                                name
                            }
                        }
                    }
                }
            """
        ).deserialize()

        val types = result.extract<List<Map<String, Any>>>("data/__schema/types")
        types.first { it["name"] == "QualificationItem" }.let {
            it["fields"] shouldBe null
            it["possibleTypes"] shouldBe listOf(mapOf("name" to "Qual1"))
        }
        types.first { it["name"] == "Qual1" }.let {
            it["fields"] shouldBe listOf(mapOf("name" to "id", "type" to mapOf("name" to null)))
            it["possibleTypes"] shouldBe null
        }
    }
}
