package de.stuebingerb.kgraphql.schema.introspection

import de.stuebingerb.kgraphql.schema.directive.DirectiveLocation

interface __Directive : Describable, Named {

    val locations: List<DirectiveLocation>

    val args: List<__InputValue>

    val isRepeatable: Boolean
}
