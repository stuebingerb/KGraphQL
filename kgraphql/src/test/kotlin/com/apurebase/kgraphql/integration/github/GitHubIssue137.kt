package com.apurebase.kgraphql.integration.github

import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.deserialize
import com.apurebase.kgraphql.extract
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class GitHubIssue137 {

    data class InputType(val id: Int, val id2: Int, val value: String)
    data class Criteria(val inputs: List<InputType>)

    @Test
    fun `testing abc`() {
        KGraphQL.schema {
            configure { wrapErrors = false }
            inputType<InputType>()
            query("search") {
                resolver { criteria: Criteria ->
                    criteria.inputs.joinToString { "${it.id}_${it.id2}: ${it.value}" }
                }
            }
        }.executeBlocking(
            """
                query Query(${'$'}searches: [InputType!]!) {
                    search(criteria: { inputs: ${'$'}searches })
                }
            """,
            """
                {
                    "searches": [
                        {"id":1, "id2": 2, "value": "Search"}
                    ]
                }
            """
        ).deserialize().extract<String>("data/search") shouldBeEqualTo "1_2: Search"
    }
}
