@file:Suppress("UnstableApiUsage")

import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.withType

plugins {
    id("kgraphql-base")
    kotlin("jvm")
    `java-library`
    `jvm-test-suite`
}

testing {
    suites {
        withType<JvmTestSuite> {
            useJUnitJupiter()
        }
    }
}
