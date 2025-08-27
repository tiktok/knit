plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "8.3.6"
    id("io.github.tiktok.knit.plugin") version "0.1.5"
    application
}

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
