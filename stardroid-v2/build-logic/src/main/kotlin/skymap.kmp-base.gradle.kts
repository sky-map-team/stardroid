import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

// What every Kotlin Multiplatform module shares, whichever targets it adds on top: the iOS
// targets, the commonTest stack, and the rule for Macs without Xcode. Not applied directly —
// modules use skymap.pure-kmp (shared code, plus a JVM target) or skymap.ios-library (iOS only).
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    // Devices, and the simulator on Apple-silicon Macs (including CI's macOS runners).
    iosArm64()
    iosSimulatorArm64()
    // The simulator on Intel Macs. Kotlin is phasing out the x86_64 Apple targets; drop this
    // with them.
    iosX64()

    sourceSets {
        // Tests live in commonTest so the same suites run on every target — for the goldens that
        // is the whole point of sharing the core. They use kotlin.test plus :core:testing's
        // Truth-shaped assertions, since Truth itself is JVM-only.
        commonTest.dependencies {
            implementation(kotlin("test"))
            if (project.path != ":core:testing") implementation(project(":core:testing"))
        }
    }
}

// Compiling iOS klibs needs only the Kotlin/Native toolchain, but linking and running a test
// binary needs a full Xcode — the Command Line Tools alone fail in `xcrun xcodebuild`. So on a
// Mac without Xcode, `check` skips the iOS test binaries (loudly) instead of failing: a machine
// set up only for Android work must still be able to run `check`. CI's macOS job always has
// Xcode and runs them. (Off macOS, the Kotlin plugin disables the Apple targets by itself.)
if (System.getProperty("os.name").startsWith("Mac")) {
    val xcodeInstalled =
        providers
            .exec {
                commandLine("xcrun", "xcodebuild", "-version")
                isIgnoreExitValue = true
            }.result
            .map { it.exitValue == 0 }
    tasks.withType<KotlinNativeLink>().configureEach {
        onlyIf("Xcode is installed") { xcodeInstalled.get() }
    }
    tasks.withType<KotlinNativeTest>().configureEach {
        onlyIf("Xcode is installed") { xcodeInstalled.get() }
    }
    tasks.named("allTests") {
        val modulePath = path
        doFirst {
            if (!xcodeInstalled.get()) {
                logger.warn("$modulePath: iOS tests SKIPPED — Xcode is not installed.")
            }
        }
    }
}
