plugins {
    id("skymap.pure-kmp")
}

// The app's settings contract — what every screen, layer and controller reads and writes — shared
// with the iOS app. Only the interface and its value types live here; the Android implementation
// (DataStoreSettings) stays in :app for now.
kotlin {
    sourceSets {
        commonMain.dependencies {
            // ViewDirectionMode, LatLong and LayerId appear in the interface's signatures.
            api(project(":core:astronomy"))
            api(project(":core:math"))
            api(project(":render:api"))
            api(libs.kotlinx.coroutines.core)
        }
    }
}
