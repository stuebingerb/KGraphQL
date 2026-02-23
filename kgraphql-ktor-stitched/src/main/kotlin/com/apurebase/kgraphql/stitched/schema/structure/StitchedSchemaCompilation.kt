package com.apurebase.kgraphql.stitched.schema.structure

import com.apurebase.kgraphql.ExperimentalAPI
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.structure.Field
import com.apurebase.kgraphql.schema.structure.SchemaCompilation
import com.apurebase.kgraphql.schema.structure.SchemaModel
import com.apurebase.kgraphql.schema.structure.Type
import com.apurebase.kgraphql.schema.structure.TypeProxy
import com.apurebase.kgraphql.schema.structure.validateName
import com.apurebase.kgraphql.stitched.schema.configuration.StitchedSchemaConfiguration

@ExperimentalAPI
class StitchedSchemaCompilation(
    override val configuration: StitchedSchemaConfiguration,
    override val definition: StitchedSchemaDefinition,
    val localSchemaDescription: String?
) : SchemaCompilation(configuration, definition, localSchemaDescription) {
    private val remoteSchemaCompilation = RemoteSchemaCompilation(configuration)

    override suspend fun perform(): DefaultSchema {
        handleBaseTypes()

        definition.remoteSchemas.forEach { (url, schema) ->
            remoteSchemaCompilation.perform(url, schema)
        }

        val queryType = handleQueries(localQueryFields() + remoteSchemaCompilation.remoteQueries)
        val mutationType = handleMutations(localMutationFields() + remoteSchemaCompilation.remoteMutations)
        val subscriptionType = handleSubscriptions(localSubscriptionFields())

        introspectTypes()

        val typesByName = (queryTypeProxies.values + enums.values + scalars.values + inputTypeProxies.values + unions).associateByTo(
                mutableMapOf()
            ) { it.name }
        (remoteSchemaCompilation.remoteQueryTypeProxies + remoteSchemaCompilation.remoteInputTypeProxies).forEach {
            if (typesByName[it.key] == null) {
                typesByName[it.key] = it.value
            } else {
                // Ignore duplicate type from remote
            }
        }

        definition.stitchedProperties.groupBy { it.typeName }.forEach { (typeName, stitchedProperties) ->
            wrapExceptions("Unable to handle stitched type '$typeName'") {
                val originalType: TypeProxy = typesByName[typeName] as? TypeProxy
                    ?: throw SchemaException("Type does not exist in any schema")
                val stitchedFields = stitchedProperties.map { property ->
                    validateName(property.fieldName)
                    if (originalType.fields?.any { it.name == property.fieldName } == true) {
                        throw SchemaException("Cannot add stitched field with duplicated name '${property.fieldName}'")
                    }
                    val remoteQuery = queryType.fields?.firstOrNull { it.name == property.remoteQueryName }
                        ?: throw SchemaException("Stitched remote query '${property.remoteQueryName}' does not exist")
                    var stitchedType = remoteQuery.returnType

                    if (!property.nullable && stitchedType.isNullable()) {
                        stitchedType = Type.NonNull(stitchedType)
                    } else if (property.nullable && stitchedType.isNotNullable()) {
                        stitchedType = stitchedType.unwrapNonNull()
                    }

                    val argsFromParent =
                        property.inputArguments.filter { it.parentFieldName != null }.associate { inputArgument ->
                            remoteQuery.args.first { it.name == inputArgument.name } to inputArgument.parentFieldName!!
                        }
                    val args = remoteQuery.args.filterNot { arg -> arg.name in argsFromParent.keys.map { it.name } }
                    val stitchedField =
                        Field.Delegated(property.fieldName, null, false, null, args, stitchedType, argsFromParent)
                    val stitchedRemoteUrl = (remoteQuery as? Field.RemoteOperation<*, *>)?.remoteUrl
                        ?: configuration.localUrl
                        ?: throw SchemaException("Remote type without url")
                    remoteSchemaCompilation.remoteRootOperation(
                        stitchedField,
                        stitchedRemoteUrl,
                        property.remoteQueryName
                    )
                }
                val proxiedOriginalType = originalType.proxied
                originalType.proxied = when (proxiedOriginalType) {
                    is Type.RemoteObject -> proxiedOriginalType.withStitchedFields(stitchedFields)
                    is Type.Object<*> -> proxiedOriginalType.withStitchedFields(stitchedFields)
                    else -> throw SchemaException("Type '${proxiedOriginalType.unwrapped().name}' cannot be stitched")
                }
            }
        }

        val model = SchemaModel(
            query = queryType,
            mutation = mutationType,
            subscription = subscriptionType,
            queryTypes = queryTypeProxies + enums + scalars,
            inputTypes = inputTypeProxies + enums + scalars,
            // Query, mutation, and subscription type are added for introspection (only) in SchemaModel; filter them
            // out here to prevent duplicates when remote schemas have references to any
            allTypes = typesByName.values.filter {
                it.name !in setOfNotNull(
                    queryType.name,
                    mutationType?.name,
                    subscriptionType?.name
                )
            },
            directives = definition.directives.map { handlePartialDirective(it) },
            // TODO: we shouldn't need to do a full recompilation again
            remoteTypesBySchema = definition.remoteSchemas.mapValues {
                RemoteSchemaCompilation(configuration).perform(it.key, it.value)
            },
            description = localSchemaDescription // TODO: schemaDescription
        )
        val schema = DefaultSchema(configuration, model)
        schemaProxy.proxiedSchema = schema
        return schema
    }
}
