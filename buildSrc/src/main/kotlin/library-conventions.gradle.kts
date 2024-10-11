import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("kotlin-conventions")
    id("dev.adamko.dokkatoo-html")
    `java-library`
}

kotlin {
    jvmToolchain(11)
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

dokkatoo {
    dokkaGeneratorIsolation.set(
        ProcessIsolation {
            maxHeapSize.set("4g")
        }
    )
}

tasks
    .named<Jar>("javadocJar") {
        from(tasks.dokkatooGeneratePublicationHtml)
    }

group = "de.stuebingerb"
version = "0.20.0"
