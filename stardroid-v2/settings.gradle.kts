pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    // Provisions the JDK toolchain (jvmToolchain(17)) if the build JDK differs.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "stardroid-v2"

// Pure modules (no Android) — see docs/design/high-level-architecture.md
include(":core:math")
include(":core:astronomy")
include(":core:catalog")
include(":core:events")
include(":core:testing")
include(":render:api")
include(":render:testscene")
include(":data:generator")

// The app's middle layer, shared with the iOS app (iOS port phase 3): Kotlin Multiplatform, with
// no Android SDK in common code. :shared:layers has an Android target rather than JVM, for :data.
include(":shared:model")
include(":shared:layers")
include(":shared:viewmodels")
include(":shared:testing")
include(":shared:ui")

// iOS-only modules (D128)
include(":render:metal")
include(":render:metal-harness")

// The iOS app's Kotlin side (iOS port phase 4): its framework, its platform edges and its UI root.
include(":app-ios")

// Android modules (:data is Android + iOS)
include(":render:gles1")
include(":render:gles3")
include(":data")
include(":app")

// Architecture-enforcement test module (D20)
include(":konsist")
