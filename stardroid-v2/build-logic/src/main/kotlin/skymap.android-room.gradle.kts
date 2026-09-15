import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

// Room + KSP for an Android module. Apply *after* skymap.android-library (or -app) in the same
// plugins block — the dependency declarations below need the android/kotlin configurations to
// exist. Lives in build-logic so KSP shares the Kotlin plugin's classloader; applying KSP by
// version alias in a module build file makes Gradle load the Kotlin plugin twice and warn.
plugins {
    id("com.google.devtools.ksp")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(libs.findLibrary("room-runtime").get())
    "implementation"(libs.findLibrary("room-ktx").get())
    "ksp"(libs.findLibrary("room-compiler").get())
}

extensions.configure<KspExtension> {
    // Exported schema JSON, checked in per module: the 4c build-time DB generator must produce
    // a database whose identity hash matches this schema for createFromAsset validation.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}
