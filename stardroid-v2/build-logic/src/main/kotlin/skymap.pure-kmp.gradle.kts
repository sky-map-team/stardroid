import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

// Convention for the PURE modules shared with the iOS port: Kotlin Multiplatform with a JVM
// target (what the Android modules and the JVM build tools consume, unchanged) plus the iOS
// targets. commonMain has neither the Android SDK nor the JDK on its classpath, so both
// `import android.*` and `import java.*` are compile errors there — D20's structural boundary,
// now doubling as the KMP-readiness check build-and-tooling.md deferred as "layer 3".
//
// Plain `kotlin("jvm")` modules that are JVM tools rather than shared code (:data:generator,
// :konsist) stay on skymap.pure-kotlin.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(17)

    jvm()
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
        // kotlin.test runs on JUnit 5 on the JVM (inferred from useJUnitPlatform below).
        jvmTest.dependencies {
            runtimeOnly(libs.findLibrary("junit-platform-launcher").get())
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
