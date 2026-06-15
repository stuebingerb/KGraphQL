package de.stuebingerb.kgraphql.specification.language

import de.stuebingerb.kgraphql.ExecutionException
import de.stuebingerb.kgraphql.Specification
import de.stuebingerb.kgraphql.defaultSchema
import de.stuebingerb.kgraphql.executeEqualQueries
import de.stuebingerb.kgraphql.expectExecutionError
import de.stuebingerb.kgraphql.schema.dsl.operations.subscribe
import de.stuebingerb.kgraphql.schema.dsl.operations.unsubscribe
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

data class Actor(var name: String? = "", var age: Int? = 0)
data class Actress(var name: String? = "", var age: Int? = 0)

@Specification("2.3 Operations")
class OperationsSpecificationTest {

    private var subscriptionResult = ""

    private fun newSchema() = defaultSchema {

        query("fizz") {
            resolver { -> "buzz" }.withArgs { }
        }

        val publisher = mutation("createActor") {
            resolver { name: String -> Actor(name, 11) }
        }

        subscription("subscriptionActor") {
            resolver { subscription: String ->
                subscribe(subscription, publisher, Actor()) {
                    subscriptionResult = it
                }
            }
        }

        subscription("unsubscriptionActor") {
            resolver { subscription: String ->
                unsubscribe(subscription, publisher, Actor())
            }
        }

        subscription("subscriptionActress") {
            resolver { subscription: String ->
                subscribe(subscription, publisher, Actress()) {
                    subscriptionResult = it
                }
            }
        }
    }

    @Test
    fun `unnamed and named queries are equivalent`() {
        executeEqualQueries(
            newSchema(),
            mapOf("data" to mapOf("fizz" to "buzz")),
            "{fizz}",
            "query {fizz}",
            "query BUZZ {fizz}"
        )
    }

    @Test
    fun `unnamed and named mutations are equivalent`() {
        executeEqualQueries(
            newSchema(),
            mapOf("data" to mapOf("createActor" to mapOf("name" to "Kurt Russel"))),
            "mutation {createActor(name : \"Kurt Russel\"){name}}",
            "mutation KURT {createActor(name : \"Kurt Russel\"){name}}"
        )
    }

    @Test
    fun `handle subscription`() {
        val schema = newSchema()
        schema.executeBlocking("subscription {subscriptionActor(subscription : \"mySubscription\"){name}}")

        subscriptionResult = ""
        schema.executeBlocking("mutation {createActor(name : \"Kurt Russel\"){name}}")
        subscriptionResult shouldBe "{\"data\":{\"name\":\"Kurt Russel\"}}"

        subscriptionResult = ""
        schema.executeBlocking("mutation{createActor(name : \"Kurt Russel1\"){name}}")
        subscriptionResult shouldBe "{\"data\":{\"name\":\"Kurt Russel1\"}}"

        subscriptionResult = ""
        schema.executeBlocking("mutation{createActor(name : \"Kurt Russel2\"){name}}")
        subscriptionResult shouldBe "{\"data\":{\"name\":\"Kurt Russel2\"}}"

        schema.executeBlocking("subscription {unsubscriptionActor(subscription : \"mySubscription\"){name}}")

        subscriptionResult = ""
        schema.executeBlocking("mutation{createActor(name : \"Kurt Russel\"){name}}")
        subscriptionResult shouldBe ""
    }

    @Test
    fun `subscription return type must be the same as the publisher's`() {
        expectExecutionError<ExecutionException>("Subscription return type must be the same as the publisher's") {
            // TODO: should fail during schema compilation already - https://github.com/stuebingerb/KGraphQL/issues/492
            newSchema().executeBlocking("subscription {subscriptionActress(subscription : \"mySubscription\"){age}}")
        }
    }
}
