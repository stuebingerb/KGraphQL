plugins {
    id("com.github.ben-manes.versions") version "0.52.0"
    jacoco
}

tasks {
    wrapper {
        distributionType = Wrapper.DistributionType.BIN
    }
}
