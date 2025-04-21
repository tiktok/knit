plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("insidePublish")
}

val bytexVersion: String by project
val junitVersion: String by project

repositories {
    maven("https://artifact.bytedance.com/repository/byteX/")
}

dependencies {
    compileOnly(gradleApi())
    // bytex need 4.1.0 to work :(
    compileOnly("com.android.tools.build:gradle:4.1.0")
    implementation("com.bytedance.android.byteX:common:$bytexVersion")
    kapt("com.bytedance.android.byteX:PluginConfigProcessor:$bytexVersion")
    implementation("com.bytedance.android.byteX:PluginConfigProcessor:$bytexVersion")
    implementation(project(":knit-asm"))

    // unit test
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
}
