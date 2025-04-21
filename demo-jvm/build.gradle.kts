buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    val remoteKnitVersion: String by project
    dependencies {
        classpath("io.github.tiktok.knit:knit-plugin:$remoteKnitVersion")
    }
}

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "8.3.6"
    application
}

apply(plugin = "io.github.tiktok.knit.plugin")

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":knit"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("knit.demo.MainKt")
}
