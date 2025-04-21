// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://plugins.gradle.org/m2/")
    }
    val agpVersion: String by project
    val kotlinVersion: String by project
    dependencies {
        classpath("com.android.tools.build:gradle:$agpVersion")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlinx:kover:0.6.1")
    }
}

subprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}
