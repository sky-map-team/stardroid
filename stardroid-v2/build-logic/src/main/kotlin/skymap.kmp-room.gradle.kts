import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Room + KSP for a multiplatform module (Room is multiplatform since 2.7). Apply *after* the
// module's KMP convention (skymap.kmp-android-library) in the same plugins block: the per-target
// KSP configurations below exist only once the targets do.
//
// No SQLite driver is added here. Android sets none, so Room keeps using the platform SQLite
// through sqlite-framework — the same library as before the module went multiplatform, and no
// bundled SQLite in the APK (D127). A module's iOS source set adds the driver it opens with.
plugins {
    id("com.google.devtools.ksp")
    id("androidx.room")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<KotlinMultiplatformExtension> {
    // The database's constructor is an `expect object` whose actuals Room generates per target;
    // expect/actual classes are still Beta, and warn without this.
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
    sourceSets.commonMain.dependencies {
        // `api`: consumers hold and close the returned database, so RoomDatabase must be on
        // their compile classpath.
        api(libs.findLibrary("room-runtime").get())
    }
}

// KSP runs once per target in a multiplatform module, and Room generates each target's
// implementation of the common @Database and @Dao declarations. Matched lazily: KSP creates
// these configurations as the targets' compilations appear, after this script has run.
val roomCompiler = libs.findLibrary("room-compiler").get()
val kspTargetConfigurations =
    setOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64", "kspIosX64")
configurations.matching { it.name in kspTargetConfigurations }.configureEach {
    dependencies.addLater(roomCompiler)
}

extensions.configure<RoomExtension> {
    // Exported schema JSON, checked in per module: the build-time DB generator must produce a
    // database whose identity hash matches this schema, or opening the bundled copy fails. Every
    // target exports the same schema, and the plugin fails the build if two ever disagree.
    schemaDirectory("$projectDir/schemas")
}

extensions.configure<KspExtension> {
    arg("room.generateKotlin", "true")
}
