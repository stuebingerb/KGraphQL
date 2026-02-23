package com.apurebase.kgraphql.schema.introspection

import com.apurebase.kgraphql.schema.directive.DirectiveLocation

interface __Directive : Describable, Named {

    val locations: List<DirectiveLocation>

    val args: List<__InputValue>

    val isRepeatable: Boolean
}
