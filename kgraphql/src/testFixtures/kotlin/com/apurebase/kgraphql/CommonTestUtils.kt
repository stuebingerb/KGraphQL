package com.apurebase.kgraphql

import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.assertions.json.shouldNotContainJsonKey
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.instanceOf
import kotlin.reflect.KClass

infix fun Any?.shouldBeInstanceOf(clazz: KClass<*>) = this shouldBe instanceOf(clazz)

inline fun <reified T : Exception> expect(message: String, block: () -> Unit) {
    shouldThrowExactly<T>(block) shouldHaveMessage message
}

inline fun <reified T : ExecutionError> expectExecutionError(vararg messages: String, block: () -> String) {
    val response = block.invoke()
    response.shouldContainJsonKey("$.data")
    response.shouldContainJsonKeyValue("$.errors.length()", messages.size)
    messages.forEachIndexed { index, message ->
        response.shouldContainJsonKeyValue("$.errors[$index].message", message)
        response.shouldContainJsonKeyValue("$.errors[$index].extensions.type", errorType<T>())
    }
}

inline fun <reified T : RequestError> expectRequestError(message: String, block: () -> String) {
    val response = block.invoke()
    response.shouldNotContainJsonKey("$.data")
    response.shouldContainJsonKeyValue("$.errors.length()", 1)
    response.shouldContainJsonKeyValue("$.errors[0].message", message)
    response.shouldContainJsonKeyValue("$.errors[0].extensions.type", errorType<T>())
}

inline fun <reified T : GraphQLError> errorType() = when (T::class) {
    InvalidInputValueException::class -> BuiltInErrorCodes.BAD_USER_INPUT.name
    ValidationException::class -> BuiltInErrorCodes.GRAPHQL_VALIDATION_FAILED.name
    InvalidSyntaxException::class -> BuiltInErrorCodes.GRAPHQL_PARSE_FAILED.name
    else -> BuiltInErrorCodes.INTERNAL_SERVER_ERROR.name
}
