plugins {
    `kotlin-dsl`
}

// Convention plugins compile against the AGP and Kotlin Gradle plugin APIs.
dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
    // On the build-logic classpath (rather than alias'd in module build files) so KSP shares
    // the Kotlin plugin's classloader — otherwise Gradle warns that the Kotlin plugin is
    // loaded multiple times. Modules apply it by bare id: `id("com.google.devtools.ksp")`.
    implementation(libs.ksp.gradlePlugin)
    // Same classloader-sharing rationale as KSP: the Compose compiler plugin must ride the
    // Kotlin plugin's classpath. Applied by bare id in `skymap.android-app`.
    implementation(libs.compose.compiler.gradlePlugin)
    // Hilt's Gradle plugin, applied by bare id in `skymap.android-app` (D59).
    implementation(libs.hilt.gradlePlugin)
}
