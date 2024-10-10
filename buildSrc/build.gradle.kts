import org.gradle.kotlin.dsl.`kotlin-dsl`

repositories {
    gradlePluginPortal()
}

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(kotlin("gradle-plugin", "2.0.21"))
}
