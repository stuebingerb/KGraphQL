package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.helpers.toValueNode
import com.apurebase.kgraphql.schema.model.ast.NameNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.structure.Type
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode

/**
 * Represents already parsed variables json
 */
interface VariablesJson {

    fun get(type: Type, key: NameNode): ValueNode?

    fun getRaw(): JsonNode?

    class Empty : VariablesJson {
        override fun get(type: Type, key: NameNode): ValueNode? = null

        override fun getRaw(): JsonNode? = null
    }

    class Defined(val json: JsonNode) : VariablesJson {
        override fun get(type: Type, key: NameNode): ValueNode? {
            return json.let { node -> node[key.value] }?.toValueNode(type)
        }

        /**
         * Returns the raw [json] unless it is a [NullNode]
         */
        override fun getRaw(): JsonNode? = json.takeUnless { it is NullNode }
    }
}
