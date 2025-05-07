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
    override val definition: StitchedSchemaDefinition
) : SchemaCompilation(configuration, definition) {
    private val remoteSchemaCompilation = RemoteSchemaCompilation(configuration)

    override suspend fun perform(): DefaultSchema {
        definition.unions.forEach { handleUnionType(it) }
        definition.objects.forEach { handleObjectType(it.kClass) }
        definition.inputObjects.forEach { handleInputType(it.kClass) }

        definition.remoteSchemas.forEach { (url, schema) ->
            remoteSchemaCompilation.perform(url, schema)
        }

        val queryType =
            handleQueries(definition.queries.map { handleOperation(it) } + remoteSchemaCompilation.remoteQueries)
        val mutationType =
            handleMutations(definition.mutations.map { handleOperation(it) } + remoteSchemaCompilation.remoteMutations)
        val subscriptionType = handleSubscriptions(definition.subscriptions.map { handleOperation(it) })

        queryTypeProxies.forEach { (kClass, typeProxy) ->
            introspectPossibleTypes(kClass, typeProxy)
            introspectInterfaces(kClass, typeProxy)
        }

        val typesByName =
            (queryTypeProxies.values + enums.values + scalars.values + inputTypeProxies.values + unions).associateByTo(
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
            val originalType: TypeProxy = (typesByName[typeName] as? TypeProxy)
                ?: throw SchemaException("Stitched type $typeName does not exist")
            val stitchedFields = stitchedProperties.map { property ->
                validateName(property.fieldName)
                if (originalType.fields?.any { it.name == property.fieldName } == true) {
                    throw SchemaException("Cannot add stitched field with duplicated name '${property.fieldName}'")
                }
                val remoteQuery = queryType.fields?.firstOrNull { it.name == property.remoteQueryName }
                    ?: error("Stitched remote query ${property.remoteQueryName} does not exist")
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
                remoteSchemaCompilation.remoteRootOperation(stitchedField, stitchedRemoteUrl, property.remoteQueryName)
            }
            val proxiedOriginalType = originalType.proxied
            originalType.proxied = when (proxiedOriginalType) {
                is Type.RemoteObject -> proxiedOriginalType.withStitchedFields(stitchedFields)
                is Type.Object<*> -> proxiedOriginalType.withStitchedFields(stitchedFields)
                else -> throw SchemaException("Type ${proxiedOriginalType.unwrapped().name} cannot be stitched")
            }
        }

        val model = SchemaModel(
            query = queryType,
            mutation = mutationType,
            subscription = subscriptionType,
            queryTypes = queryTypeProxies + enums + scalars,
            inputTypes = inputTypeProxies + enums + scalars,
            allTypes = typesByName.values.toList(),
            directives = definition.directives.map { handlePartialDirective(it) },
            // TODO: we shouldn't need to do a full recompilation again
            remoteTypesBySchema = definition.remoteSchemas.mapValues {
                RemoteSchemaCompilation(configuration).perform(it.key, it.value)
            }
        )
        val schema = DefaultSchema(configuration, model)
        schemaProxy.proxiedSchema = schema
        return schema
    }
}
