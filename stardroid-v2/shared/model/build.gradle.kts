plugins {
    id("skymap.pure-kmp")
}

// The app's state and controllers, shared with the iOS app: the contracts every screen, layer
// and controller programs against (Settings, StartupState, ExperimentConfig, Analytics, the
// location and geocoding edges) and the platform-free controllers over them (TimeController and
// its clocks, LocationController, StartupRouter). The Android implementations of the contracts —
// DataStore, Firebase, the platform location and geocoder services — stay in :app.
kotlin {
    // ReentrantLock is an expect class (the clocks' @Synchronized, made multiplatform); expect/
    // actual classes are still Beta, and warn without this.
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    sourceSets {
        commonMain.dependencies {
            // ViewDirectionMode, LatLong, LayerId and Instant appear in the contracts' signatures.
            api(project(":core:astronomy"))
            api(project(":core:math"))
            api(project(":render:api"))
            api(libs.kotlinx.coroutines.core)
            // DataStoreSettings and DataStoreStartupState. Multiplatform since DataStore 1.1, and
            // already what Android ships (under datastore-preferences), so no new library there.
            api(libs.androidx.datastore.preferences.core)
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
