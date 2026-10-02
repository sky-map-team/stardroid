// skymap.ios-library plus the Compose compiler, for iOS-only modules written in Compose
// Multiplatform, the iOS app's UI layer (D134): :app-ios. The compiler
// plugin rides build-logic's classpath (as for skymap.android-app), so modules cannot request
// it by id themselves.
plugins {
    id("skymap.ios-library")
    id("org.jetbrains.kotlin.plugin.compose")
}
