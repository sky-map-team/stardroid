import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    id("skymap.pure-kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:math"))
            api(project(":core:astronomy"))
            // Flow is the repository interface's emission type, so it leaks to consumers — hence
            // `api`.
            api(libs.kotlinx.coroutines.core)
        }
    }
}

// NameNormalizerCorpusTest re-normalizes every catalog name on each target and compares the
// result with the JVM's, exported from source-data/ by :data:generator (D126). Every test task
// runs the export first and gets the file's path in the environment.
val nameCorpus =
    rootProject.layout.projectDirectory.file("data/generator/build/name-corpus/names.tsv")

fun Task.useNameCorpus() {
    dependsOn(":data:generator:exportNameCorpus")
    inputs.file(nameCorpus).withPropertyName("nameCorpus")
        .withPathSensitivity(PathSensitivity.NONE)
}

tasks.withType<Test>().configureEach {
    useNameCorpus()
    environment("SKYMAP_NAME_CORPUS", nameCorpus.asFile.absolutePath)
}

tasks.withType<KotlinNativeTest>().configureEach {
    useNameCorpus()
    // `xcrun simctl spawn` forwards only SIMCTL_CHILD_-prefixed variables (prefix stripped).
    environment("SIMCTL_CHILD_SKYMAP_NAME_CORPUS", nameCorpus.asFile.absolutePath)
}
