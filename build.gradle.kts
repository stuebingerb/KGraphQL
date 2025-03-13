plugins {
    id("com.github.ben-manes.versions") version "0.52.0"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.17.0"
    jacoco
}

tasks {
    wrapper {
        distributionType = Wrapper.DistributionType.BIN
    }
}
