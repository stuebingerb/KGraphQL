import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    id("library-conventions")
    id("org.jetbrains.kotlinx.benchmark") version "0.4.13"
}

lateinit var benchmarkImplementation: String

sourceSets {
    benchmarkImplementation = create("jvm").implementationConfigurationName
}

kotlin
    .target
    .compilations
    .getByName("jvm")
    .associateWith(kotlin.target.compilations.getByName("main"))

benchmark {
    targets {
        register("jvm") {
            this as JvmBenchmarkTarget
            jmhVersion = "1.37"
        }
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.jdk8)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jackson.core.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.caffeine)
    implementation(libs.deferredJsonBuilder)

    testImplementation(libs.hamcrest)
    testImplementation(libs.kluent)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.debug)
    testImplementation(libs.kotlinx.coroutines.test)
    benchmarkImplementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.13")
}
