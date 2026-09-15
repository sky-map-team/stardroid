import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType

// Convention for PURE Kotlin modules: no Android SDK on the classpath, so `import android.*`
// is a compile error (D20, the structural half of the pure/Android boundary).
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(17)
}

// Pure-module test stack lives here so every module gets it consistently with no per-module
// boilerplate. String-keyed configs because the Kotlin plugin is applied within this same
// convention plugin, so the typed `testImplementation` accessors aren't generated yet here.
dependencies {
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testImplementation"(libs.findLibrary("truth").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
