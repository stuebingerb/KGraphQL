package com.apurebase.kgraphql.schema.structure

import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.model.ast.ArgumentNode
import com.apurebase.kgraphql.schema.model.ast.SelectionNode.FieldNode
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

private val namePattern = Regex("[_a-zA-Z][_a-zA-Z0-9]*")

fun validatePropertyArguments(parentType: Type, field: Field, requestNode: FieldNode) {
    val argumentValidationExceptions = field.validateArguments(requestNode.arguments, parentType.name)

    if (argumentValidationExceptions.isNotEmpty()) {
        throw ValidationException(argumentValidationExceptions.fold("") { sum, exc ->
            "$sum${exc.message}"
        }, nodes = argumentValidationExceptions.flatMap { it.nodes ?: listOf() })
    }
}

fun Field.validateArguments(selectionArgs: List<ArgumentNode>?, parentTypeName: String?): List<ValidationException> {
    if (!(args.isNotEmpty() || selectionArgs?.isNotEmpty() != true)) {
        return listOf(
            ValidationException(
                message = "Property '$name' on type '$parentTypeName' has no arguments, found: ${selectionArgs.map { it.name.value }}",
                nodes = selectionArgs
            )
        )
    }

    val exceptions = mutableListOf<ValidationException>()

    val parameterNames = args.map { it.name }
    val invalidArguments = selectionArgs?.filter { it.name.value !in parameterNames }

    if (!invalidArguments.isNullOrEmpty()) {
        exceptions.add(
            ValidationException(
                message = "'$name' does support arguments: $parameterNames, found: ${selectionArgs.map { it.name.value }}"
            )
        )
    }

    args.forEach { arg ->
        val value = selectionArgs?.firstOrNull { arg.name == it.name.value }
        if (value == null && arg.type.kind == TypeKind.NON_NULL && arg.defaultValue == null) {
            exceptions.add(
                ValidationException(
                    message = "Missing value for non-nullable argument ${arg.name} on the field '$name'"
                )
            )
        } // else is valid
    }

    return exceptions
}

/**
 * validate that only typed fragments or __typename are present
 */
fun validateUnionRequest(field: Field.Union<*>, selectionNode: FieldNode) {
    val illegalChildren =
        selectionNode.selectionSet?.selections?.filterIsInstance<FieldNode>()?.filter { it.name.value != "__typename" }

    if (!illegalChildren.isNullOrEmpty()) {
        throw ValidationException(
            message = "Invalid selection set with properties: ${
                illegalChildren.joinToString(prefix = "[", postfix = "]") {
                    it.aliasOrName.value
                }
            } on union type property ${field.name} : ${(field.returnType.unwrapped() as Type.Union).possibleTypes.map { it.name }}",
            nodes = illegalChildren
        )
    }
}

fun validateName(name: String) {
    if (name.startsWith("__")) {
        throw SchemaException(
            "Illegal name '$name'. Names starting with '__' are reserved for introspection system"
        )
    }
    // https://spec.graphql.org/October2021/#sec-Names
    // "Names in GraphQL are limited to the Latin ASCII subset of SourceCharacter in order to support interoperation with as many other systems as possible."
    if (!name.matches(namePattern)) {
        throw SchemaException("Illegal name '$name'. Names must start with a letter or underscore, and may only contain [_a-zA-Z0-9]")
    }
}

// function before generic, because it is its subset
fun assertValidObjectType(kClass: KClass<*>) = when {
    kClass.isSubclassOf(Function::class) -> throw SchemaException("Cannot handle function class '${kClass.qualifiedName}' as object type")
    kClass.isSubclassOf(Enum::class) -> throw SchemaException("Cannot handle enum class '${kClass.qualifiedName}' as object type")
    else -> Unit
}
