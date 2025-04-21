pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

rootProject.name = "knit"
// demo
include(":demo-app")
include(":demo-jvm")

include(":knit")
include(":knit-android")
include(":knit-asm")

// plugins
include(":knit-plugin")
include(":knit-bytex")

includeBuild("build-logic")
