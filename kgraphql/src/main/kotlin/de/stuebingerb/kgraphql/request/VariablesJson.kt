package de.stuebingerb.kgraphql.request

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import de.stuebingerb.kgraphql.helpers.toValueNode
import de.stuebingerb.kgraphql.schema.model.ast.NameNode
import de.stuebingerb.kgraphql.schema.model.ast.ValueNode
import de.stuebingerb.kgraphql.schema.structure.Type

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
