plugins {
    id("skymap.android-library")
    id("skymap.android-room")
}

android {
    namespace = "com.google.android.stardroid.data"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

/**
 * Runs the `:data:generator` main against the exported Room schema and `source-data/`,
 * producing the bundled catalog DB. AGP assigns [outputDir] (via
 * `addGeneratedSourceDirectory`) and packages it as a variant asset, so `skymap.db` is a pure
 * build artifact — never checked in (catalog-and-schema.md).
 */
abstract class GenerateCatalogDbTask : JavaExec() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemaFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceData: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        // Args come from the task's own properties so the provider captures no script
        // references (a configuration-cache requirement).
        argumentProviders.add(
            CommandLineArgumentProvider {
                listOf(
                    schemaFile.get().asFile.absolutePath,
                    sourceData.get().asFile.absolutePath,
                    outputDir.file("skymap.db").get().asFile.absolutePath,
                )
            },
        )
    }
}

val catalogGenerator: Configuration by configurations.creating

val generateCatalogDb =
    tasks.register<GenerateCatalogDbTask>("generateCatalogDb") {
        classpath = catalogGenerator
        mainClass.set("com.google.android.stardroid.data.generator.MainKt")
        schemaFile.set(
            layout.projectDirectory
                .file("schemas/com.google.android.stardroid.data.SkyMapDatabase/1.json"),
        )
        sourceData.set(rootProject.layout.projectDirectory.dir("source-data"))
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            generateCatalogDb,
            GenerateCatalogDbTask::outputDir,
        )
    }
}

dependencies {
    api(project(":core:catalog"))
    // The convention plugin wires room-runtime as `implementation`; consumers of this module
    // hold and close the returned SkyMapDatabase (a RoomDatabase), so its supertype must be
    // on their compile classpath.
    api(libs.room.runtime)
    api(project(":core:astronomy"))
    api(project(":core:math"))

    catalogGenerator(project(":data:generator"))

    // Repository tests need a real SQLite (FTS4, Room invalidation), so they are instrumented
    // (androidTest) against an in-memory DB rather than JVM unit tests — catalog-and-schema.md.
    // The satellite fetch policy, cache and repository are plain JVM logic - no Room, no SQLite -
    // so they are unit-testable without a device, unlike the catalog repository below.
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
