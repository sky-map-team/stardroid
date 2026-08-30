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
include(":render:api")
include(":data:generator")

// Android modules
include(":render:gles1")
include(":data")
include(":app")

// Architecture-enforcement test module (D20)
include(":konsist")
