import com.vanniktech.maven.publish.SonatypeHost

val version: String by project
val sonatypeUsername: String? = System.getenv("sonatypeUsername")
val sonatypePassword: String? = System.getenv("sonatypePassword")

plugins {
    id("com.github.ben-manes.versions") version "0.51.0"
    id("com.vanniktech.maven.publish") version "0.29.0"
    kotlin("jvm") version "2.0.21"
    jacoco
}

allprojects {
    group = "de.stuebingerb"
    version = version
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "com.vanniktech.maven.publish")
    mavenPublishing {
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        signAllPublications()
    }
}

tasks {
    wrapper {
        distributionType = Wrapper.DistributionType.ALL
    }
}
