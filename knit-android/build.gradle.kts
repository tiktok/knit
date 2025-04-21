plugins {
    id("com.android.library")
    kotlin("android")
    id("insidePublish")
}

android {
    namespace = "tiktok.knit.android"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    api(project(":knit"))
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.5.1")
    implementation("androidx.fragment:fragment:1.2.0")
}

