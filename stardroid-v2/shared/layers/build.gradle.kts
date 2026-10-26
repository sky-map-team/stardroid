plugins {
    id("skymap.kmp-android-library")
}

// The sky layers: everything that turns the catalog, the ephemeris, the clock and the settings
// into the LayerScenes a renderer draws (layers-and-app.md), shared with the iOS app. Its Android
// side is a multiplatform target rather than JVM because it depends on :data (the satellite
// elements), which is Android + iOS. The one Android-resource piece, ResourceLayerStrings, stays
// in :app behind the LayerStrings interface.
kotlin {
    android {
        namespace = "com.google.android.stardroid.layers"
        // Host (JVM) tests run commonTest, so the layer suites run on the JVM and the iOS
        // simulator alike.
        withHostTestBuilder {}
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:math"))
            api(project(":core:astronomy"))
            api(project(":core:catalog"))
            api(project(":render:api"))
            api(project(":shared:model"))
            // SatelliteLayer draws SatelliteElements.
            api(project(":data"))
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test-junit5"))
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
