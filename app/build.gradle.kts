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
        versionCode = 5
        versionName = "0.3.2"

        // ABI selection is configured only in splits. Do not also set ndk.abiFilters,
        // because Android Gradle Plugin rejects using both filters together.
    }

    // Android refuses to install an unsigned APK ("App not installed"), so release builds are always signed.
    // With a real release key (FOLIA_KEYSTORE_* variables, set from GitHub secrets in CI) the APK is signed
    // with it. Without one, it falls back to the debug key so the APK still installs.
    val releaseKeystorePath: String? = System.getenv("FOLIA_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystorePath != null && file(releaseKeystorePath).exists()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("FOLIA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FOLIA_KEY_ALIAS")
                keyPassword = System.getenv("FOLIA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }

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
    implementation("androidx.core:core-ktx:1.15.0")

    testImplementation("junit:junit:4.13.2")
}
