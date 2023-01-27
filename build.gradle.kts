import de.marcphilipp.gradle.nexus.NexusPublishPlugin
import java.time.Duration

val version: String by project
val sonatypeUsername: String? = System.getenv("sonatypeUsername")
val sonatypePassword: String? = System.getenv("sonatypePassword")

plugins {
    id("com.github.ben-manes.versions") version "0.44.0"
    id("io.codearte.nexus-staging") version "0.30.0"
    id("de.marcphilipp.nexus-publish") version "0.4.0"
    jacoco
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    group = "com.apurebase"
    version = version

    apply<NexusPublishPlugin>()

    nexusPublishing {
        repositories {
            sonatype()
        }
        clientTimeout.set(Duration.parse("PT10M")) // 10 minutes
    }
}

nexusStaging {
    packageGroup = "com.apurebase"
    username = sonatypeUsername
    password = sonatypePassword
    numberOfRetries = 360 // 1 hour if 10 seconds delay
    delayBetweenRetriesInMillis = 10000 // 10 seconds
}

tasks {
    wrapper {
        distributionType = Wrapper.DistributionType.ALL
    }
    closeRepository {
        mustRunAfter(subprojects.map { it.tasks.getByName("publishToSonatype") }.toTypedArray())
    }
    closeAndReleaseRepository {
        mustRunAfter(subprojects.map { it.tasks.getByName("publishToSonatype") }.toTypedArray())
    }
}
