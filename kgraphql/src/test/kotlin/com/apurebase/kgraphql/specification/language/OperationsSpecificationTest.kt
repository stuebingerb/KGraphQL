package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.executeEqualQueries
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.dsl.operations.subscribe
import com.apurebase.kgraphql.schema.dsl.operations.unsubscribe
import org.amshove.kluent.shouldEqual
import org.junit.jupiter.api.Test

data class Actor(var name : String? = "", var age: Int? = 0)
data class Actress(var name : String? = "", var age: Int? = 0)

@Specification("2.3 Operations")
class OperationsSpecificationTest {

    var subscriptionResult = ""

    val schema = defaultSchema {

        query("fizz") {
            resolver{ -> "buzz"}.withArgs {  }
        }

        val publisher = mutation("createActor") {
            resolver { name : String -> Actor(name, 11) }
        }

        subscription("subscriptionActor") {
            resolver { subscription: String ->
                subscribe(subscription, publisher, Actor()) {
                    subscriptionResult = it
                    println(it)
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
    fun `unnamed and named queries are equivalent`(){
        executeEqualQueries(schema,
                mapOf("data" to mapOf("fizz" to "buzz")),
                "{fizz}",
                "query {fizz}",
                "query BUZZ {fizz}"
        )
    }

    @Test
    fun `unnamed and named mutations are equivalent`(){
        executeEqualQueries(schema,
                mapOf("data" to mapOf("createActor" to mapOf("name" to "Kurt Russel"))),
                "mutation {createActor(name : \"Kurt Russel\"){name}}",
                "mutation KURT {createActor(name : \"Kurt Russel\"){name}}"
        )
    }

    @Test
    fun `handle subscription`(){
        schema.executeBlocking("subscription {subscriptionActor(subscription : \"mySubscription\"){name}}")

        subscriptionResult = ""
        schema.executeBlocking("mutation {createActor(name : \"Kurt Russel\"){name}}")

        subscriptionResult shouldEqual "{\"data\":{\"name\":\"Kurt Russel\"}}"

        subscriptionResult = ""
        schema.executeBlocking("mutation{createActor(name : \"Kurt Russel1\"){name}}")
        subscriptionResult shouldEqual "{\"data\":{\"name\":\"Kurt Russel1\"}}"

        subscriptionResult = ""
        schema.executeBlocking("mutation{createActor(name : \"Kurt Russel2\"){name}}")
        subscriptionResult shouldEqual "{\"data\":{\"name\":\"Kurt Russel2\"}}"

        schema.executeBlocking("subscription {unsubscriptionActor(subscription : \"mySubscription\"){name}}")

        subscriptionResult = ""
        schema.executeBlocking("mutation{createActor(name : \"Kurt Russel\"){name}}")
        subscriptionResult shouldEqual ""

    }

    @Test
    fun `Subscription return type must be the same as the publisher's`(){
        expect<SchemaException>("Subscription return type must be the same as the publisher's"){
            schema.executeBlocking("subscription {subscriptionActress(subscription : \"mySubscription\"){age}}")
        }
    }
}

