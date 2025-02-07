@file:Suppress("LocalVariableName")

package com.apurebase.kgraphql.schema.structure

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.configuration.SchemaConfiguration
import com.apurebase.kgraphql.defaultKQLTypeName
import com.apurebase.kgraphql.getIterableElementType
import com.apurebase.kgraphql.isIterable
import com.apurebase.kgraphql.request.isIntrospectionType
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.builtin.BuiltInScalars
import com.apurebase.kgraphql.schema.directive.Directive
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.introspection.NotIntrospected
import com.apurebase.kgraphql.schema.introspection.SchemaProxy
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.model.BaseOperationDef
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.InputValueDef
import com.apurebase.kgraphql.schema.model.PropertyDef
import com.apurebase.kgraphql.schema.model.QueryDef
import com.apurebase.kgraphql.schema.model.SchemaDefinition
import com.apurebase.kgraphql.schema.model.Transformation
import com.apurebase.kgraphql.schema.model.TypeDef
import com.apurebase.kgraphql.schema.structure.stitched.RemoteSchemaCompilation
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

@Suppress("UNCHECKED_CAST")
class SchemaCompilation(
    val configuration: SchemaConfiguration,
    val definition: SchemaDefinition
) {
    private val queryTypeProxies = mutableMapOf<KClass<*>, TypeProxy>()

    private val inputTypeProxies = mutableMapOf<KClass<*>, TypeProxy>()

    private val unions = mutableListOf<Type.Union>()

    private val enums = definition.enums.associate { enum -> enum.kClass to enum.toEnumType() }

    private val scalars = definition.scalars.associate { scalar -> scalar.kClass to scalar.toScalarType() }

    private val schemaProxy = SchemaProxy()

    private val contextType = Type._Context()

    private val executionType = Type._ExecutionNode()

    private enum class TypeCategory {
        INPUT, QUERY
    }

    private val remoteSchemaCompilation = RemoteSchemaCompilation(configuration)

    suspend fun perform(): DefaultSchema {
        definition.unions.forEach { handleUnionType(it) }
        definition.objects.forEach { handleObjectType(it.kClass) }
        definition.inputObjects.forEach { handleInputType(it.kClass) }

        definition.remoteSchemas.forEach { (url, schema) ->
            remoteSchemaCompilation.perform(url, schema)
        }

        val queryType = handleQueries()
        val mutationType = handleMutations()
        val subscriptionType = handleSubscriptions()

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
                // TODO: log as soon as we have logging
            }
        }

        definition.links.groupBy { it.typeName }.forEach { (typeName, links) ->
            val originalType: TypeProxy = (typesByName[typeName] as? TypeProxy)
                ?: throw SchemaException("linked type $typeName does not exist")
            val linkedFields = links.map { link ->
                if (originalType.fields?.any { it.name == link.fieldName } == true) {
                    throw SchemaException("Cannot add stitched field ${link.fieldName} with duplicate name")
                }
                val linkedRemoteQuery = queryType.fields?.firstOrNull { it.name == link.remoteQueryName }
                    ?: error("linked remote query ${link.remoteQueryName} does not exist")
                var linkedType = linkedRemoteQuery.returnType

                // TODO: we might (should?) be able to get rid of link.nullable as well
                if (!link.nullable && linkedType.isNullable()) {
                    linkedType = Type.NonNull(linkedType)
                } else if (link.nullable && linkedType.isNotNullable()) {
                    linkedType = linkedType.unwrapNonNull()
                }

                val argsFromParent =
                    link.linkArguments.filter { it.parentFieldName != null }.associate { linkedArgument ->
                        linkedRemoteQuery.args.first { it.name == linkedArgument.name } to linkedArgument.parentFieldName!!
                    }
                val args = linkedRemoteQuery.args.filterNot { arg -> arg.name in argsFromParent.keys.map { it.name } }
                val linkedField = Field.Delegated(link.fieldName, null, false, null, args, linkedType, argsFromParent)
                val linkedRemoteUrl = (linkedRemoteQuery as? Field.RemoteOperation<*, *>)?.remoteUrl
                    ?: link.localUrl
                    ?: throw SchemaException("linked type without url")
                remoteSchemaCompilation.remoteRootOperation(linkedField, linkedRemoteUrl, link.remoteQueryName)
            }
            // TODO: support *local* original types
            originalType.proxied = (originalType.proxied as Type.RemoteObject).withStitchedFields(linkedFields)
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

    private fun introspectPossibleTypes(kClass: KClass<*>, typeProxy: TypeProxy) {
        val proxied = typeProxy.proxied
        if (proxied is Type.Interface<*>) {
            val possibleTypes = queryTypeProxies.filter { (otherKClass, otherTypeProxy) ->
                otherTypeProxy.kind == TypeKind.OBJECT && otherKClass != kClass && otherKClass.isSubclassOf(kClass)
            }.values.toList()

            typeProxy.proxied = proxied.withPossibleTypes(possibleTypes)
        }
    }

    private fun introspectInterfaces(kClass: KClass<*>, typeProxy: TypeProxy) {
        val proxied = typeProxy.proxied
        if (proxied is Type.Object<*>) {
            val interfaces = queryTypeProxies.filter { (otherKClass, otherTypeProxy) ->
                otherTypeProxy.kind == TypeKind.INTERFACE && otherKClass != kClass && kClass.isSubclassOf(otherKClass)
            }.values.toList()

            typeProxy.proxied = proxied.withInterfaces(interfaces)
        } else if (proxied is Type.Interface<*>) {
            val interfaces = queryTypeProxies.filter { (otherKClass, otherTypeProxy) ->
                otherTypeProxy.kind == TypeKind.INTERFACE && otherKClass != kClass && kClass.isSubclassOf(otherKClass)
            }.values.toList()

            typeProxy.proxied = proxied.withInterfaces(interfaces)
        }
    }

    private suspend fun handlePartialDirective(directive: Directive.Partial): Directive {
        val inputValues = handleInputValues(directive.name, directive.execution, emptyList())
        return directive.toDirective(inputValues)
    }

    private suspend fun handleQueries(): Type {
        val __typenameField = typenameField(FunctionWrapper.on { -> "Query" })
        val declaredFields = definition.queries.map { handleOperation(it) } + remoteSchemaCompilation.remoteQueries
        if (declaredFields.isEmpty()) {
            throw SchemaException("Schema must define at least one query")
        }
        return Type.OperationObject(
            name = "Query",
            description = "Query object",
            fields = declaredFields + __typenameField + introspectionSchemaQuery() + introspectionTypeQuery()
        )
    }

    private suspend fun handleMutations(): Type? {
        val __typenameField = typenameField(FunctionWrapper.on { -> "Mutation" })
        val declaredFields = definition.mutations.map { handleOperation(it) } + remoteSchemaCompilation.remoteMutations
        return if (declaredFields.isNotEmpty()) {
            Type.OperationObject(
                name = "Mutation",
                description = "Mutation object",
                fields = declaredFields + __typenameField
            )
        } else {
            null
        }
    }

    private suspend fun handleSubscriptions(): Type? {
        val declaredFields = definition.subscriptions.map { handleOperation(it) }
        return if (declaredFields.isNotEmpty()) {
            Type.OperationObject(
                name = "Subscription",
                description = "Subscription object",
                // https://spec.graphql.org/October2021/#sec-Type-Name-Introspection
                //      "__typename may not be included as a root field in a subscription operation."
                fields = definition.subscriptions.map { handleOperation(it) }
            )
        } else {
            null
        }
    }

    private suspend fun introspectionSchemaQuery() = handleOperation(
        QueryDef("__schema", FunctionWrapper.on<__Schema> { schemaProxy })
    )

    private suspend fun introspectionTypeQuery() = handleOperation(
        QueryDef("__type", FunctionWrapper.on { name: String ->
            schemaProxy.types.firstOrNull { it.name == name }
        })
    )

    private suspend fun handleOperation(operation: BaseOperationDef<*, *>): Field {
        val returnType = handlePossiblyWrappedType(operation.returnType, TypeCategory.QUERY)
        val inputValues = handleInputValues(operation.name, operation, operation.inputValues)
        return Field.Function(operation, returnType, inputValues)
    }

    private suspend fun handleUnionProperty(unionProperty: PropertyDef.Union<*>): Field {
        val inputValues = handleInputValues(unionProperty.name, unionProperty, unionProperty.inputValues)
        val type = applyNullability(unionProperty.nullable, handleUnionType(unionProperty.union))
        return Field.Union(unionProperty, type, inputValues)
    }

    private suspend fun handlePossiblyWrappedType(kType: KType, typeCategory: TypeCategory): Type = try {
        when {
            kType.isIterable() -> handleCollectionType(kType, typeCategory)
            kType.jvmErasure == Context::class && typeCategory == TypeCategory.INPUT -> contextType
            kType.jvmErasure == Execution.Node::class && typeCategory == TypeCategory.INPUT -> executionType
            kType.jvmErasure == Context::class && typeCategory == TypeCategory.QUERY -> throw SchemaException("Context type cannot be part of schema")
            kType.arguments.isNotEmpty() -> configuration.genericTypeResolver.resolveMonad(kType)
                .let { handlePossiblyWrappedType(it, typeCategory) }

            kType.jvmErasure.isSealed -> TypeDef.Union(
                name = kType.jvmErasure.simpleName!!,
                members = kType.jvmErasure.sealedSubclasses.toSet(),
                description = null
            ).let { applyNullability(kType.isMarkedNullable, handleUnionType(it)) }

            else -> handleSimpleType(kType, typeCategory)
        }
    } catch (e: Throwable) {
        // The `KotlinReflectionInternalError` happens during `kType.jvmErasure` and cannot be caught directly
        if ("KotlinReflectionInternalError" in e.toString()) {
            throw SchemaException("If you construct a query/mutation generically, you must specify the return type T explicitly with resolver{ ... }.returns<T>()")
        } else {
            throw e
        }
    }

    private suspend fun handleCollectionType(kType: KType, typeCategory: TypeCategory): Type {
        val type = kType.getIterableElementType()
        val nullableListType = Type.AList(handlePossiblyWrappedType(type, typeCategory), kType.jvmErasure)
        return applyNullability(kType.isMarkedNullable, nullableListType)
    }

    private suspend fun handleSimpleType(kType: KType, typeCategory: TypeCategory): Type {
        val simpleType = handleRawType(kType.jvmErasure, typeCategory)
        return applyNullability(kType.isMarkedNullable, simpleType)
    }

    private fun applyNullability(isNullable: Boolean, simpleType: Type): Type {
        return if (!isNullable) {
            Type.NonNull(simpleType)
        } else {
            simpleType
        }
    }

    private suspend fun handleRawType(kClass: KClass<*>, typeCategory: TypeCategory): Type {
        when (val type = unions.find { it.name == kClass.simpleName }) {
            null -> Unit
            else -> return type
        }

        if (kClass == Context::class) {
            throw SchemaException("Context type cannot be part of schema")
        }

        val cachedInstances = when (typeCategory) {
            TypeCategory.QUERY -> queryTypeProxies
            TypeCategory.INPUT -> inputTypeProxies
        }

        return cachedInstances[kClass]
            ?: enums[kClass]
            ?: scalars[kClass]
            ?: when (typeCategory) {
                TypeCategory.QUERY -> handleObjectType(kClass)
                TypeCategory.INPUT -> handleInputType(kClass)
            }
    }

    private suspend fun <T, K, R> handleDataloadOperation(
        operation: PropertyDef.DataLoadedFunction<T, K, R>
    ): Field {
        val returnType = handlePossiblyWrappedType(operation.returnType, TypeCategory.QUERY)
        val inputValues = handleInputValues(operation.name, operation.prepare, operation.inputValues)

        return Field.DataLoader(
            kql = operation,
            loader = operation.loader,
            returnType = returnType,
            arguments = inputValues
        )
    }

    private suspend fun handleObjectType(kClass: KClass<*>): Type {
        assertValidObjectType(kClass)
        val objectDefs = definition.objects.filter { it.kClass.isSuperclassOf(kClass) }
        val objectDef = objectDefs.find { it.kClass == kClass } ?: TypeDef.Object(kClass.defaultKQLTypeName(), kClass)

        // treat introspection types as objects -> adhere to reference implementation behaviour
        val kind = if (kClass.isFinal || objectDef.isIntrospectionType()) {
            TypeKind.OBJECT
        } else {
            TypeKind.INTERFACE
        }

        val objectType = if (kind == TypeKind.OBJECT) {
            Type.Object(objectDef)
        } else {
            Type.Interface(objectDef)
        }
        val typeProxy = TypeProxy(objectType)
        queryTypeProxies[kClass] = typeProxy

        val allKotlinProperties = objectDefs.fold(emptyMap<String, PropertyDef.Kotlin<*, *>>()) { acc, def ->
            acc + def.kotlinProperties.mapKeys { (property) -> property.name }
        }
        val allTransformations = objectDefs.fold(emptyMap<String, Transformation<*, *>>()) { acc, def ->
            acc + def.transformations.mapKeys { (property) -> property.name }
        }

        val kotlinFields = kClass.memberProperties
            .filter { field -> field.visibility == KVisibility.PUBLIC }
            .filterNot { field -> objectDefs.any { it.isIgnored(field.name) } }
            .map { property ->
                handleKotlinProperty(
                    kProperty = property,
                    kqlProperty = allKotlinProperties[property.name],
                    transformation = allTransformations[property.name]
                )
            }

        val extensionFields = objectDefs
            .flatMap(TypeDef.Object<*>::extensionProperties)
            .map { property -> handleOperation(property) }

        val dataloadExtensionFields = objectDefs
            .flatMap(TypeDef.Object<*>::dataloadExtensionProperties)
            .map { property -> handleDataloadOperation(property) }

        val unionFields = objectDefs
            .flatMap(TypeDef.Object<*>::unionProperties)
            .map { property -> handleUnionProperty(property) }

        val typenameResolver: suspend (Any) -> String = { value: Any ->
            queryTypeProxies[value.javaClass.kotlin]?.name ?: error("No query type proxy found for $value")
        }

        val __typenameField = typenameField(FunctionWrapper.on(typenameResolver, true))

        val declaredFields = kotlinFields + extensionFields + unionFields + dataloadExtensionFields

        if (declaredFields.isEmpty()) {
            throw SchemaException("An Object type must define one or more fields. Found none on type ${objectDef.name}")
        }

        declaredFields.find { it.name.startsWith("__") }?.let { field ->
            throw SchemaException("Illegal name '${field.name}'. Names starting with '__' are reserved for introspection system")
        }

        val allFields = declaredFields + __typenameField
        typeProxy.proxied = if (kind == TypeKind.OBJECT) {
            Type.Object(objectDef, allFields)
        } else {
            Type.Interface(objectDef, allFields)
        }
        return typeProxy
    }

    private suspend fun handleInputType(kClass: KClass<*>): Type {
        assertValidObjectType(kClass)

        if (kClass.primaryConstructor == null) {
            throw SchemaException("Java class '${kClass.simpleName}' as inputType is not supported")
        }

        val inputObjectDef =
            definition.inputObjects.find { it.kClass == kClass } ?: TypeDef.Input(kClass.defaultKQLTypeName(), kClass)
        val objectType = Type.Input(inputObjectDef)
        val typeProxy = TypeProxy(objectType)
        inputTypeProxies[kClass] = typeProxy

        val fields = if (kClass.findAnnotation<NotIntrospected>() == null) {
            kClass.memberProperties.map { property ->
                handleKotlinInputProperty(
                    kProperty = property,
                    kqlProperty = inputObjectDef.kotlinProperties[property]
                )
            }
        } else {
            listOf()
        }

        typeProxy.proxied = Type.Input(inputObjectDef, fields)
        return typeProxy
    }

    private suspend fun handleInputValues(
        operationName: String,
        operation: FunctionWrapper<*>,
        inputValues: List<InputValueDef<*>>
    ): List<InputValue<*>> {
        val invalidInputValues = inputValues
            .map { it.name }
            .filterNot { it in operation.argumentsDescriptor.keys }

        if (invalidInputValues.isNotEmpty()) {
            throw SchemaException("Invalid input values on $operationName: $invalidInputValues")
        }

        return operation.argumentsDescriptor.map { (name, kType) ->
            val inputValue = inputValues.find { it.name == name }
            val kqlInput = inputValue ?: InputValueDef(kType.jvmErasure, name)
            val inputType = handlePossiblyWrappedType(inputValue?.kType ?: kType, TypeCategory.INPUT)
            if (kqlInput.isDeprecated && !inputType.isNullable()) {
                throw SchemaException("Required arguments cannot be marked as deprecated")
            }
            InputValue(kqlInput, inputType)
        }
    }

    private suspend fun handleUnionType(union: TypeDef.Union): Type.Union {
        val possibleTypes = union.members.map {
            handleRawType(it, TypeCategory.QUERY)
        }

        val invalidPossibleTypes = possibleTypes.filterNot { it.kind == TypeKind.OBJECT }
        if (invalidPossibleTypes.isNotEmpty()) {
            throw SchemaException("Invalid union type members")
        }

        val __typenameField = typenameField(FunctionWrapper.on({ value: Any ->
            queryTypeProxies[value.javaClass.kotlin]?.name ?: error("No query type proxy found for $value")
        }, true))

        val unionType = Type.Union(union, __typenameField, possibleTypes)
        unions.add(unionType)
        return unionType
    }

    private suspend fun <T : Any, R> handleKotlinInputProperty(
        kProperty: KProperty1<T, R>,
        kqlProperty: PropertyDef.Kotlin<*, *>?
    ): InputValue<*> {
        val type = handlePossiblyWrappedType(kProperty.returnType, TypeCategory.INPUT)
        val actualKqlProperty = kqlProperty ?: PropertyDef.Kotlin(kProperty)
        if (actualKqlProperty.isDeprecated && !type.isNullable()) {
            throw SchemaException("Required fields cannot be marked as deprecated")
        }
        return InputValue(
            InputValueDef(
                kProperty.returnType.jvmErasure,
                kProperty.name,
                description = actualKqlProperty.description,
                isDeprecated = actualKqlProperty.isDeprecated,
                deprecationReason = actualKqlProperty.deprecationReason
            ), type
        )
    }

    private suspend fun <T : Any, R> handleKotlinProperty(
        kProperty: KProperty1<T, R>,
        kqlProperty: PropertyDef.Kotlin<*, *>?,
        transformation: Transformation<*, *>?
    ): Field.Kotlin<*, *> {
        val returnType = handlePossiblyWrappedType(kProperty.returnType, TypeCategory.QUERY)
        val inputValues = if (transformation != null) {
            handleInputValues("$kProperty transformation", transformation.transformation, emptyList())
        } else {
            emptyList()
        }

        val actualKqlProperty = kqlProperty ?: PropertyDef.Kotlin(kProperty)

        return Field.Kotlin(
            kql = actualKqlProperty as PropertyDef.Kotlin<T, R>,
            returnType = returnType,
            arguments = inputValues,
            transformation = transformation as Transformation<T, R>?
        )
    }

    private fun typenameField(functionWrapper: FunctionWrapper<String>) = Field.Function(
        PropertyDef.Function<Nothing, String>("__typename", functionWrapper),
        BuiltInScalars.STRING.typeDef.toScalarType(),
        emptyList()
    )
}
