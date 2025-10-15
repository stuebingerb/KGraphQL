plugins {
    id("library-conventions")
    alias(libs.plugins.serialization)
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(project(":kgraphql"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)

    testImplementation(libs.junit.jupiter.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.auth)
    testRuntimeOnly(libs.junit.launcher)
}
