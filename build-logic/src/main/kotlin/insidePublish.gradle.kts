// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.util.Properties

val knitVersion: String by project
project.ext["VERSION_NAME"] = knitVersion
val localPublish: Boolean = findProperty("localPublish") == "true"
val mavenArtifactId: String = findProperty("MAVEN_ARTIFACT") as? String ?: project.name
version = knitVersion

apply(plugin = "com.vanniktech.maven.publish")

val publishProperties = Properties()

val publishPropertiesFile = File(System.getProperty("user.home"), ".gradle/publish.properties")

val canPublish = publishPropertiesFile.exists()

publishPropertiesFile.takeIf { canPublish }?.reader()?.use {
    publishProperties.load(it)
}

val projectGitUrl: String by project

if (canPublish) {
    println("need publish! found publish property file")
    ext["signing.keyId"] = publishProperties["signing.keyId"]
    ext["signing.password"] = publishProperties["signing.password"]
    ext["signing.secretKeyRingFile"] = publishProperties["signing.secretKeyRingFile"]
}


project.extensions.configure<PublishingExtension> {
    repositories {
        if (localPublish) maven {
            val buildDir = rootProject.layout.buildDirectory.get().asFile
            val localPublishPath = File(buildDir, "artifactLocalPublish").absolutePath
            name = "projectLocal"
            setUrl("file://$localPublishPath/")
        }
    }
}

val groupName = "io.github.tiktok.knit"
project.group = groupName
project.version = knitVersion

project.extensions.configure<MavenPublishBaseExtension> {
    coordinates(groupName, mavenArtifactId, knitVersion)
    pom {
        name.set("Knit")
        description.set("A zero-intermediation DI framework for Kotlin.")
        inceptionYear.set("2025")
        url.set(projectGitUrl)
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("zsqw123")
                name.set("zsqw123")
                url.set("https://github.com/zsqw123/")
            }
        }
        scm {
            url.set(projectGitUrl)
        }
    }
    signAllPublications()
}

fun setupJvmTarget() {
    project.configure<JavaPluginExtension> {
        targetCompatibility = JavaVersion.VERSION_11
    }
    val isAndroidProject =
        plugins.findPlugin("com.android.application") != null ||
                plugins.findPlugin("com.android.library") != null
    if (isAndroidProject) {
        project.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
                freeCompilerArgs.add("-Xcontext-receivers")
            }
        }
    } else {
        project.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
                freeCompilerArgs.add("-Xcontext-receivers")
            }
        }
    }
}

setupJvmTarget()
