plugins {
    id("library-conventions")
    alias(libs.plugins.serialization)
}

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

dependencies {
    api(project(":kgraphql"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kluent)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.auth)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
