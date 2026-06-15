package de.stuebingerb.kgraphql.schema.directive

import de.stuebingerb.kgraphql.schema.model.FunctionWrapper

class DirectiveExecution(val function: FunctionWrapper<DirectiveResult>) : FunctionWrapper<DirectiveResult> by function
