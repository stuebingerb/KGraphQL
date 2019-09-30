---
title: Getting Started
weight: 2
---

[![Bintray](https://api.bintray.com/packages/apurebase/apurebase/kgraphql/images/download.svg)](https://bintray.com/apurebase/apurebase/kgraphql)

KGraphQL is pushed to bintray repository and also linked to JCenter. It requires kotlin compiler version 1.3.x and require kotlin runtime of the same version as a dependency.


## Gradle
Add Bintray JCenter repository:

```groovy
repositories {
  jcenter()
}
```

Add dependencies (you can also add other modules that you need):

```groovy
compile 'com.apurebase:kgraphql:${KGraphQLVersion}'
```

And make sure that you use the right Kotlin version:

```groovy
buildscript {
  ext.kotlin_version = '1.3.50'
}
```

## Maven
Add Bintray JCenter repository to section:

```xml
<repositories>
  <repository>
    <id>jcenter</id>
    <url>https://jcenter.bintray.com/</url>
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

```xml
<properties>
  <kotlin.version>1.3.50</kotlin.version>
</properties>
```
