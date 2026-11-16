pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Official MuPDF Android JNI bindings (com.artifex.mupdf:fitz) are published here
        maven { url = uri("https://maven.ghostscript.com/") }
    }
}

rootProject.name = "Folia"
include(":app")
