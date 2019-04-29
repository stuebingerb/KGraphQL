package com.apurebase.kgraphql.schema.directive

import com.apurebase.kgraphql.schema.model.FunctionWrapper


class DirectiveExecution(val function: FunctionWrapper<DirectiveResult>) : FunctionWrapper<DirectiveResult> by function