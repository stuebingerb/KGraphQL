import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    base
    application
    kotlin("jvm")
    id("org.jetbrains.dokka") version "1.9.20"
    signing
}

kotlin {
    jvmToolchain(11)
}

val ktor_version: String by project
val logback_version: String by project
val exposed_version: String by project
val h2_version: String by project
val hikari_version: String by project

val junit_version: String by project

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

dependencies {
    implementation(project(":kgraphql-ktor"))
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("com.h2database:h2:$h2_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")
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
                    name.set("aPureBase")
                    url.set("http://apurebase.com/")
                }
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/aPureBase/KGraphQL/blob/main/LICENSE.md")
                    }
                }
                developers {
                    developer {
                        id.set("jeggy")
                        name.set("Jógvan Olsen")
                        email.set("jol@apurebase.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/aPureBase/KGraphQL.git")
                    developerConnection.set("scm:git:https://github.com/aPureBase/KGraphQL.git")
                    url.set("https://github.com/aPureBase/KGraphQL/")
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
