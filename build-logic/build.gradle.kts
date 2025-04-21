// Copyright (c) 2025 by TikTok Ltd., All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

plugins {
    kotlin("jvm") version "2.1.20"
    `kotlin-dsl`
    id("java-gradle-plugin")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
}

dependencies {
    implementation(gradleApi())
    implementation(kotlin("gradle-plugin"))
    implementation("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.31.0")
    implementation("org.jetbrains.dokka:org.jetbrains.dokka.gradle.plugin:2.0.0")
}


gradlePlugin {
    plugins {
        create("inside") {
            id = "inside"
            implementationClass = "stub.inside.InsidePlugin"
        }
    }
}
