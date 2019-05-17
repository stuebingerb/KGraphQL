package com.apurebase.kgraphql


class ValidationException(message: String, cause: Throwable? = null) : RequestException(message, cause)