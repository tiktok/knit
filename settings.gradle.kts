pluginManagement {
    repositories {
        maven { url = uri("${rootDir}/build/artifactLocalPublish") }
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        google()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.tiktok.knit.plugin") {
                useModule("io.github.tiktok.knit:knit-plugin:${requested.version}")
            }
        }
    }
}

rootProject.name = "knit"
// demo
include(":demo-app")
// Allow skipping demo-jvm during plugin publishing to avoid plugin resolution before publish
val skipDemoJvm = providers.gradleProperty("skipDemoJvm").isPresent
if (!skipDemoJvm) {
    include(":demo-jvm")
}

include(":knit")
include(":knit-android")
include(":knit-asm")

// plugins
include(":knit-plugin")
include(":knit-bytex")

includeBuild("build-logic")
