plugins {
    id("skymap.kmp-compose")
}

// The screens both apps draw (D134): Compose Multiplatform, with their strings as Compose
// resources in the Android resource format (the same values-*/ folders, so tm translates them
// as it does :app's). Screens move here from :app one at a time, each bringing its strings.
kotlin {
    android {
        namespace = "com.google.android.stardroid.ui.shared"
        // Host (JVM) tests run commonTest, so the suites run on the JVM and the iOS simulator.
        withHostTestBuilder {}
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":render:api"))
            // The ViewModels' state types the screens draw (D133).
            api(project(":shared:viewmodels"))
            // NFD for the help search's accent folding (the catalog's name search uses it too).
            implementation(project(":core:catalog"))
            implementation(libs.cmp.lifecycle.runtime.compose)
            implementation(libs.cmp.material.icons.core)
        }
        androidMain.dependencies {
            // The notification-permission ask (rememberNotificationPermissionRequest).
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            // The location sheet's map (RemoteImage), through the app's ImageLoader.
            implementation(libs.coil.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test-junit5"))
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

compose.resources {
    // Public and under the app's package, so :app's Compose code reads `Res.string.x` from here
    // as it reads `R.string.x` from its own resources.
    publicResClass = true
    packageOfResClass = "com.google.android.stardroid.ui.resources"
    generateResClass = always
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
