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

// Every TextId's resource name, which the shared tonight rows are worded from. Read from the enum's
// source so a new TextId can't be left out of the export.
val textIdSource =
    rootProject.layout.projectDirectory.file(
        "core/text/src/commonMain/kotlin/com/google/android/stardroid/text/TextId.kt",
    )
val textIdKeys =
    Regex("""^\s*[A-Z][A-Z0-9_]*\("([^"]+)"""", RegexOption.MULTILINE)
        .findAll(textIdSource.asFile.readText())
        .map { it.groupValues[1] }
        .toList()
        .also { check(it.size > 50) { "Read only ${it.size} TextIds from $textIdSource" } }

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
            "tonight_widget_label",
            "tonight_widget_description",
            "tonight_widget_header",
            "tonight_widget_sun_times",
            "tonight_widget_sunset_only",
            "tonight_quiet",
            "tonight_quiet_next",
            "countdown_widget_label",
            "countdown_widget_description",
            "countdown_days",
            "countdown_tonight",
            "countdown_shower_peak",
            "countdown_new_moon_peak",
            "countdown_full_moon",
            "countdown_new_moon",
        ).plus(textIdKeys).distinct(),
    )
    outputDirectory.set(layout.buildDirectory.dir("generated/widgetStrings"))
}
