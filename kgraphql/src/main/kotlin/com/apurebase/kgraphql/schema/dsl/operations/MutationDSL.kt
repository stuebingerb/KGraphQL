package com.apurebase.kgraphql.schema.dsl.operations

import com.apurebase.kgraphql.schema.model.MutationDef

class MutationDSL(
    name: String
) : AbstractOperationDSL(name) {

    internal fun toKQLMutation(): MutationDef<out Any?> {
        val function = requireNotNull(functionWrapper) {
            "resolver has to be specified for mutation [$name]"
        }

        return MutationDef(
            name = name,
            resolver = function,
            description = description,
            isDeprecated = isDeprecated,
            deprecationReason = deprecationReason,
            inputValues = inputValues,
            accessRule = accessRuleBlock,
            explicitReturnType = explicitReturnType
        )
    }
}
