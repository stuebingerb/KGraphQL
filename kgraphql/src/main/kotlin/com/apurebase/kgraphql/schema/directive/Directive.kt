package com.apurebase.kgraphql.schema.directive

import com.apurebase.kgraphql.schema.directive.DirectiveLocation.ARGUMENT_DEFINITION
import com.apurebase.kgraphql.schema.directive.DirectiveLocation.ENUM_VALUE
import com.apurebase.kgraphql.schema.directive.DirectiveLocation.FIELD
import com.apurebase.kgraphql.schema.directive.DirectiveLocation.FIELD_DEFINITION
import com.apurebase.kgraphql.schema.directive.DirectiveLocation.FRAGMENT_SPREAD
import com.apurebase.kgraphql.schema.directive.DirectiveLocation.INLINE_FRAGMENT
import com.apurebase.kgraphql.schema.directive.DirectiveLocation.INPUT_FIELD_DEFINITION
import com.apurebase.kgraphql.schema.directive.DirectiveLocation.SCALAR
import com.apurebase.kgraphql.schema.introspection.__Directive
import com.apurebase.kgraphql.schema.introspection.__InputValue
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.structure.InputValue

/**
 * Directives provide a way to describe alternate runtime execution and type validation behavior in a GraphQL document.
 */
data class Directive(
    override val name: String,
    override val locations: List<DirectiveLocation>,
    val execution: DirectiveExecution,
    override val description: String?,
    val arguments: List<InputValue<*>>,
    override val isRepeatable: Boolean
) : __Directive {

    override val args: List<__InputValue>
        get() = arguments

    data class Partial(
        val name: String,
        val locations: List<DirectiveLocation>,
        val execution: DirectiveExecution,
        val description: String? = null,
        val isRepeatable: Boolean = false
    ) {
        fun toDirective(inputValues: List<InputValue<*>>) = Directive(
            name = this.name,
            locations = this.locations,
            execution = this.execution,
            description = this.description,
            arguments = inputValues,
            isRepeatable = isRepeatable
        )
    }

    companion object {
        /**
         * https://spec.graphql.org/draft/#sec--skip
         *
         * The `@skip` built-in directive may be provided for fields, fragment spreads, and inline fragments,
         * and allows for conditional exclusion during execution as described by the `if` argument.
         */
        val SKIP = Partial(
            "skip",
            listOf(FIELD, FRAGMENT_SPREAD, INLINE_FRAGMENT),
            DirectiveExecution(FunctionWrapper.on { `if`: Boolean -> DirectiveResult(!`if`) })
        )

        /**
         * https://spec.graphql.org/draft/#sec--include
         *
         * The `@include` built-in directive may be provided for fields, fragment spreads, and inline fragments,
         * and allows for conditional inclusion during execution as described by the `if` argument.
         */
        val INCLUDE = Partial(
            "include",
            listOf(FIELD, FRAGMENT_SPREAD, INLINE_FRAGMENT),
            DirectiveExecution(FunctionWrapper.on { `if`: Boolean -> DirectiveResult(`if`) })
        )

        /**
         * https://spec.graphql.org/draft/#sec--deprecated
         *
         * The `@deprecated` built-in directive is used within the type system definition language to indicate
         * deprecated portions of a GraphQL service's schema, such as deprecated fields on a type, arguments on
         * a field, input fields on an input type, or values of an enum type.
         */
        val DEPRECATED = Partial(
            "deprecated",
            listOf(FIELD_DEFINITION, ARGUMENT_DEFINITION, INPUT_FIELD_DEFINITION, ENUM_VALUE),
            // DirectiveExecution is a no-op, since it cannot be used during execution.
            DirectiveExecution(FunctionWrapper.on { reason: String? -> DirectiveResult(true) })
        )

        /**
         * https://spec.graphql.org/September2025/#sec--specifiedBy
         *
         * The `@specifiedBy` built-in directive is used within the type system definition language to provide a
         * scalar specification URL for specifying the behavior of custom scalar types. The URL should point to a
         * human-readable specification of the data format, serialization, and coercion rules.
         * It must not appear on built-in scalar types.
         */
        val SPECIFIED_BY = Partial(
            "specifiedBy",
            listOf(SCALAR),
            // DirectiveExecution is a no-op, since it cannot be used during execution.
            DirectiveExecution(FunctionWrapper.on { url: String -> DirectiveResult(true) })
        )
    }
}
