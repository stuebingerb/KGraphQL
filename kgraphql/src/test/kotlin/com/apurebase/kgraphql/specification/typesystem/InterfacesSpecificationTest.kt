package com.apurebase.kgraphql.specification.typesystem

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.Specification
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.expect
import com.apurebase.kgraphql.extract
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@Specification("3.1.3 Interfaces")
class InterfacesSpecificationTest {
    interface SimpleInterface {
        val exe: String
    }

    data class Simple(override val exe: String, val stuff: String) : SimpleInterface

    val schema = KGraphQL.schema {
        type<Simple>()
        query("simple") { resolver { -> Simple("EXE", "CMD") as SimpleInterface } }
    }

    @Test
    suspend fun `Interfaces represent a list of named fields and their arguments`() {
        val map = deserialize(schema.execute("{simple{exe}}"))
        map.extract<String>("data/simple/exe") shouldBe "EXE"
    }

    @Test
    suspend fun `When querying for fields on an interface type, only those fields declared on the interface may be queried`() {
        expect<ValidationException>("Property stuff on SimpleInterface does not exist") {
            schema.execute("{simple{exe, stuff}}")
        }
    }

    @Test
    suspend fun `Query for fields of interface implementation can be done only by fragments`() {
        val map = deserialize(schema.execute("{simple{exe ... on Simple { stuff }}}"))
        map.extract<String>("data/simple/stuff") shouldBe "CMD"
    }
}
