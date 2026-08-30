import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

// The Android + Kotlin + Compose + Hilt baseline for the app module (see
// docs/design/build-and-tooling.md). Hilt and KSP ride the build-logic classpath
// and are applied by bare id, sharing the Kotlin plugin's classloader like the
// Room convention does.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(libs.findLibrary("hilt-android").get())
    "ksp"(libs.findLibrary("hilt-compiler").get())
}

extensions.configure<ApplicationExtension> {
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        targetSdk = 36
    }
    buildFeatures {
        compose = true
        // The codebase's first BuildConfig use (D92): the satellite force-fetch in Diagnostics is
        // gated on BuildConfig.DEBUG, so it cannot ship by accident. A discoverable "fetch now" in
        // release would be the retry storm the circuit breaker exists to prevent, and a build-type
        // gate is the only kind of gate that cannot be reached by an ordinary user.
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

extensions.configure<KotlinAndroidProjectExtension> {
    jvmToolchain(17)
}
