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
