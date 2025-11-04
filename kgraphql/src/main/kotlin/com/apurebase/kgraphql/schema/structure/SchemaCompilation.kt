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
import com.apurebase.kgraphql.schema.model.EnumValueDef
import com.apurebase.kgraphql.schema.model.FunctionWrapper
import com.apurebase.kgraphql.schema.model.InputValueDef
import com.apurebase.kgraphql.schema.model.PropertyDef
import com.apurebase.kgraphql.schema.model.QueryDef
import com.apurebase.kgraphql.schema.model.SchemaDefinition
import com.apurebase.kgraphql.schema.model.Transformation
import com.apurebase.kgraphql.schema.model.TypeDef
import kotlinx.coroutines.CancellationException
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
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
open class SchemaCompilation(
    open val configuration: SchemaConfiguration,
    open val definition: SchemaDefinition
) {
    protected val queryTypeProxies = mutableMapOf<KClass<*>, TypeProxy>()

    protected val inputTypeProxies = mutableMapOf<KClass<*>, TypeProxy>()

    protected val unions = mutableListOf<Type.Union>()

    protected val enums by lazy {
        definition.enums.associateTo(mutableMapOf()) { enum -> enum.kClass to enum.toEnumType() }
    }

    protected val scalars by lazy { definition.scalars.associate { scalar -> scalar.kClass to scalar.toScalarType() } }

    protected val schemaProxy = SchemaProxy()

    private val contextType = Type._Context()

    private val executionType = Type._ExecutionNode()

    private enum class TypeCategory {
        INPUT, QUERY
    }

    open suspend fun perform(): DefaultSchema {
        handleBaseTypes()

        val queryType = handleQueries(localQueryFields())
        val mutationType = handleMutations(localMutationFields())
        val subscriptionType = handleSubscriptions(localSubscriptionFields())

        introspectTypes()

        val model = SchemaModel(
            query = queryType,
            mutation = mutationType,
            subscription = subscriptionType,
            queryTypes = queryTypeProxies + enums + scalars,
            inputTypes = inputTypeProxies + enums + scalars,
            allTypes = queryTypeProxies.values
                + inputTypeProxies.values
                + enums.values
                + scalars.values
                + unions.distinctBy(Type.Union::name),
            directives = definition.directives.map { handlePartialDirective(it) },
            remoteTypesBySchema = emptyMap()
        )
        val schema = DefaultSchema(configuration, model)
        schemaProxy.proxiedSchema = schema
        return schema
    }

    protected suspend fun handleBaseTypes() {
        definition.unions.forEach {
            wrapExceptions("Unable to handle union type '${it.name}'") { handleUnionType(it) }
        }
        definition.objects.forEach {
            wrapExceptions("Unable to handle object type '${it.name}'") { handleObjectType(it.kClass) }
        }
        definition.inputObjects.forEach {
            wrapExceptions("Unable to handle input type '${it.name}'") { handleInputType(it.kClass) }
        }

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

    protected suspend fun handlePartialDirective(directive: Directive.Partial): Directive {
        val inputValues = handleInputValues(directive.execution, emptyList())
        return directive.toDirective(inputValues)
    }

    protected suspend fun localSubscriptionFields() = definition.subscriptions.map {
        wrapExceptions("Unable to handle 'subscription(\"${it.name}\")'") {
            handleOperation(it)
        }
    }

    protected suspend fun localMutationFields() = definition.mutations.map {
        wrapExceptions("Unable to handle 'mutation(\"${it.name}\")'") {
            handleOperation(it)
        }
    }

    protected suspend fun localQueryFields() = definition.queries.map {
        wrapExceptions("Unable to handle 'query(\"${it.name}\")'") {
            handleOperation(it)
        }
    }

    protected suspend fun handleQueries(declaredFields: List<Field>): Type {
        val __typenameField = typenameField(FunctionWrapper.on { -> "Query" })
        if (declaredFields.isEmpty()) {
            throw SchemaException("Schema must define at least one query")
        }
        return Type.OperationObject(
            name = "Query",
            description = "Query object",
            fields = declaredFields + __typenameField + introspectionSchemaQuery() + introspectionTypeQuery()
        )
    }

    protected fun handleMutations(declaredFields: List<Field>): Type? {
        val __typenameField = typenameField(FunctionWrapper.on { -> "Mutation" })
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

    protected fun introspectTypes() = queryTypeProxies.forEach { (kClass, typeProxy) ->
        introspectPossibleTypes(kClass, typeProxy)
        introspectInterfaces(kClass, typeProxy)
    }

    protected fun handleSubscriptions(declaredFields: List<Field>): Type? {
        return if (declaredFields.isNotEmpty()) {
            Type.OperationObject(
                name = "Subscription",
                description = "Subscription object",
                // https://spec.graphql.org/October2021/#sec-Type-Name-Introspection
                //      "__typename may not be included as a root field in a subscription operation."
                fields = declaredFields
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

    protected suspend fun <T : Any> wrapExceptions(prefix: String, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw SchemaException("$prefix: ${e.message}", e)
    }

    private suspend fun handleOperation(operation: BaseOperationDef<*, *>): Field {
        val returnType = handlePossiblyWrappedType(operation.returnType, TypeCategory.QUERY)
        val inputValues = handleInputValues(operation, operation.inputValues)
        return Field.Function(operation, returnType, inputValues)
    }

    private suspend fun handleUnionProperty(unionProperty: PropertyDef.Union<*>): Field {
        val inputValues = handleInputValues(unionProperty, unionProperty.inputValues)
        val type = applyNullability(unionProperty.nullable, handleUnionType(unionProperty.union))
        return Field.Union(unionProperty, type, inputValues)
    }

    private suspend fun handlePossiblyWrappedType(kType: KType, typeCategory: TypeCategory): Type = when {
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

        kType.jvmErasure == Any::class -> throw SchemaException("If you construct a query/mutation generically, you must specify the return type T explicitly with resolver { ... }.returns<T>()")

        kType.jvmErasure.isSubclassOf(Enum::class) -> handleEnumType(kType, kType.jvmErasure.java as Class<Enum<*>>)

        else -> handleSimpleType(kType, typeCategory)
    }

    private fun <T : Enum<T>> handleEnumType(kType: KType, enumClass: Class<Enum<T>>): Type {
        val simpleType = enums[kType.jvmErasure] ?: TypeDef.Enumeration(
            name = enumClass.simpleName,
            kClass = kType.jvmErasure as KClass<T>,
            values = enumClass.enumConstants.map { value ->
                EnumValueDef(value as T)
            },
            description = null
        ).toEnumType().also {
            enums[kType.jvmErasure as KClass<T>] = it
        }
        return applyNullability(kType.isMarkedNullable, simpleType)
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
        val inputValues = handleInputValues(operation.prepare, operation.inputValues)

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

        if (!objectDef.isIntrospectionType()) {
            validateName(objectDef.name)
            if ((enums.values + scalars.values + inputTypeProxies.values + unions).any { it.name == objectDef.name }) {
                throw SchemaException("Cannot add object type with duplicated name '${objectDef.name}'")
            }
            if (queryTypeProxies.any { it.key != kClass && it.value.name == objectDef.name }) {
                throw SchemaException("Cannot add object type with duplicated name '${objectDef.name}'")
            }
        }

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
            queryTypeProxies[value.javaClass.kotlin]?.name ?: error("No query type proxy found for '$value'")
        }

        val __typenameField = typenameField(FunctionWrapper.on(typenameResolver, true))

        // https://spec.graphql.org/October2021/#sec-Objects.Type-Validation
        val declaredFields = kotlinFields.toMutableList<Field>()
        extensionFields.forEach {
            if (declaredFields.any { field -> field.name == it.name }) {
                throw SchemaException("Cannot add extension field with duplicated name '${it.name}'")
            }
            declaredFields.add(it)
        }
        unionFields.forEach {
            if (declaredFields.any { field -> field.name == it.name }) {
                throw SchemaException("Cannot add union field with duplicated name '${it.name}'")
            }
            declaredFields.add(it)
        }
        dataloadExtensionFields.forEach {
            if (declaredFields.any { field -> field.name == it.name }) {
                throw SchemaException("Cannot add dataloaded field with duplicated name '${it.name}'")
            }
            declaredFields.add(it)
        }

        if (declaredFields.isEmpty()) {
            throw SchemaException("An object type must define one or more fields. Found none on type '${objectDef.name}'")
        }

        declaredFields.forEach { validateName(it.name) }

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

        val primaryConstructor = kClass.primaryConstructor
            ?: throw SchemaException("Java class '${kClass.simpleName}' as input type is not supported")

        val inputObjectDef =
            definition.inputObjects.find { it.kClass == kClass } ?: TypeDef.Input(kClass.defaultKQLTypeName().let {
                if (it.endsWith("Input")) {
                    it
                } else {
                    "${it}Input"
                }
            }, kClass)

        validateName(inputObjectDef.name)
        if ((enums.values + scalars.values + queryTypeProxies.values + unions).any { it.name == inputObjectDef.name }) {
            throw SchemaException("Cannot add input type with duplicated name '${inputObjectDef.name}'")
        }
        if (inputTypeProxies.any { it.key != kClass && it.value.name == inputObjectDef.name }) {
            throw SchemaException("Cannot add input type with duplicated name '${inputObjectDef.name}'")
        }

        val objectType = Type.Input(inputObjectDef)
        val typeProxy = TypeProxy(objectType)
        if (typeProxy.checkEqualName(
                inputTypeProxies.values,
                queryTypeProxies.values,
                scalars.values,
                unions,
                enums.values
            )
        ) {
            throw SchemaException("Cannot add input type with duplicated name '${typeProxy.name}'")
        }
        inputTypeProxies[kClass] = typeProxy

        val memberPropertiesByName = kClass.memberProperties.associateBy { it.name }
        val fields = if (kClass.findAnnotation<NotIntrospected>() == null) {
            // Input types are created using their primary constructor. Therefore, it makes sense to (only) use the
            // parameters of this constructor for the fields (cf. https://github.com/stuebingerb/KGraphQL/issues/235).
            // Member properties are sorted by name (https://youtrack.jetbrains.com/issue/KT-41042), and SDL is
            // sorting by name, so we'll also sort constructor parameters to be consistent.
            primaryConstructor.parameters.sortedBy { it.name }.map { parameter ->
                // kProperty is used to configure deprecation and description in the DSL, so we use it
                // if available
                val kProperty = memberPropertiesByName[parameter.name]
                handleKotlinInputProperty(
                    parameter = parameter,
                    kProperty = kProperty,
                    kqlProperty = kProperty?.let { inputObjectDef.kotlinProperties[it] }
                )
            }
        } else {
            listOf()
        }

        if (fields.isEmpty()) {
            throw SchemaException("An input type must define one or more fields. Found none on type '${inputObjectDef.name}'")
        }

        fields.forEach { validateName(it.name) }

        typeProxy.proxied = Type.Input(inputObjectDef, fields)
        return typeProxy
    }

    private suspend fun handleInputValues(
        operation: FunctionWrapper<*>,
        inputValues: List<InputValueDef<*>>
    ): List<InputValue<*>> {
        val invalidInputValues = inputValues
            .map { it.name }
            .filterNot { it in operation.argumentsDescriptor.keys }

        if (invalidInputValues.isNotEmpty()) {
            throw SchemaException("Invalid input values: $invalidInputValues, available: ${operation.argumentsDescriptor.keys}")
        }

        return operation.argumentsDescriptor.map { (name, kType) ->
            val inputValue = inputValues.find { it.name == name }
            val kqlInput = inputValue ?: InputValueDef(kType.jvmErasure, name)
            val inputType = handlePossiblyWrappedType(inputValue?.kType ?: kType, TypeCategory.INPUT)
            if (kqlInput.isDeprecated && !inputType.isNullable()) {
                throw SchemaException("Required argument '${kqlInput.name}' cannot be marked as deprecated")
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
            throw SchemaException("Invalid union type members: ${invalidPossibleTypes.map { it.name }}")
        }

        val __typenameField = typenameField(FunctionWrapper.on({ value: Any ->
            queryTypeProxies[value.javaClass.kotlin]?.name ?: error("No query type proxy found for '$value'")
        }, true))

        val unionType = Type.Union(union, __typenameField, possibleTypes)
        unions.add(unionType)
        return unionType
    }

    private suspend fun <T : Any, R> handleKotlinInputProperty(
        parameter: KParameter,
        kProperty: KProperty1<T, R>?,
        kqlProperty: PropertyDef.Kotlin<*, *>?
    ): InputValue<*> {
        val type = handlePossiblyWrappedType(parameter.type, TypeCategory.INPUT)
        val actualKqlProperty = kqlProperty ?: kProperty?.let { PropertyDef.Kotlin(it) }
        if (actualKqlProperty?.isDeprecated == true && !type.isNullable()) {
            throw SchemaException("Required field '${actualKqlProperty.name}' cannot be marked as deprecated")
        }
        val parameterName = parameter.name ?: throw SchemaException("No name available for parameter '$parameter'")
        return InputValue(
            InputValueDef(
                kClass = parameter.type.jvmErasure,
                // actualKqlProperty can have a custom name configured via DSL
                name = actualKqlProperty?.name ?: parameterName,
                description = actualKqlProperty?.description,
                isDeprecated = actualKqlProperty?.isDeprecated ?: false,
                deprecationReason = actualKqlProperty?.deprecationReason,
                parameterName = parameterName
            ), type
        )
    }

    private suspend fun <T : Any, R> handleKotlinProperty(
        kProperty: KProperty1<T, R>,
        kqlProperty: PropertyDef.Kotlin<*, *>?,
        transformation: Transformation<*, *>?
    ): Field.Kotlin<*, *> {
        val returnType = handlePossiblyWrappedType(transformation?.kFunction?.returnType ?: kProperty.returnType, TypeCategory.QUERY)
        val inputValues = if (transformation != null) {
            handleInputValues(transformation.transformation, emptyList())
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

    // https://spec.graphql.org/October2021/#sec-Names
    // "Names in GraphQL are case-sensitive. That is to say name, Name, and NAME all refer to different names."
    private fun Type.checkEqualName(vararg collections: Collection<Type>): Boolean {
        return collections.fold(false) { acc, list -> acc || list.any { it.name == name } }
    }
}
