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
        versionCode = 6
        versionName = "0.3.3"

        // ABI selection is configured only in splits. Do not also set ndk.abiFilters,
        // because Android Gradle Plugin rejects using both filters together.
    }

    // Android refuses unsigned APKs ("App not installed"), and it also refuses to update an app when the new
    // APK is signed with a different key. So every release must use the SAME key:
    //   1. a private release key from FOLIA_KEYSTORE_* variables (GitHub secrets), if the maintainer sets one, else
    //   2. the public sideload key committed in signing/ (see signing/README.md), else
    //   3. the local debug key (only if the signing folder was removed).
    // Switching between these changes the signature, and Android then needs a one-time uninstall.
    val releaseKeystorePath: String? = System.getenv("FOLIA_KEYSTORE_PATH")
    val sideloadKeystore = rootProject.file("signing/folia-sideload.jks")
    signingConfigs {
        if (releaseKeystorePath != null && file(releaseKeystorePath).exists()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("FOLIA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FOLIA_KEY_ALIAS")
                keyPassword = System.getenv("FOLIA_KEY_PASSWORD")
            }
        } else if (sideloadKeystore.exists()) {
            create("release") {
                storeFile = sideloadKeystore
                storeType = "pkcs12"
                storePassword = "folia-sideload"
                keyAlias = "folia"
                keyPassword = "folia-sideload"
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
