plugins {
    kotlin("jvm")
    id("com.gradle.plugin-publish") version "1.1.0"
    id("insidePublish")
    id("signing")
}

val projectGitUrl: String by project
val knitVersion: String by project

project.group = "io.github.tiktok.knit"
project.version = knitVersion

gradlePlugin {
    website = projectGitUrl
    vcsUrl = projectGitUrl
    plugins {
        create("Knit") {
            id = "io.github.tiktok.knit.plugin"
            displayName = "TikTok Knit Gradle Plugin"
            description = "A zero-intermediation DI framework for Kotlin."
            tags = listOf("DI", "Kotlin")
            implementationClass = "tiktok.knit.plugin.KnitGradlePlugin"
        }
    }
}

dependencies {
    compileOnly(gradleApi())
    val agpVersion: String by project
    val asmVersion: String by project
    compileOnly("com.android.tools.build:gradle:$agpVersion")
    implementation(project(":knit-asm"))
    implementation("org.ow2.asm:asm-tree:$asmVersion")
}

signing {
    isRequired = false
}
