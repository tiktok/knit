import tiktok.knit.plugin.KnitExtension

// Removed buildscript block and replaced with plugins DSL
plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "8.3.6"
    id("application")
    id("io.github.tiktok.knit.plugin") version "0.1.5-local"
}


// Log where the knit plugin class was loaded from (helps confirm local vs remote)
println("KNIT_PLUGIN_FROM=" + tiktok.knit.plugin.KnitGradlePlugin::class.java.protectionDomain.codeSource.location)


val knitVersion: String by project
val junitVersion: String by project

// Removed apply statement as the plugin is now included in the plugins block

repositories {
    maven { url = uri("${rootDir}/build/artifactLocalPublish") }
    mavenCentral()
}

extensions.getByType<KnitExtension>().apply {
    dependencyTreeOutputPath.set("build/knit.json")
}

dependencies {
    implementation(project(":knit"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("knit.demo.MainKt")
}
