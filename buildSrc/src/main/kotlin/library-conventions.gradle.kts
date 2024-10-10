import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("kotlin-conventions")
    `java-library`
}

kotlin {
    jvmToolchain(11)
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

group = "de.stuebingerb"
version = "0.20.0"
