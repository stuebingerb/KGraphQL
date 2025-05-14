package com.apurebase.kgraphql.specification.language

import com.apurebase.kgraphql.Actor
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.defaultSchema
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@Specification("2.7 Field Alias")
class FieldAliasSpecificationTest {

    val age = 232

    val actorName = "Boguś Linda"

    val schema = defaultSchema {
        query("actor") {
            resolver { -> Actor(actorName, age) }
        }

        type<Actor> {
            transformation(Actor::age) { age: Int, inMonths: Boolean? ->
                if (inMonths == true) age * 12 else age
            }
        }
    }

    @Test
    fun `can define response object field name`() {
        val map =
            deserialize(schema.executeBlocking("{actor{ageMonths: age(inMonths : true) ageYears: age(inMonths : false)}}"))
        map.extract<Int>("data/actor/ageMonths") shouldBe age * 12
        map.extract<Int>("data/actor/ageYears") shouldBe age
    }

    @Test
    fun `top level of a query can be given alias`() {
        val map = deserialize(schema.executeBlocking("{ bogus : actor{name}}"))
        map.extract<String>("data/bogus/name") shouldBe actorName
    }
}
