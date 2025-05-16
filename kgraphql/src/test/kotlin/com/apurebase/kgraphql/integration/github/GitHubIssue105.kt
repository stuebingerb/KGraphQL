package com.apurebase.kgraphql.integration.github

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class GitHubIssue105 {

    sealed class ContactStatus {
        data class NotInvited(
            val dummy: String = "dummy" // because no empty types in gql
        ) : ContactStatus()

        data class Invited(
            val invitationId: String,
            val invitedAt: Instant
        ) : ContactStatus()

        data class Onboarded(
            val onboardedAt: Instant = Instant.now(),
            val userId: String
        ) : ContactStatus()
    }

    data class Carrier(
        val contactStatus: ContactStatus
    )

    @Test
    fun `sealed classes unions should allow requesting __typename`() {
        val schema = KGraphQL.schema {
            longScalar<Instant> {
                serialize = { it.toEpochMilli() }
                deserialize = { Instant.ofEpochMilli(it) }
            }
            unionType<ContactStatus>()
            query("contactStatus") {
                resolver { -> ContactStatus.Onboarded(userId = "Leopard2A5") }
            }
        }
        val results = schema.executeBlocking(
            """{
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
        }""".trimIndent()
        ).deserialize()

        results.extract<String>("data/contactStatus/userId") shouldBe "Leopard2A5"
        results.extract<String>("data/contactStatus/__typename") shouldBe "Onboarded"
    }

    @Test
    fun `inner sealed classes unions should allow requesting __typename`() {
        val schema = KGraphQL.schema {
            longScalar<Instant> {
                serialize = { it.toEpochMilli() }
                deserialize = { Instant.ofEpochMilli(it) }
            }
            unionType<ContactStatus>()
            query("carrier") {
                resolver { -> Carrier(ContactStatus.Onboarded(userId = "Leopard2A5")) }
            }
        }

        val results = schema.executeBlocking(
            """{
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
        }""".trimIndent()
        ).deserialize()

        results.extract<String>("data/carrier/contactStatus/userId") shouldBe "Leopard2A5"
        results.extract<String>("data/carrier/contactStatus/__typename") shouldBe "Onboarded"
    }
}
