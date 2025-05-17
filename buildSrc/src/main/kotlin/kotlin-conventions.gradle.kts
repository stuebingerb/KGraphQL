@file:Suppress("UnstableApiUsage")

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
