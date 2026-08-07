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

// Shared game + protocol code, a peer of both clients at the repo root — neither owns it.
// Pulled in by path, the same way the Android project pulls in :mwa. Nothing is duplicated:
// :core is the single definition of the wire format, so a program change lands in both at once.
include(":core")
project(":core").projectDir = file("../dark-galaxy-core")

// Shared Compose screens — one UI for Android and the browser.
include(":ui")
project(":ui").projectDir = file("../dark-galaxy-core/ui")
