import org.gradle.kotlin.dsl.`kotlin-dsl`

repositories {
    gradlePluginPortal()
}

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle)
    implementation(libs.dokkatoo.gradle)
    implementation(libs.mavenPublish.gradle)
}
