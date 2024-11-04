---
title: Getting Started
weight: 1
---

[![Maven Central](https://img.shields.io/maven-central/v/de.stuebingerb/kgraphql.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22de.stuebingerb%22%20AND%20a:%22kgraphql%22)

KGraphQL is pushed to MavenCentral repository. It requires kotlin compiler version 1.4.x and require kotlin runtime of
the same version as a dependency.

=== "Kotlin Gradle Script"
    Add Maven Central repository:

    ```kotlin
    repositories {
      mavenCentral()
    }
    ```
    
    Add dependencies:
    
    ```kotlin
    implementation("de.stuebingerb:kgraphql:$KGraphQLVersion")
    ```
=== "Gradle"
    Add Maven Central repository:

    ```groovy
    repositories {
      mavenCentral()
    }
    ```

    Add dependencies (you can also add other modules that you need):

    ```groovy
    implementation 'de.stuebingerb:kgraphql:${KGraphQLVersion}'
    ```

=== "Maven"
    Add Maven Central repository to section:

    ```xml
    <repositories>
        <repository>
            <id>central</id>
            <url>https://repo1.maven.org/maven2</url>
        </repository>
    </repositories>
    ```
    
    Add dependency:
    
    ```xml
    <dependency>
      <groupId>de.stuebingerb</groupId>
      <artifactId>kgraphql</artifactId>
      <version>${KGraphQLVersion}</version>
    </dependency>
    ```
