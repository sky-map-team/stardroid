import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import skymap.strings.ConvertAndroidStrings
import skymap.useDatetimeCompat

// Shared Compose Multiplatform UI (D134): screens both apps draw, written once in commonMain with
// their strings as Compose resources. skymap.kmp-android-library plus the Compose compiler and
// Compose Multiplatform's plugin, which generates the module's `Res` accessors and packages its
// resources into the Android app and the iOS framework alike.
//
// The strings are written as Android resources, in `src/commonMain/res` (values*/ folders, the
// same format and escaping as :app's, so tm translates both alike). ConvertAndroidStrings applies
// aapt2's escape and whitespace rules, which Compose's own converter does not, and hands Compose
// the result as the source set's resources.
plugins {
    id("skymap.kmp-android-library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies {
        api(libs.findLibrary("cmp-runtime").get())
        api(libs.findLibrary("cmp-foundation").get())
        api(libs.findLibrary("cmp-ui").get())
        api(libs.findLibrary("cmp-material3").get())
        api(libs.findLibrary("cmp-components-resources").get())
    }
}

val convertAndroidStrings =
    tasks.register<ConvertAndroidStrings>("convertAndroidStrings") {
        androidResources.set(layout.projectDirectory.dir("src/commonMain/res"))
        composeResources.set(layout.buildDirectory.dir("generated/skymap/composeResources/commonMain"))
    }

compose.resources {
    customDirectory("commonMain", convertAndroidStrings.flatMap { it.composeResources })
}

// The iOS configurations only (see skymap.DatetimeCompat): Android stays on kotlinx-datetime 0.6.1.
useDatetimeCompat { it.name.contains("ios", ignoreCase = true) }
