package com.apurebase.kgraphql.schema

import com.apurebase.kgraphql.request.isIntrospectionType
import com.apurebase.kgraphql.schema.builtin.BuiltInScalars
import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.introspection.__Described
import com.apurebase.kgraphql.schema.introspection.__Directive
import com.apurebase.kgraphql.schema.introspection.__InputValue
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.introspection.__Type
import com.apurebase.kgraphql.schema.introspection.typeReference
import com.apurebase.kgraphql.schema.model.Depreciable

data class SchemaPrinterConfig(
    /**
     * Whether to *always* include the schema itself. Otherwise, it will only be included
     * if required by the spec.
     */
    val includeSchemaDefinition: Boolean = false,

    /**
     * Whether to include descriptions.
     */
    val includeDescriptions: Boolean = false,

    /**
     * Whether to include built-in directives.
     */
    val includeBuiltInDirectives: Boolean = false
)

class SchemaPrinter(private val config: SchemaPrinterConfig = SchemaPrinterConfig()) {
    /**
     * Returns the given [schema] in schema definition language (SDL). Types and fields are sorted
     * ascending by their name and appear in order of their corresponding spec section, i.e.
     *
     *  - 3.3 Schema
     *  - 3.4 Types
     *    - Scalars
     *    - Objects
     *    - Interfaces
     *    - Unions
     *    - Enums
     *    - Input Types
     *  - 3.13 Directives
     *
     * If descriptions are included, they will use single quotes, broken up on newlines, e.g.
     *
     * ```
     *     "This is a long comment,"
     *     "spanning over multiple"
     *     "lines."
     *     scalar DescriptedScalar
     * ```
     */
    fun print(schema: __Schema): String {
        // All actual (non-introspection) types of the schema
        val schemaTypes =
            schema.types.filterNot { it.isIntrospectionType() || it.name == null }.sortedByName().groupBy { it.kind }

        // Schema
        //   https://spec.graphql.org/draft/#sec-Root-Operation-Types.Default-Root-Operation-Type-Names
        val schemaDefinition = if (includeSchemaDefinition(schema)) {
            buildString {
                appendLine("schema {")
                val indentation = "  "
                // The query root operation type must always be provided
                appendDescription(schema.queryType, indentation)
                appendLine("${indentation}query: ${schema.queryType.name}")
                // The mutation root operation type is optional; if it is not provided, the service does not support mutations
                schema.mutationType?.let {
                    appendDescription(it, indentation)
                    appendLine("${indentation}mutation: ${it.name}")
                }
                // Similarly, the subscription root operation type is also optional; if it is not provided, the service does not support subscriptions
                schema.subscriptionType?.let {
                    appendDescription(it, indentation)
                    appendLine("${indentation}subscription: ${it.name}")
                }
                appendLine("}")
            }
        } else {
            ""
        }

        // Scalars
        //   https://spec.graphql.org/draft/#sec-Scalars.Built-in-Scalars
        //   "When representing a GraphQL schema using the type system definition language, all built-in scalars must be omitted for brevity."
        val scalars = buildString {
            schemaTypes[TypeKind.SCALAR]?.filter { !it.isBuiltInScalar() }?.forEachIndexed { index, type ->
                if (index > 0) {
                    appendLine()
                }
                appendDescription(type)
                appendLine("scalar ${type.name}")
            }
        }

        // Objects (includes Query, Mutation, and Subscription) with non-empty fields
        //  https://spec.graphql.org/draft/#sec-Objects
        val objects = buildString {
            schemaTypes[TypeKind.OBJECT]?.filter { !it.fields.isNullOrEmpty() }?.forEachIndexed { index, type ->
                if (index > 0) {
                    appendLine()
                }
                appendDescription(type)
                appendLine("type ${type.name}${type.implements()} {")
                appendFields(type)
                appendLine("}")
            }
        }

        // Interfaces
        //   https://spec.graphql.org/draft/#sec-Interfaces
        val interfaces = buildString {
            schemaTypes[TypeKind.INTERFACE]?.forEachIndexed { index, type ->
                if (index > 0) {
                    appendLine()
                }
                appendDescription(type)
                appendLine("interface ${type.name}${type.implements()} {")
                appendFields(type)
                appendLine("}")
            }
        }

        // Unions
        //   https://spec.graphql.org/draft/#sec-Unions
        val unions = buildString {
            schemaTypes[TypeKind.UNION]?.forEachIndexed { index, type ->
                if (index > 0) {
                    appendLine()
                }
                appendDescription(type)
                val possibleTypes = type.possibleTypes.sortedByName().map { it.name }
                appendLine("union ${type.name} = ${possibleTypes.joinToString(" | ")}")
            }
        }

        // Enums
        //  https://spec.graphql.org/draft/#sec-Enums
        val enums = buildString {
            schemaTypes[TypeKind.ENUM]?.forEachIndexed { index, type ->
                if (index > 0) {
                    appendLine()
                }
                appendDescription(type)
                appendLine("enum ${type.name} {")
                val indentation = "  "
                type.enumValues.sortedByName().forEach { enumValue ->
                    appendDescription(enumValue, indentation)
                    appendLine("${indentation}${enumValue.name}${enumValue.deprecationInfo()}")
                }
                appendLine("}")
            }
        }

        // Input Types
        //  https://spec.graphql.org/draft/#sec-Input-Objects
        val inputTypes = buildString {
            schemaTypes[TypeKind.INPUT_OBJECT]?.filter { !it.inputFields.isNullOrEmpty() }
                ?.forEachIndexed { index, type ->
                    if (index > 0) {
                        appendLine()
                    }
                    appendDescription(type)
                    appendLine("input ${type.name}${type.implements()} {")
                    val indentation = "  "
                    type.inputFields.sortedByName().forEach { field ->
                        appendDescription(field, indentation)
                        appendLine("${indentation}${field.name}: ${field.type.typeReference()}${field.deprecationInfo()}")
                    }
                    appendLine("}")
                }
        }

        // Directives
        //   https://spec.graphql.org/draft/#sec-Type-System.Directives.Built-in-Directives
        //   "When representing a GraphQL schema using the type system definition language any built-in directive may be omitted for brevity."
        val directives = buildString {
            schema.directives.filter { config.includeBuiltInDirectives || !it.isBuiltIn() }.sortedByName()
                .forEachIndexed { index, directive ->
                    if (index > 0) {
                        appendLine()
                    }
                    val args = directive.args.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ", prefix = "(", postfix = ")") { arg ->
                            arg.name + ": " + arg.type.typeReference() + arg.defaultInfo()
                        } ?: ""
                    appendLine("directive @${directive.name}$args on ${directive.locations.joinToString(" | ") { it.name }}")
                }
        }

