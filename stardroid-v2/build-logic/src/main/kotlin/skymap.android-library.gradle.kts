import com.android.build.api.dsl.LibraryExtension

// Kotlin support comes from AGP's built-in Kotlin (AGP 9+) rather than a separate
// org.jetbrains.kotlin.android plugin application - see skymap.android-app for why.
plugins {
    id("com.android.library")
    id("org.jlleitschuh.gradle.ktlint")
}

extensions.configure<LibraryExtension> {
    compileSdk = 36
    defaultConfig {
        // See skymap.android-app.gradle.kts: 28 is best-effort (D9, revised), not a supported
        // floor. Code may assume API 29+ without guards.
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
