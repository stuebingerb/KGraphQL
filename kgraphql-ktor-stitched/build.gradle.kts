plugins {
    id("library-conventions")
    alias(libs.plugins.serialization)
}

dependencies {
    api(project(":kgraphql"))
    api(project(":kgraphql-ktor"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.jackson.core.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kluent)
    testImplementation(libs.ktor.server.test.host)
}
