import java.util.Properties

plugins {
    id("skymap.android-app")
}

// The Geoapify static-map key rides in app/no-checkin.properties (v1's scheme, git-ignored).
// Without the file (CI, fresh clones) the resource stays "unset" and the location sheet
// falls back to its text-only form.
val secrets =
    Properties().apply {
        val secretsFile = file("no-checkin.properties")
        if (secretsFile.exists()) {
            secretsFile.inputStream().use { load(it) }
        }
    }

android {
    namespace = "com.google.android.stardroid"
    defaultConfig {
        applicationId = "com.google.android.stardroid"
        versionCode = 1727
        versionName = "2.0.1:Apollo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // D95: CI passes -PskipGlBenchmarks=true, which the D19 perf gate reads to skip
        // itself. Set through the DSL rather than
        // -Pandroid.testInstrumentationRunnerArguments.*, which AGP warns is incompatible
        // with configuration caching.
        providers.gradleProperty("skipGlBenchmarks").orNull?.let {
            testInstrumentationRunnerArguments["skipGlBenchmarks"] = it
        }
        resValue(
            "string",
            "geoapify_maps_api_key",
            secrets.getProperty("geoapify.api.key", "unset"),
        )
    }

    // v1's scheme (app/build.gradle): the upload key signs the Play Store bundle,
    // the legacy key signs standalone APKs for distribution outside the Play Store.
    signingConfigs {
        if (secrets.containsKey("upload.keystore.path")) {
            create("releasebundle") {
                storeFile = file(secrets.getProperty("upload.keystore.path"))
                storePassword = secrets.getProperty("upload.store-pwd")
                keyPassword = secrets.getProperty("upload.key-pwd")
                keyAlias = secrets.getProperty("upload.keystore.alias")
            }
        }
        if (secrets.containsKey("apk.keystore.path")) {
            create("releaseapk") {
                storeFile = file(secrets.getProperty("apk.keystore.path"))
                storePassword = secrets.getProperty("apk.store-pwd")
                keyPassword = secrets.getProperty("apk.key-pwd")
                keyAlias = secrets.getProperty("apk.keystore.alias")
            }
        }
    }

    buildTypes {
        release {
            val isBundleTask = gradle.startParameter.taskNames.any { it.contains("bundle") }
            signingConfig =
                if (isBundleTask) {
                    signingConfigs.findByName("releasebundle")
                } else {
                    signingConfigs.findByName("releaseapk")
                }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // v1's flavor pair (D3, D49): gms bundles Play Services (Firebase Analytics/Remote
    // Config, fused location); fdroid is pure open source. The flavor seam is FlavorEdges,
    // one implementation per source set.
    flavorDimensions += "sourciness"
    productFlavors {
        create("gms") {
            dimension = "sourciness"
        }
        create("fdroid") {
            dimension = "sourciness"
        }
    }

    lint {
        // Partial translation is the steady state, not a defect (D72). Locales are filled
        // incrementally by `tm translate` and Android falls back to English per-string, so
        // an untranslated key is a coverage number rather than a build break — `tm
        // languages` reports it precisely. Left as an error this fires once per missing
        // string per locale (356 on the first salvage import) and buries real lint findings.
        warning += "MissingTranslation"
    }
}

dependencies {
    implementation(project(":core:math"))
    implementation(project(":core:astronomy"))
    implementation(project(":core:catalog"))
    implementation(project(":core:events"))
    implementation(project(":render:api"))
    implementation(project(":render:gles1"))
    implementation(project(":data"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    // The location sheet's Geoapify static-map image (flavor-neutral, plain HTTP).
    implementation(libs.coil.compose)
    // The moon-phase home-screen widget and its refresh job (D69).
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)
    // Through-camera (AR) mode's preview stack (camera-ar-mode.md/D64) — Jetpack, so
    // flavor-neutral (no gms/fdroid split needed).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    "gmsImplementation"(platform(libs.firebase.bom))
    "gmsImplementation"(libs.firebase.analytics)
    "gmsImplementation"(libs.firebase.config)
    "gmsImplementation"(libs.play.services.location)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
