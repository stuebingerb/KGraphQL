plugins {
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1"
    alias(libs.plugins.kotlinx.kover)
    jacoco
}

tasks {
    wrapper {
        distributionType = Wrapper.DistributionType.BIN
    }

    updateDaemonJvm {
        @Suppress("UnstableApiUsage")
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

dependencies {
    kover(project(":kgraphql"))
    kover(project(":kgraphql-ktor"))
    kover(project(":kgraphql-ktor-stitched"))
}
