@file:Suppress("UnstableApiUsage")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.10.0")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "core"

include("kgraphql", "kgraphql-ktor", "kgraphql-ktor-stitched")
