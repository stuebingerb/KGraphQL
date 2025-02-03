package com.apurebase.kgraphql.schema.structure.stitched

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.configuration.SchemaConfiguration
import com.apurebase.kgraphql.schema.builtin.BuiltInScalars
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.introspection.__Field
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.introspection.__Type
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.InputValueDef
import com.apurebase.kgraphql.schema.model.PropertyDef
import com.apurebase.kgraphql.schema.model.TypeDef
import com.apurebase.kgraphql.schema.structure.Field
import com.apurebase.kgraphql.schema.structure.InputValue
import com.apurebase.kgraphql.schema.structure.Type
import com.apurebase.kgraphql.schema.structure.TypeProxy
import com.fasterxml.jackson.databind.JsonNode

class RemoteSchemaCompilation(val configuration: SchemaConfiguration) {
    val remoteQueryTypeProxies = mutableMapOf<String, TypeProxy>()
    val remoteInputTypeProxies = mutableMapOf<String, TypeProxy>()
    val remoteQueries = mutableListOf<Field>()
    val remoteMutations = mutableListOf<Field>()

    fun perform(url: String, schema: __Schema): List<TypeProxy> {
        schema.types.forEach { type ->
            when (type.kind) {
                TypeKind.OBJECT -> {
                    when (type.name) {
                        "Query" -> handleRemoteQueries(type, url)
                        "Mutation" -> handleRemoteMutations(type, url)
                        "Subscription" -> TODO("remote subscriptions are not supported")
                        else -> handleRemoteObjectType(type)
                    }
                }

                TypeKind.UNION -> handleRemoteUnionType(type)
                TypeKind.ENUM -> handleRemoteEnumType(type)
                TypeKind.INPUT_OBJECT -> handleRemoteInputType(type)
                TypeKind.INTERFACE -> handleRemoteInterface(type)
                TypeKind.SCALAR -> handleRemoteScalar(type)
                TypeKind.LIST -> {} // NOOP
                TypeKind.NON_NULL -> {} // NOOP
            }
        }
        return (remoteQueryTypeProxies.values + remoteInputTypeProxies.values).toList()
    }

    private fun handleRemoteQueries(type: __Type, url: String) {
        remoteQueries.addAll(type.fields.orEmpty().map {
            remoteRootOperation(it, url)
        })
    }

    private fun handleRemoteMutations(type: __Type, url: String) {
        remoteMutations.addAll(type.fields.orEmpty().map {
            remoteRootOperation(it, url)
        })
    }

    fun remoteRootOperation(
        field: __Field,
        url: String,
        queryName: String = field.name
    ): Field.RemoteOperation<Nothing, JsonNode?> {
        val kql = PropertyDef.Function<Nothing, JsonNode?>(
            name = field.name,
            resolver = FunctionWrapper.ArityTwo(
                implementation = { node: Execution.Remote, ctx: Context ->
                    val remoteExecutor = requireNotNull(configuration.remoteExecutor) {
                        "Remote executor not defined for ${field.name}"
                    }
                    remoteExecutor.execute(node, ctx)
                },
                hasReceiver = false
            )
        )
        return Field.RemoteOperation(
            kql = kql,
            field = handleRemoteField(field),
            remoteUrl = url,
            remoteQuery = queryName,
            arguments = listOf(
                InputValue(
                    InputValueDef(Execution.Remote::class, "executionNode"),
                    Type._ExecutionNode()
                ),
                InputValue(
                    InputValueDef(Context::class, "ctx"),
                    Type._Context()
                )
            ),
            args = field.args
        )
    }

    private fun handleRemoteScalar(type: __Type): Type {
        val typeName = checkNotNull(type.name) {
            "Cannot handle remote type $type without name"
        }
        val typeProxy = TypeProxy(Type.RemoteScalar(typeName, type.description))
        remoteQueryTypeProxies[typeName] = typeProxy
        return typeProxy
    }

