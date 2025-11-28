package com.apurebase.kgraphql.stitched.schema.structure

import com.apurebase.kgraphql.schema.directive.DirectiveLocation
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.introspection.__Directive
import com.apurebase.kgraphql.schema.introspection.__EnumValue
import com.apurebase.kgraphql.schema.introspection.__Field
import com.apurebase.kgraphql.schema.introspection.__InputValue
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.introspection.__Type
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class IntrospectionResponse(val data: IntrospectionData)

@Serializable
data class IntrospectionData(
    val __schema: IntrospectedSchema
)

@Serializable
data class IntrospectedDirective(
    override val name: String,
    override val locations: List<DirectiveLocation> = emptyList(),
    override val args: List<IntrospectedInputValue> = emptyList(),
    override val isRepeatable: Boolean = false,
    override val description: String? = null
) : __Directive

@Serializable
data class IntrospectedEnumValue(
    override val name: String,
    override val isDeprecated: Boolean = false,
    override val deprecationReason: String? = null,
    override val description: String? = null
) : __EnumValue

@Serializable
data class IntrospectedInputValue(
    override val name: String,
    override val type: IntrospectedType,
    override val defaultValue: String? = null,
    override val isDeprecated: Boolean = false,
    override val deprecationReason: String? = null,
    override val description: String? = null
) : __InputValue

@Serializable
data class IntrospectedField(
    override val name: String,
    override val type: IntrospectedType,
    override val args: List<IntrospectedInputValue> = emptyList(),
    override val isDeprecated: Boolean = false,
    override val deprecationReason: String? = null,
    override val description: String? = null
) : __Field

@Serializable
data class IntrospectedType(
    override val name: String?,
    override val kind: TypeKind = TypeKind.OBJECT,
    override val description: String? = null,
    override var fields: List<IntrospectedField>? = null,
    override val interfaces: List<IntrospectedType>? = null,
    override val possibleTypes: List<IntrospectedType>? = null,
    override val enumValues: List<IntrospectedEnumValue>? = null,
    override val inputFields: List<IntrospectedInputValue>? = null,
    override val ofType: IntrospectedType? = null,
    override val specifiedByURL: String? = null
) : __Type

@Serializable
data class IntrospectedRootOperation(
    override val name: String,
    override val kind: TypeKind = TypeKind.OBJECT,
    override val description: String? = null,
    override var fields: List<IntrospectedField>? = null,
    override val interfaces: List<IntrospectedType>? = null,
    override val possibleTypes: List<IntrospectedType>? = null,
    override val enumValues: List<IntrospectedEnumValue>? = null,
    override val inputFields: List<IntrospectedInputValue>? = null,
    override val ofType: IntrospectedType? = null,
    override val specifiedByURL: String? = null
) : __Type

@Serializable
data class IntrospectedSchema(
    override val queryType: IntrospectedRootOperation,
    override val mutationType: IntrospectedRootOperation?,
    override val subscriptionType: IntrospectedRootOperation?,
    override val types: List<IntrospectedType>,
    override val directives: List<IntrospectedDirective>,
) : __Schema {
    companion object {
        // Decoding the IntrospectionResponse must not fail when there are additional entries in the response, cf.
        // https://spec.graphql.org/September2025/#sec-Additional-Entries:
        //   "Clients must ignore any entries other than those described above."
        //
        // Technically, we could support extensions but we're not doing anything with those anyway, so there's
        // (currently) no point in mapping them.
        private val json = Json { ignoreUnknownKeys = true }

        fun fromIntrospectionResponse(response: String) =
            json.decodeFromString(IntrospectionResponse.serializer(), response).data.__schema
    }
}
