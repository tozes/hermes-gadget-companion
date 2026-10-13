pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HermesCompanion"

// The app's logic, ported from GadgetKit: plain Kotlin, tested on the JVM.
include(":core")
// The Android app: Compose screens and the Bluetooth LE link.
include(":app")
