plugins {
    id("com.github.ben-manes.versions") version "0.52.0"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1"
    alias(libs.plugins.kotlinx.kover)
    jacoco
}

tasks {
    wrapper {
        distributionType = Wrapper.DistributionType.BIN
    }
}

dependencies {
    kover(project(":kgraphql"))
    kover(project(":kgraphql-ktor"))
    kover(project(":kgraphql-ktor-stitched"))
}
