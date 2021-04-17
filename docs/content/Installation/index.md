---
title: Getting Started
weight: 1
---

[![Maven Central](https://img.shields.io/maven-central/v/com.apurebase/kgraphql.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.apurebase%22%20AND%20a:%22kgraphql%22)

KGraphQL is pushed to MavenCentral repository. It requires kotlin compiler version 1.4.x and require kotlin runtime of the same version as a dependency.

=== "Kotlin Gradle Script"
    Add Maven Central repository:
    
    ```kotlin
    repositories {
      mavenCentral()
    }
    ```
    
    Add dependencies:
    
    ```kotlin
    implementation("com.apurebase:kgraphql:$KGraphQLVersion")
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
    implementation 'com.apurebase:kgraphql:${KGraphQLVersion}'
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
      <groupId>com.apurebase</groupId>
      <artifactId>kgraphql</artifactId>
      <version>${KGraphQLVersion}</version>
    </dependency>
    ```
