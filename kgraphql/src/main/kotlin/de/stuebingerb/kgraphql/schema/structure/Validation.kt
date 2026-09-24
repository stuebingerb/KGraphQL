package de.stuebingerb.kgraphql.schema.structure

import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.schema.SchemaException
import de.stuebingerb.kgraphql.schema.introspection.TypeKind
import de.stuebingerb.kgraphql.schema.introspection.__Type
import de.stuebingerb.kgraphql.schema.model.ast.SelectionNode.FieldNode
import de.stuebingerb.kgraphql.schema.model.ast.ValueNode
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

private val namePattern = Regex("[_a-zA-Z][_a-zA-Z0-9]*")

internal fun Field.validateArguments(requestNode: FieldNode, parentTypeName: String?) {
    val selectionArgs = requestNode.arguments
    if (!(args.isNotEmpty() || selectionArgs?.isNotEmpty() != true)) {
        throw ValidationException(
            message = "Property '$name' on type '$parentTypeName' has no arguments, found: ${selectionArgs.map { it.name.value }}",
            node = requestNode
        )
    }

    val parameterNames = args.mapTo(HashSet()) { it.name }
    val invalidArguments = selectionArgs?.filter { it.name.value !in parameterNames }

    if (!invalidArguments.isNullOrEmpty()) {
        throw ValidationException(
            message = "'$name' does support arguments: $parameterNames, found: ${selectionArgs.map { it.name.value }}",
            node = requestNode
        )
    }

    args.forEach { arg ->
        val value = selectionArgs?.firstOrNull { arg.name == it.name.value }
        if (value == null && arg.type.kind == TypeKind.NON_NULL && arg.defaultValue == null) {
            throw ValidationException(
                message = "Missing value for non-nullable argument '${arg.name}' on the field '$name'",
                node = requestNode
            )
        }
        value?.value?.validate(arg.type.unwrapped())
    }
}

private fun ValueNode.validate(type: __Type) {
    // TODO: start from type and validate value against expected type (needs most likely previous variable resolution)
    when (this) {
        is ValueNode.ObjectValueNode -> validateInputObject(type)
        is ValueNode.ListValueNode -> values.forEach { it.validate(type) }
        else -> Unit // TODO: extend validation for other values
    }
}

private fun ValueNode.ObjectValueNode.validateInputObject(type: __Type) {
    // Validate oneOf input object rules:
    //  - must have exactly one field set, and
    //  - the value for that field must be non-null
    // cf. https://spec.graphql.org/September2025/#sec-OneOf-Input-Objects.Input-Coercion
    if (type.isOneOf == true) {
        if (fields.size != 1) {
            throw ValidationException(
                message = "OneOf input object '${type.name}' must have exactly one field set, but ${fields.size} were provided",
                node = this
            )
        } else {
            val singleField = fields.first()
            if (singleField.value is ValueNode.NullValueNode) {
                throw ValidationException(
                    message = "Value for member field '${singleField.name.value}' must be non-null",
                    node = this
                )
            }
        }
    }

    fields.forEach { field ->
        val fieldType = type.inputFields.orEmpty().firstOrNull { it.name == field.name.value }?.type
            ?: throw ValidationException(
                message = "Property '${field.name.value}' on '${type.name}' does not exist",
                node = this
            )
        field.value.validate(fieldType.unwrapped())
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

internal fun assertValidObjectType(kClass: KClass<*>) = when {
    kClass.isSubclassOf(Function::class) -> throw SchemaException("Cannot handle function class '${kClass.qualifiedName}' as object type")
    kClass.isSubclassOf(Enum::class) -> throw SchemaException("Cannot handle enum class '${kClass.qualifiedName}' as object type")
    else -> Unit
}
