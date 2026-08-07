pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "DarkGalaxyWeb"

// Shared code lives in the Android project and is pulled in by path, the same way that project
// pulls in :mwa. Nothing is duplicated — :core is the single definition of the wire format, so a
// program change lands in both clients at once.
//
// Commented out until :core is extracted from :app into its own module (migration step 2).
// include(":core")
// project(":core").projectDir = file("../dark-galaxy-android/core")