        return listOf(
            schemaDefinition,
            scalars,
            objects,
            interfaces,
            unions,
            enums,
            inputTypes,
            directives
        ).filterNot { it.isBlank() }.joinToString("\n")
    }

    // Computes whether the schema definition should be included. Returns `true` if enforced by the configuration, or if
    // required by the spec:
    //   "The type system definition language can omit the schema definition when each root operation type uses its respective default root type name and no other type uses any default root type name."
    //   "Likewise, when representing a GraphQL schema using the type system definition language, a schema definition should be omitted if each root operation type uses its respective default root type name and no other type uses any default root type name."
    private fun includeSchemaDefinition(schema: __Schema): Boolean =
        config.includeSchemaDefinition || schema.hasRootOperationTypeWithNonDefaultName() || schema.hasRegularTypeWithDefaultRootTypeName()

    private fun __Schema.hasRootOperationTypeWithNonDefaultName(): Boolean = queryType.name != "Query" ||
        (mutationType != null && mutationType?.name != "Mutation") ||
        (subscriptionType != null && subscriptionType?.name != "Subscription")

    private fun __Schema.hasRegularTypeWithDefaultRootTypeName() = types.any {
        (it != queryType && it.name == "Query") ||
            (it != mutationType && it.name == "Mutation") ||
            (it != subscriptionType && it.name == "Subscription")
    }

    private fun Depreciable.deprecationInfo(): String = if (isDeprecated) {
        " @deprecated(reason: \"$deprecationReason\")"
    } else {
        ""
    }

    private fun __Directive.isBuiltIn(): Boolean =
        name in setOf(Directive.DEPRECATED.name, Directive.INCLUDE.name, Directive.SKIP.name)

    private fun __Type.isBuiltInScalar(): Boolean = name in BuiltInScalars.entries.map { it.typeDef.name }

    private fun __Type.implements(): String =
        interfaces
            .sortedByName()
            .mapNotNull { it.name }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = " & ", prefix = " implements ") ?: ""

    private fun Any.description(): List<String>? = when (this) {
        is __Described -> description?.takeIf { it.isNotBlank() }?.lines()
        is __Type -> description?.takeIf { it.isNotBlank() }?.lines()
        else -> null
    }

    // https://spec.graphql.org/October2021/#sec-Descriptions
    private fun StringBuilder.appendDescription(type: Any, indentation: String = ""): StringBuilder {
        type.description()?.takeIf { config.includeDescriptions }?.let { description ->
            description.forEach {
                appendLine("$indentation\"$it\"")
            }
        }
        return this
    }

    private fun StringBuilder.appendFields(type: __Type): StringBuilder {
        val indentation = "  "
        type.fields.sortedByName().forEach { field ->
            appendDescription(field, indentation)
            // Write each field arg in it's own line if we have to add directives or description,
            // otherwise print them on a single line
            if (field.args.any { it.isDeprecated || (config.includeDescriptions && !it.description.isNullOrBlank()) }) {
                appendLine("${indentation}${field.name}(")
                field.args.forEach { arg ->
                    val argsIndentation = "$indentation$indentation"
                    appendDescription(arg, argsIndentation)
                    appendLine("$argsIndentation${arg.name}: ${arg.type.typeReference()}${arg.defaultInfo()}${arg.deprecationInfo()}")
                }
                appendLine("${indentation}): ${field.type.typeReference()}${field.deprecationInfo()}")
            } else {
                val args =
                    field.args.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "(", postfix = ")") { arg ->
                        arg.name + ": " + arg.type.typeReference() + arg.defaultInfo()
                    } ?: ""
                appendLine("${indentation}${field.name}$args: ${field.type.typeReference()}${field.deprecationInfo()}")
            }
        }
        return this
    }

    private fun __InputValue.defaultInfo(): String = defaultValue?.let {
        " = $it"
    } ?: ""

    @JvmName("sortedTypesByName")
    private fun List<__Type>?.sortedByName() =
        orEmpty().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.toString() })

    @JvmName("sortedDescribedByName")
    private fun <T : __Described> List<T>?.sortedByName() =
        orEmpty().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}
