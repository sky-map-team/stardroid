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
    // Compose Multiplatform's plugin, for the shared UI's resources (generated Res accessors,
    // packaged for both apps). Applied by bare id in `skymap.kmp-compose`.
    implementation(libs.compose.multiplatform.gradlePlugin)
    // Hilt's Gradle plugin, applied by bare id in `skymap.android-app` (D59).
    implementation(libs.hilt.gradlePlugin)
    // Room's Gradle plugin (schema export for every KMP target), applied by bare id in
    // `skymap.kmp-room`.
    implementation(libs.room.gradlePlugin)

    // The shared strings' converter (skymap.strings) is the only build logic with tests.
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
