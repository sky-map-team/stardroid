import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import skymap.strings.ExportAppleStrings

plugins {
    id("skymap.ios-library")
}

// The iOS widgets' Kotlin, built as the SkyMapWidgetKit framework that the ios/SkyMap widget
// extension links (ios-platform-surfaces.md §6.1). The extension is its own process with a tight
// memory limit, so this holds only the shared astronomy and the widget models: no Compose, no
// Metal, no database. :app-ios depends on it too, for the App Group store it writes.
kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "SkyMapWidgetKit"
            isStatic = true
        }
    }
    sourceSets {
        iosMain.dependencies {
            implementation(project(":shared:surfaces"))
        }
    }
}

// The widgets' strings for the extension's bundle, from the same Android-format XML that :app and
// :shared:ui read and tm translates. The Xcode build copies them in (project.yml).
tasks.register<ExportAppleStrings>("exportWidgetStrings") {
    androidResources.from(
        rootProject.layout.projectDirectory.dir("shared/ui/src/commonMain/res"),
        rootProject.layout.projectDirectory.dir("app/src/main/res"),
    )
    keys.set(
        listOf(
            "moon_widget_label",
            "moon_widget_description",
            "moon_widget_illumination",
            "moon_widget_illumination_short",
            "moon_widget_age",
            "moon_widget_rise_time",
            "moon_widget_set_time",
            "moon_widget_time_day_offset",
            "moon_phase_new",
            "moon_phase_waxing_crescent",
            "moon_phase_first_quarter",
            "moon_phase_waxing_gibbous",
            "moon_phase_full",
            "moon_phase_waning_gibbous",
            "moon_phase_last_quarter",
            "moon_phase_waning_crescent",
        ),
    )
    outputDirectory.set(layout.buildDirectory.dir("generated/widgetStrings"))
}
