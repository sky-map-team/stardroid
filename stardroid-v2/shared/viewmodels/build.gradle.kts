plugins {
    id("skymap.kmp-android-library")
}

// The screens' logic, shared with the iOS app: the ViewModels behind the map, search, time travel,
// layers, location, object info, settings, startup, calibration and diagnostics screens, with the
// state types and geometry they compute. They extend androidx.lifecycle's multiplatform ViewModel,
// so they move here unchanged: Android uses them as before, and the iOS UI can hold the same
// classes (D133). The Compose screens that render them stay in :app.
kotlin {
    android {
        namespace = "com.google.android.stardroid.viewmodels"
        // Host (JVM) tests run commonTest, so the ViewModel suites run on the JVM and the iOS
        // simulator alike.
        withHostTestBuilder {}
    }

    // formatRaDec is an expect fun, and stays exactly String.format on Android.
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:layers"))
            api(project(":shared:model"))
            api(project(":data"))
            api(libs.androidx.lifecycle.viewmodel)
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
