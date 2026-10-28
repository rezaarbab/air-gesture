plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ir.airgesture"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.airgesture"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        // The build marker is printed on the diagnostics tab, so a report always says which build ran.
        versionName = "3.0-" + (System.getenv("GITHUB_SHA")?.take(7) ?: "ci")
        ndk {
            // 64-bit only, as asked: MediaPipe also ships armeabi-v7a/x86 libs and they are dead weight.
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("ci") {
            storeFile = rootProject.file("ci-release.jks")
            storePassword = "air-tmp-2026"
            keyAlias = "air"
            keyPassword = "air-tmp-2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val ks = rootProject.file("ci-release.jks")
            if (ks.exists()) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    androidResources {
        // Both .task models are already compressed zip archives; re-deflating them only slows the build.
        noCompress += "task"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("com.google.mediapipe:tasks-vision:0.10.29")
}
