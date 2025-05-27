import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("kotlin-conventions")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
    id("io.gitlab.arturbosch.detekt")
}

group = "de.stuebingerb"
version = "0.31.0"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
    }
}

dokka {
    dokkaGeneratorIsolation = ProcessIsolation {
        maxHeapSize = "4g"
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    pom {
        name.set("KGraphQL")
        description.set("KGraphQL is a Kotlin implementation of GraphQL. It provides a rich DSL to set up the GraphQL schema.")
        url.set("https://github.com/stuebingerb/KGraphQL/")
        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/license/mit/")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("stuebingerb")
                name.set("Bernd Stübinger")
                email.set("41049452+stuebingerb@users.noreply.github.com")
                url.set("https://github.com/stuebingerb/")
            }
            developer {
                id.set("mervyn-mccreight")
                name.set("Mervyn McCreight")
                email.set("mervyn-mccreight@users.noreply.github.com")
                url.set("https://github.com/mervyn-mccreight/")
            }
        }
        scm {
            url.set("https://github.com/stuebingerb/KGraphQL/")
            connection.set("scm:git:https://github.com/stuebingerb/KGraphQL.git")
            developerConnection.set("scm:git:https://github.com/stuebingerb/KGraphQL.git")
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/config/detekt.yml")
}

// Exclude test fixtures from publication, as we use them only internally
plugins.withId("org.gradle.java-test-fixtures") {
    val component = components["java"] as AdhocComponentWithVariants
    component.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    component.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }

    // Workaround to not publish test fixtures sources added by com.vanniktech.maven.publish plugin
    // TODO: Remove as soon as https://github.com/vanniktech/gradle-maven-publish-plugin/issues/779 is closed
    afterEvaluate {
        component.withVariantsFromConfiguration(configurations["testFixturesSourcesElements"]) { skip() }
    }
}
