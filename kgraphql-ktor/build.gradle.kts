plugins {
    id("library-conventions")
    kotlin("plugin.serialization") version "2.0.21"
    signing
}

val caffeine_version: String by project
val kDataLoader_version: String by project
val kotlin_version: String by project
val serialization_version: String by project
val coroutine_version: String by project
val jackson_version: String by project
val ktor_version: String by project

val netty_version: String by project
val hamcrest_version: String by project
val kluent_version: String by project
val junit_version: String by project

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api(project(":kgraphql"))
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-serialization:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit_version")
    testImplementation("org.amshove.kluent:kluent:$kluent_version")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.ktor:ktor-server-auth:$ktor_version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit_version")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
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
