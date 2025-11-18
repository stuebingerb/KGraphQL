repositories {
    gradlePluginPortal()
}

plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlin.gradle)
    implementation(libs.dokka.gradle)
    implementation(libs.mavenPublish.gradle)
    implementation(libs.detekt.gradle)
    implementation(libs.ktlint.gradle)
}
