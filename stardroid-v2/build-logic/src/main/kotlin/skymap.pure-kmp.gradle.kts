import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

// Convention for the PURE modules shared with the iOS port: KMP with a JVM target plus iOS.
// commonMain has neither the Android SDK nor the JDK on its classpath, so `import android.*` and
// `import java.*` are compile errors there (D20). JVM tools (:data:generator, :konsist) stay on
// skymap.pure-kotlin.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(jdkVersion)

    jvm()
    iosArm64()
    iosSimulatorArm64()
    // Intel-Mac simulator; drop when Kotlin drops the x86_64 Apple targets.
    iosX64()

    sourceSets {
        // Truth is JVM-only, so common tests use kotlin.test plus :core:testing's assertions.
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

// Linking and running iOS test binaries needs a full Xcode (Command Line Tools alone fail), so a
// Mac without it skips them loudly rather than failing `check` for Android-only setups.
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
