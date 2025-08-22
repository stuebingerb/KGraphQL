package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.schema.model.ast.DocumentNode
import com.github.benmanes.caffeine.cache.Caffeine

internal class CachingDocumentParser(cacheMaximumSize: Long) : RequestParser {

    private sealed class Result {
        class Success(val document: DocumentNode) : Result()
        class Exception(val exception: kotlin.Exception) : Result()
    }

    private val cache = Caffeine.newBuilder().maximumSize(cacheMaximumSize).build<String, Result>()

    override fun parseDocument(input: String): DocumentNode {
        val result = cache.get(input) {
            try {
                Result.Success(Parser(input).parseDocument())
            } catch (e: Exception) {
                Result.Exception(e)
            }
        }

        when (result) {
            is Result.Success -> return result.document
            is Result.Exception -> throw result.exception
        }
    }
}
