package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.*
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.dsl.subscribe
import com.apurebase.kgraphql.schema.dsl.unsubscribe
import org.junit.Assert
import org.junit.Test

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
        executeEqualQueries( schema,
                mapOf("data" to mapOf("fizz" to "buzz")),
                "{fizz}",
                "query {fizz}",
                "query BUZZ {fizz}"
        )
    }

    @Test
    fun `unnamed and named mutations are equivalent`(){
        executeEqualQueries( schema,
                mapOf("data" to mapOf("createActor" to mapOf("name" to "Kurt Russel"))),
                "{createActor(name : \"Kurt Russel\"){name}}",
                "mutation {createActor(name : \"Kurt Russel\"){name}}",
                "mutation KURT {createActor(name : \"Kurt Russel\"){name}}"
        )
    }

    @Test
    fun `handle subscription`(){
        schema.execute("subscription {subscriptionActor(subscription : \"mySubscription\"){name}}")

        subscriptionResult = ""
        schema.execute("{createActor(name : \"Kurt Russel\"){name}}")
        Assert.assertEquals(subscriptionResult, "{\"data\":{\"name\":\"Kurt Russel\"}}")

        subscriptionResult = ""
        schema.execute("{createActor(name : \"Kurt Russel1\"){name}}")
        Assert.assertEquals(subscriptionResult, "{\"data\":{\"name\":\"Kurt Russel1\"}}")

        subscriptionResult = ""
        schema.execute("{createActor(name : \"Kurt Russel2\"){name}}")
        Assert.assertEquals(subscriptionResult, "{\"data\":{\"name\":\"Kurt Russel2\"}}")

        schema.execute("subscription {unsubscriptionActor(subscription : \"mySubscription\"){name}}")

        subscriptionResult = ""
        schema.execute("{createActor(name : \"Kurt Russel\"){name}}")
        Assert.assertEquals(subscriptionResult, "")

    }

    @Test
    fun `Subscription return type must be the same as the publisher's`(){
        expect<SchemaException>("Subscription return type must be the same as the publisher's"){
            schema.execute("subscription {subscriptionActress(subscription : \"mySubscription\"){age}}")
        }
    }
}