import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    base
    kotlin("jvm")
    id("org.jetbrains.dokka") version "1.9.20"
    signing
}

kotlin {
    jvmToolchain(11)
}

val caffeine_version: String by project
val kDataLoader_version: String by project
val deferredJsonBuilder_version: String by project
val kotlin_version: String by project
val serialization_version: String by project
val coroutine_version: String by project
val jackson_version: String by project
val netty_version: String by project
val hamcrest_version: String by project
val kluent_version: String by project
val junit_version: String by project

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutine_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutine_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version") // JVM dependency

    implementation("com.fasterxml.jackson.core:jackson-databind:$jackson_version")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")

    implementation("com.github.ben-manes.caffeine:caffeine:$caffeine_version")
    implementation("com.apurebase:DeferredJsonBuilder:$deferredJsonBuilder_version")

    testImplementation("io.netty:netty-all:$netty_version")
    testImplementation("org.hamcrest:hamcrest:$hamcrest_version")
    testImplementation("org.amshove.kluent:kluent:$kluent_version")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit_version")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junit_version")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutine_version")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutine_version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit_version")
}

tasks {
    compileKotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
    compileTestKotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

    test {
        useJUnitPlatform()
    }
    dokkaHtml {
        outputDirectory.set(layout.buildDirectory.dir("javadoc"))
        dokkaSourceSets {
            configureEach {
                jdkVersion.set(11)
                reportUndocumented.set(true)
                platform.set(org.jetbrains.dokka.Platform.jvm)
            }
        }
    }
}

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier = "sources"
    from(sourceSets.main.get().allSource)
}

val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    archiveClassifier = "javadoc"
    from(tasks.dokkaHtml)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = project.name
            from(components["java"])
            artifact(sourcesJar)
            artifact(dokkaJar)
            pom {
                name.set("KGraphQL")
                description.set("KGraphQL is a Kotlin implementation of GraphQL. It provides a rich DSL to set up the GraphQL schema.")
                url.set("https://kgraphql.io/")
                organization {
                    name.set("stuebingerb")
                    url.set("https://github.com/stuebingerb")
                }
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/stuebingerb/KGraphQL/blob/main/LICENSE.md")
                    }
                }
                developers {
                    developer {
                        id.set("stuebingerb")
                        name.set("stuebingerb")
                        email.set("41049452+stuebingerb@users.noreply.github.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/stuebingerb/KGraphQL.git")
                    developerConnection.set("scm:git:https://github.com/stuebingerb/KGraphQL.git")
                    url.set("https://github.com/stuebingerb/KGraphQL/")
                    tag.set("HEAD")
                }
            }
        }
    }
}

signing {
    isRequired = isReleaseVersion
    useInMemoryPgpKeys(
        System.getenv("ORG_GRADLE_PROJECT_signingKey"),
        System.getenv("ORG_GRADLE_PROJECT_signingPassword")
    )
    sign(publishing.publications["maven"])
}