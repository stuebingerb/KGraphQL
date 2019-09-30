package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.ExecutionException
import com.apurebase.kgraphql.RequestException
import com.apurebase.kgraphql.isLiteral
import com.apurebase.kgraphql.request.Variables
import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.scalar.deserializeScalar
import com.apurebase.kgraphql.schema.structure2.InputValue
import com.apurebase.kgraphql.schema.structure2.Type
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure


open class ArgumentTransformer(val schema : DefaultSchema) {

    fun transformValue(type: Type, value: String, variables: Variables) : Any? {
        val kType = type.toKType()
        val typeName = type.unwrapped().name

        return when {
            value.startsWith("$") -> {
                variables.get (
                        kType.jvmErasure, kType, typeName, value, { subValue -> transformValue(type, subValue, variables) }
                )
            }
            value == "null" && type.isNullable() -> null
            value == "null" && type.isNotNullable() -> {
                throw RequestException("argument '$value' is not valid value of type ${type.unwrapped().name}")
            }
            else -> {
                return transformString(value, kType)
            }
        }

    }

    private fun transformString(value: String, kType: KType): Any {

        val kClass = kType.jvmErasure

        fun throwInvalidEnumValue(enumType : Type.Enum<*>){
            throw RequestException(
                    "Invalid enum ${schema.model.enums[kClass]?.name} value. Expected one of ${enumType.values}"
            )
        }

        schema.model.enums[kClass]?.let { enumType ->
            if(value.isLiteral()) {
                throw RequestException("String literal '$value' is invalid value for enum type ${enumType.name}")
            }
            return enumType.values.find { it.name == value }?.value ?: throwInvalidEnumValue(enumType)
        } ?: schema.model.scalars[kClass]?.let { scalarType ->
            return deserializeScalar(scalarType, value)
        } ?: throw RequestException("Invalid argument value '$value' for type ${schema.model.inputTypes[kClass]?.name}")
    }

    fun transformCollectionElementValue(inputValue: InputValue<*>, value: String, variables: Variables): Any? {
        assert(inputValue.type.isList())
        val elementType = inputValue.type.unwrapList().ofType as Type?
                ?: throw ExecutionException("Unable to handle value of element of collection without type")

        return transformValue(elementType, value, variables)
    }

    fun transformPropertyValue(parameter: InputValue<*>, value: String, variables: Variables): Any? {
        return transformValue(parameter.type, value, variables)
    }

    fun transformPropertyObjectValue(parameter: InputValue<*>, value: List<*>): Any? {
        return schema.configuration.objectMapper.readValue(value.toJson(), parameter.type.unwrapped().kClass?.java)
    }
}

fun List<*>.toJson() : String {
    val json = StringBuilder()
    var isSimpleList = false
    for ((index, value1) in this.withIndex()) {
        when{
            value1 == "{" || value1 == "[" || value1 == ":" -> json.append(value1)
            (json.substring(json.length - 1) == "," && !isSimpleList) // case a new key
                    || this[index - 1] == "{" -> { // case first key
                // Keys
                json.append("\"")
                json.append(value1)
                json.append("\"")
            }
            else -> {
                // Values
                json.append(value1)
                // if this the last value don't add a coma
                if (index < this.size - 1 && this[index + 1] != "}" && this[index + 1] != "]") json.append(",")
                if (this[index - 1] == "[") isSimpleList = true
                if (isSimpleList && this[index] == "]") isSimpleList = false
            }
        }
    }
    return json.toString()
}