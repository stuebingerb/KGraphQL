package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.DefaultSchema
import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.FileNotFoundException

val objectMapper = jacksonObjectMapper()

@JvmName("deserializeString")
fun deserialize(json: String): Map<*, *> {
    return objectMapper.readValue(json, HashMap::class.java)
}

fun String.deserialize(): Map<*, *> = deserialize(this)

@Suppress("UNCHECKED_CAST")
fun <T> Map<*, *>.extract(path: String): T {
    val tokens = path.trim().split('/').filter(String::isNotBlank)
    try {
        return tokens.fold(this as Any?) { workingMap, token ->
            if (token.contains('[')) {
                require((workingMap as Map<*, *>).containsKey(token.substringBefore('[')))
                val list = workingMap[token.substringBefore('[')]
                val index = token.substring(token.indexOf('[') + 1, token.length - 1).toInt()
                (list as List<*>)[index]
            } else {
                require((workingMap as Map<*, *>).containsKey(token))
                workingMap[token]
            }
        } as T
    } catch (e: Exception) {
        throw IllegalArgumentException("Path: $path does not exist in map: $this", e)
    }
}

fun defaultSchema(block: SchemaBuilder.() -> Unit): DefaultSchema {
    return SchemaBuilder().apply(block).build() as DefaultSchema
}

fun assertNoErrors(map: Map<*, *>) {
    if (map["errors"] != null) throw AssertionError("Errors encountered: ${map["errors"]}")
    if (map["data"] == null) throw AssertionError("Data is null")
}

suspend fun executeEqualQueries(schema: Schema, expected: Map<*, *>, vararg queries: String) {
    queries.map { request ->
        schema.execute(request).deserialize()
    }.forAll { map ->
        map shouldBe expected
    }
}

fun Any.getResourceAsFile(name: String): File =
    this::class.java.classLoader.getResource(name)?.toURI()?.let(::File) ?: throw FileNotFoundException()

object ResourceFiles {
    val kitchenSinkQuery = getResourceAsFile("kitchen-sink.graphql").readText()
}
