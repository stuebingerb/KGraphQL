package com.apurebase.kgraphql.schema.structure2

import com.apurebase.kgraphql.RequestException
import com.apurebase.kgraphql.ValidationException
import com.apurebase.kgraphql.request.Arguments
import com.apurebase.kgraphql.request.graph.Fragment
import com.apurebase.kgraphql.request.graph.SelectionNode
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.jol.ast.ArgumentNode
import com.apurebase.kgraphql.schema.jol.ast.NameNode
import com.apurebase.kgraphql.schema.jol.ast.SelectionNode.FieldNode
import com.apurebase.kgraphql.schema.jol.ast.SelectionNode.FragmentNode.FragmentSpreadNode
import com.apurebase.kgraphql.schema.jol.ast.SelectionNode.FragmentNode.InlineFragmentNode
import com.apurebase.kgraphql.schema.jol.ast.SelectionSetNode
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

fun validatePropertyArguments(parentType: Type, field: Field, requestNode: SelectionNode) {

    val argumentValidationExceptions = field.validateArguments(requestNode.arguments, parentType.name)

    if (argumentValidationExceptions.isNotEmpty()) {
        throw ValidationException(argumentValidationExceptions.fold("") { sum, exc -> sum + "${exc.message}" })
    }
}

fun validatePropertyArguments(parentType: Type, field: Field, requestNode: FieldNode) {
    val argumentValidationExceptions = field.validateArguments(requestNode.arguments, parentType.name)

    if (argumentValidationExceptions.isNotEmpty()) {
        throw ValidationException(argumentValidationExceptions.fold("") { sum, exc ->
            "$sum${exc.message}"
        })
    }
}

fun Field.validateArguments(selectionArgs: List<ArgumentNode>?, parentTypeName: String?): List<ValidationException> {
    if (!(this.arguments.isNotEmpty() || selectionArgs?.isNotEmpty() != true)) {
        return listOf(ValidationException(
            "Property $name on type $parentTypeName has no arguments, found: ${selectionArgs.map { it.name.value }}"
        ))
    }

    val exceptions = mutableListOf<ValidationException>()

    val parameterNames = arguments.map { it.name }
    val invalidArguments = selectionArgs?.filter { it.name.value !in parameterNames }

    if (invalidArguments != null && invalidArguments.isNotEmpty()) {
        exceptions.add(ValidationException("$name does support arguments ${arguments.map { it.name }}. " +
            "Found arguments ${selectionArgs.map { it.name.value }}")
        )
    }

    arguments.forEach { arg ->
        val value = selectionArgs?.firstOrNull { arg.name == it.name.value }
        if (value == null && arg.type.kind == TypeKind.NON_NULL && arg.defaultValue == null) {
            exceptions.add(ValidationException("Missing value for non-nullable argument ${arg.name} on field '$name'"))
        } // else is valid
    }

    return exceptions
}

fun Field.validateArguments(selectionArgs: Arguments?, parentTypeName : String?) : List<ValidationException> {
    if (!(this.arguments.isNotEmpty() || selectionArgs?.isNotEmpty() != true)) {
        return listOf(ValidationException(
            "Property $name on type $parentTypeName has no arguments, found: ${selectionArgs.map { it.key }}")
        )
    }

    val exceptions = mutableListOf<ValidationException>()

    val parameterNames = arguments.map { it.name }
    val invalidArguments = selectionArgs?.filterKeys { it !in parameterNames }

    if(invalidArguments != null && invalidArguments.isNotEmpty()){
        exceptions.add(ValidationException("${this.name} does support arguments ${arguments.map { it.name }}. " +
                "Found arguments ${selectionArgs.map { it.key }}"))
    }

    arguments.forEach { arg ->
        val value = selectionArgs?.get(arg.name)
        if(value != null || arg.type.kind != TypeKind.NON_NULL || arg.defaultValue != null){
            //is valid
        } else {
            exceptions.add(ValidationException("Missing value for non-nullable argument ${arg.name} on field '${this.name}'"))
        }
    }
    return exceptions
}


/**
 * validate that only typed fragments or __typename are present
 */
fun validateUnionRequest(field: Field.Union<*>, selectionNode: FieldNode) {
    val illegalChildren = selectionNode.selectionSet?.selections?.filterNot {
        !(it is FieldNode && it.name.value != "__typename")
    }

    if (illegalChildren?.any() == true) {
        throw RequestException(
            "Invalid selection set with properties: ${illegalChildren.joinToString(prefix = "[", postfix = "]") {
                (it as FieldNode).aliasOrName.value
            }} " +
                    "on union type property ${field.name} : ${field.returnType.possibleTypes.map { it.name }}"
        )
    }
}

/**
 * validate that only typed fragments or __typename are present
 */
fun validateUnionRequest(field: Field.Union<*>, selectionNode: SelectionNode) {
    val illegalChildren = selectionNode.children?.filterNot {
        it is Fragment.Inline || it is Fragment.External || it.key == "__typename"
    }

    if (illegalChildren?.any() == true) {
        throw RequestException(
                "Invalid selection set with properties: $illegalChildren " +
                        "on union type property ${field.name} : ${field.returnType.possibleTypes.map { it.name }}"
        )
    }
}

fun validateName(name : String) {
    if(name.startsWith("__")){
        throw SchemaException("Illegal name '$name'. " +
                "Names starting with '__' are reserved for introspection system"
        )
    }
}

fun assertValidObjectType(kClass: KClass<*>) {
    when {
    //function before generic, because it is its subset
        kClass.isSubclassOf(Function::class) ->
            throw SchemaException("Cannot handle function $kClass as Object type")
        kClass.isSubclassOf(Enum::class) ->
            throw SchemaException("Cannot handle enum class $kClass as Object type")
    }
}

fun validateFragment(set: SelectionSetNode, name: String) = set.selections.forEach { validateFragment(it, name) }
fun validateFragment(node: com.apurebase.kgraphql.schema.jol.ast.SelectionNode?, name: String) {
    val str = when (node) {
        is FieldNode -> node.aliasOrName.value
        is FragmentSpreadNode -> node.name.value
        is InlineFragmentNode -> "inline"
        null -> "null"
    }
    println("Validating $str for $name")
    when (node) {
        is FieldNode -> Unit
        is FragmentSpreadNode -> if (node.name.value == name) throw RequestException("Fragment spread circular references are not allowed")
        is InlineFragmentNode -> validateFragment(node.parent, name)
        else -> Unit
    }
}
