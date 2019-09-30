package com.apurebase.kgraphql.jol

import java.io.File

@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
fun Any.getResourceAsFile(name: String): File = this::class.java.classLoader.getResource(name).toURI().let(::File)

object ResourceFiles {
    val kitchenSinkQuery = getResourceAsFile("kitchen-sink.graphql").readText()
}


const val d = '$'