    // TODO: we should probably only search within the *same* schema?
    private fun handleRemoteType(type: __Type): Type {
        return when (type.kind) {
            TypeKind.LIST -> Type.AList(handleRemoteType(type.ofType!!), List::class)
            TypeKind.NON_NULL -> Type.NonNull(handleRemoteType(type.ofType!!))
            TypeKind.OBJECT -> remoteQueryTypeProxies[type.name] ?: handleRemoteObjectType(type)
            TypeKind.ENUM -> remoteQueryTypeProxies[type.name] ?: handleRemoteEnumType(type)
            TypeKind.INTERFACE -> remoteQueryTypeProxies[type.name] ?: handleRemoteInterface(type)
            TypeKind.INPUT_OBJECT -> remoteInputTypeProxies[type.name] ?: handleRemoteInputType(type)
            TypeKind.UNION -> remoteQueryTypeProxies[type.name] ?: handleRemoteUnionType(type)
            TypeKind.SCALAR -> remoteQueryTypeProxies[type.name] ?: handleRemoteScalar(type)
        }
    }

    private fun handleRemoteInterface(type: __Type): Type.RemoteInterface {
        val typeName = checkNotNull(type.name) {
            "Cannot handle remote type $type without name"
        }
        val fields = type.fields.orEmpty().map {
            handleRemoteField(it)
        }
        val interfaces = type.interfaces?.map {
            handleRemoteType(it)
        }

        val objectType = Type.RemoteInterface(typeName, type.description, fields + typenameField(), interfaces)
        val typeProxy = TypeProxy(objectType)
        remoteQueryTypeProxies[typeName] = typeProxy
        return objectType
    }

    private fun handleRemoteField(field: __Field) = Field.Delegated(
        name = field.name,
        description = field.description,
        isDeprecated = field.isDeprecated,
        deprecationReason = field.deprecationReason,
        args = field.args,
        returnType = handleRemoteType(field.type),
        // TODO: only remote operations actually support fields from parent
        argsFromParent = (field as? Field.Delegated)?.argsFromParent.orEmpty()
    )

    private fun handleRemoteObjectType(type: __Type): TypeProxy {
        val typeName = checkNotNull(type.name) {
            "Cannot handle remote type $type without name"
        }
        val fields = type.fields.orEmpty().map {
            handleRemoteField(it)
        }
        val interfaces = type.interfaces?.map {
            handleRemoteType(it)
        }

        val objectType = Type.RemoteObject(typeName, type.description, fields + typenameField(), interfaces)
        val typeProxy = remoteQueryTypeProxies[typeName] ?: TypeProxy(objectType)
        typeProxy.proxied = objectType
        remoteQueryTypeProxies[typeName] = typeProxy
        return typeProxy
    }

    private fun handleRemoteEnumType(type: __Type): Type {
        val typeName = checkNotNull(type.name) {
            "Cannot handle remote type $type without name"
        }

        val enumType = Type.RemoteEnum(typeName, type.description, type.enumValues.orEmpty())
        val typeProxy = TypeProxy(enumType)
        remoteQueryTypeProxies[typeName] = typeProxy
        return enumType
    }

    private fun handleRemoteUnionType(type: __Type): Type.Union {
        val possibleTypes = type.possibleTypes.orEmpty().map {
            handleRemoteType(it)
        }

        val typeName = checkNotNull(type.name) {
            "Cannot handle remote type $type without name"
        }

        val objectDef = TypeDef.Union(typeName, emptySet(), type.description)
        val objectType = Type.Union(objectDef, typenameField(), possibleTypes)
        val typeProxy = TypeProxy(objectType)
        remoteQueryTypeProxies[typeName] = typeProxy
        return objectType
    }

    private fun handleRemoteInputType(type: __Type): Type {
        val typeName = checkNotNull(type.name) {
            "Cannot handle remote type $type without name"
        }
        val objectType = Type.RemoteInputObject(typeName, type.description, type.inputFields.orEmpty())
        val typeProxy = TypeProxy(objectType)
        remoteInputTypeProxies[typeName] = typeProxy
        return objectType
    }

    private fun typenameField() = Field.Function(
        kql = PropertyDef.Function<Nothing, String>(
            name = "__typename",
            resolver = FunctionWrapper.on({ node: JsonNode -> node["__typename"].textValue() }, true)
        ),
        returnType = BuiltInScalars.STRING.typeDef.toScalarType(),
        arguments = emptyList()
    )
}
