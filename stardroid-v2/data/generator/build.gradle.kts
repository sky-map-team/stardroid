plugins {
    id("skymap.pure-kotlin")
}

dependencies {
    // Shares NameNormalizer with :data so generated name_normalized values cannot drift
    // from what the runtime pack writer would compute (D33).
    implementation(project(":core:catalog"))
    implementation(libs.sqlite.jdbc)
    // Used as a JSON DOM only (parseToJsonElement); no @Serializable classes, so the
    // serialization compiler plugin is not needed.
    implementation(libs.kotlinx.serialization.json)
}

// The generator test runs against the real checked-in inputs: the exported Room schema and
// source-data/. Declared as inputs so edits to either re-run the reproducibility gate.
val schemaJson =
    "${rootDir.absolutePath}/data/schemas/com.google.android.stardroid.data.SkyMapDatabase/1.json"
val sourceData = "${rootDir.absolutePath}/source-data"

tasks.withType<Test>().configureEach {
    systemProperty("skymap.schemaJson", schemaJson)
    systemProperty("skymap.sourceData", sourceData)
    inputs.file(schemaJson).withPropertyName("roomSchema")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(sourceData).withPropertyName("sourceData")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// Every catalog name with its JVM-normalized form (NameCorpusExport), for :core:catalog's
// cross-platform NameNormalizer agreement test, whose test tasks depend on this one by path and
// read its output (D126).
tasks.register<JavaExec>("exportNameCorpus") {
    // Locals, so the argument provider captures no script references (configuration cache).
    val sourceDir = sourceData
    val corpus = layout.buildDirectory.file("name-corpus/names.tsv")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.google.android.stardroid.data.generator.NameCorpusExport")
    inputs.dir(sourceDir).withPropertyName("sourceData")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(corpus).withPropertyName("corpus")
    argumentProviders.add(
        CommandLineArgumentProvider { listOf(sourceDir, corpus.get().asFile.absolutePath) },
    )
}
