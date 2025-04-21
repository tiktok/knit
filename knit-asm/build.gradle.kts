plugins {
    kotlin("jvm")
    id("insidePublish")
    id("kover")
}

val asmVersion: String by project
val junitVersion: String by project

dependencies {
    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")
    implementation("org.ow2.asm:asm-analysis:$asmVersion")
    implementation("org.ow2.asm:asm-commons:$asmVersion")

    implementation(kotlin("metadata-jvm"))
    implementation(kotlin("reflect"))

    api(project(":knit"))

    implementation("com.google.guava:guava:28.1-jre")
    implementation("com.google.code.gson:gson:2.8.9")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-launcher")
//    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}

tasks.apply {
    test {
        useJUnitPlatform()
    }
}
