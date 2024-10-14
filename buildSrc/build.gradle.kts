import org.gradle.kotlin.dsl.`kotlin-dsl`

repositories {
    gradlePluginPortal()
}

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(kotlin("gradle-plugin", "2.0.21"))
    implementation("dev.adamko.dokkatoo:dokkatoo-plugin:2.4.0")
    implementation("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.30.0")
}
