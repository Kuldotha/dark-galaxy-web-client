import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

// NOTE: Kotlin here is 2.0.21 while the Android app is on 2.0.10 — Compose Multiplatform's web
// target needs 2.0.20+. That skew is harmless while this project is standalone, but both builds
// must agree on the Kotlin version before :core can be shared, so the Android app gets bumped to
// match as part of extracting it.

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "darkgalaxy"
        browser {
            commonWebpackConfig {
                outputFileName = "darkgalaxy.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(project(":core"))
            }
        }
    }
}

// The web PDA lookup calls globalThis.solanaWeb3, defined by the vendored IIFE build that lives
// beside the code needing it in :core. Merge that into this app's resources so it ships with the
// distribution instead of having to be copied in by hand.
tasks.named<ProcessResources>("wasmJsProcessResources") {
    from("${rootProject.projectDir}/../dark-galaxy-core/src/wasmJsMain/resources")
}
