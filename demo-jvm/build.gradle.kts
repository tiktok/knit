import tiktok.knit.plugin.KnitExtension

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "8.3.6"
    id("application")
    id("io.github.tiktok.knit.plugin") version "0.1.5-local"
}

/**
 * Use this to verify whether the knit plugin being run is local
 * or the official published version (look for the -local flag)
 */
println("KNIT_PLUGIN_FROM=" + tiktok.knit.plugin.KnitGradlePlugin::class.java.protectionDomain.codeSource.location)

val knitVersion: String by project
val junitVersion: String by project

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
