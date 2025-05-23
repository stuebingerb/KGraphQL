package com.apurebase.kgraphql.schema.structure

import com.apurebase.kgraphql.schema.introspection.__InputValue
import com.apurebase.kgraphql.schema.model.InputValueDef

class InputValue<T : Any>(
    valueDef: InputValueDef<T>,
    override val type: Type,
    //TODO: Replace with GraphQL compliant representation
    override val defaultValue: String? = valueDef.defaultValue?.toString()
) : __InputValue {

    override val name: String = valueDef.name

    override val description: String? = valueDef.description

    override val isDeprecated: Boolean = valueDef.isDeprecated

    override val deprecationReason: String? = valueDef.deprecationReason

    val default: T? = valueDef.defaultValue

    val parameterName: String = valueDef.parameterName
}
