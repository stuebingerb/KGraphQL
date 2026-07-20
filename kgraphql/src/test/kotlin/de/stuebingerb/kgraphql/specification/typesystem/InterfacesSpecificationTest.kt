package de.stuebingerb.kgraphql.specification.typesystem

import de.stuebingerb.kgraphql.KGraphQL
import de.stuebingerb.kgraphql.Specification
import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.deserialize
import de.stuebingerb.kgraphql.expectRequestError
import de.stuebingerb.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@Specification("3.1.3 Interfaces")
class InterfacesSpecificationTest {
    interface SimpleInterface {
        val exe: String
    }

    data class Simple(override val exe: String, val stuff: String) : SimpleInterface
    data class Simple2(override val exe: String, val other: String) : SimpleInterface

    val schema = KGraphQL.schema {
        type<Simple>()
        type<Simple2>()
        query("simple") { resolver { -> Simple("EXE", "CMD") as SimpleInterface } }
        query("simpleList") { resolver { -> listOf(Simple("EXE", "CMD"), Simple2("EXE", "other")) } }
    }

    @Test
    fun `Interfaces represent a list of named fields and their arguments`() {
        val map = deserialize(schema.executeBlocking("{simple{exe}}"))
        map.extract<String>("data/simple/exe") shouldBe "EXE"
    }

    @Test
    fun `When querying for fields on an interface type, only those fields declared on the interface may be queried`() {
        expectRequestError<ValidationException>("Property 'stuff' on 'SimpleInterface' does not exist") {
            schema.executeBlocking("{simple{exe, stuff}}")
        }
    }

    @Test
    fun `Query for fields of interface implementation can be done only by fragments`() {
        val map = deserialize(schema.executeBlocking("{simple{exe ... on Simple { stuff }}}"))
        map.extract<String>("data/simple/stuff") shouldBe "CMD"
    }

    @Test
    fun `fragments on interfaces should work`() {
        schema.executeBlocking(
            """
                {
                    simpleList {
                        __typename
                        exe
                        ...simpleFragment
                    }
                }
                
                fragment simpleFragment on SimpleInterface {
                    ... on Simple { stuff }
                    ... on Simple2 { other }
                }
            """.trimIndent()
        ) shouldBe """
            {"data":{"simpleList":[{"__typename":"Simple","exe":"EXE","stuff":"CMD"},{"__typename":"Simple2","exe":"EXE","other":"other"}]}}
        """.trimIndent()
    }
}
