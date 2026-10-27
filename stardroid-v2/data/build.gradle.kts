import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    id("skymap.kmp-android-library")
    id("skymap.kmp-room")
}

// Multiplatform since phase 1 of the iOS port: the Room database, its DAOs and the repository are
// common code, which iOS opens too. What stays Android-only is in androidMain:
// building the database from the APK asset, and the satellite fetcher (for now).
kotlin {
    android {
        namespace = "com.google.android.stardroid.data"
        // Assets (the bundled catalog, below) are part of Android resource processing, which a
        // multiplatform library leaves off unless asked.
        androidResources { enable = true }
        // Host (JVM) tests are their own tree, apart from commonTest: the common catalog tests need
        // a real SQLite, which a JVM unit test does not have.
        withHostTestBuilder { sourceSetTreeName = "unitTest" }
        // Device tests share the "test" tree, so they run commonTest on Android.
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:catalog"))
            api(project(":core:astronomy"))
            api(project(":core:math"))
            implementation(libs.kotlinx.coroutines.core)
        }
        iosMain.dependencies {
            // iOS opens the catalog with a bundled SQLite, not the system one. D127 prefers the
            // system SQLite, but androidx.sqlite 2.6's NativeSQLiteDriver cannot open any
            // database with it: every open enables extension loading, which iOS's SQLite is
            // built without, and fails with SQLITE_MISUSE. 2.7 fixes that, but it also drops
            // iosX64, the only simulator an Intel Mac runs. Revisit with the upgrade — the
            // driver is one line in the platform code. Never in commonMain: Android keeps the
            // platform SQLite, and the APK stays as it was (D127).
            implementation(libs.androidx.sqlite.bundled)
        }
        // The catalog tests are common, so the same suites run against Android's SQLite (as
        // instrumented tests, androidDeviceTest) and iOS's (on the simulator).
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        // The satellite fetch policy, cache and repository are plain JVM logic - no Room, no
        // SQLite - so they are unit-testable without a device, unlike the catalog below.
        getByName("androidHostTest").dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.truth)
            runtimeOnly(libs.junit.platform.launcher)
        }
        // The catalog tests need a real SQLite (FTS4, Room invalidation), so on Android they run
        // as instrumented tests rather than JVM unit tests — catalog-and-schema.md. All of them
        // are in commonTest; this source set holds only the Android way to open a database.
        getByName("androidDeviceTest").dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
        }
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
                .file("schemas/com.google.android.stardroid.data.SkyMapDatabase/2.json"),
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
    catalogGenerator(project(":data:generator"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// The iOS catalog tests open the generated catalog, found through the environment. The test binary
// runs under `xcrun simctl spawn`, which forwards only SIMCTL_CHILD_-prefixed variables (prefix
// stripped) into the simulated process.
tasks.withType<KotlinNativeTest>().configureEach {
    val catalogDb = generateCatalogDb.flatMap { it.outputDir.file("skymap.db") }
    inputs.file(catalogDb).withPropertyName("catalogDb").withPathSensitivity(PathSensitivity.NONE)
    doFirst {
        environment("SIMCTL_CHILD_SKYMAP_CATALOG_DB", catalogDb.get().asFile.absolutePath)
    }
}
