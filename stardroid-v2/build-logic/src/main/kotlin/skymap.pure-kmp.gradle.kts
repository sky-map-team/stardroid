import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType

// Convention for the PURE modules shared with the iOS port: Kotlin Multiplatform with a JVM
// target (what the Android modules and the JVM build tools consume, unchanged) plus the iOS
// targets from skymap.kmp-base. commonMain has neither the Android SDK nor the JDK on its
// classpath, so both `import android.*` and `import java.*` are compile errors there — D20's
// structural boundary, now doubling as the KMP-readiness check build-and-tooling.md deferred as
// "layer 3".
//
// Plain `kotlin("jvm")` modules that are JVM tools rather than shared code (:data:generator,
// :konsist) stay on skymap.pure-kotlin.
plugins {
    id("skymap.kmp-base")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        // kotlin.test runs on JUnit 5 on the JVM (inferred from useJUnitPlatform below).
        jvmTest.dependencies {
            runtimeOnly(libs.findLibrary("junit-platform-launcher").get())
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
