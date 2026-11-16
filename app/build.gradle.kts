plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val mupdfVersion: String by project

android {
    namespace = "com.simbuild.folia"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simbuild.folia"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        // Low space: ship only the ABIs real phones use. Drop armeabi-v7a to save ~40% more.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // One APK per ABI -> each download is only the native code it needs
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
        jniLibs { useLegacyPackaging = false } // keep .so uncompressed in the APK: smaller install, lower RAM
    }
}

dependencies {
    implementation("com.artifex.mupdf:fitz:$mupdfVersion")

    // Compose: only what the app really uses (no material-icons-extended, it is huge)
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")

    testImplementation("junit:junit:4.13.2")
}
