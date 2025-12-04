plugins {
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
