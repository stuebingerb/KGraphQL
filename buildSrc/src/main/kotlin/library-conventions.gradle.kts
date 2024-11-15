import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("kotlin-conventions")
    id("dev.adamko.dokkatoo-html")
    id("com.vanniktech.maven.publish")
}

group = "de.stuebingerb"
version = "0.24.0"

kotlin {
    jvmToolchain(11)
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

dokkatoo {
    dokkaGeneratorIsolation.set(
        ProcessIsolation {
            maxHeapSize.set("4g")
        }
    )
}

mavenPublishing {
    configure(KotlinJvm(JavadocJar.Dokka("dokkatooGeneratePublicationHtml")))
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
