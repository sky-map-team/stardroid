# Future Design: FlatBuffers Data Generation Pipeline

> **STATUS: FUTURE DESIGN — NOT IMPLEMENTED**
>
> This document describes a proposed future migration of the data pipeline from Protocol Buffers
> to FlatBuffers. The current pipeline uses Protocol Buffers and Java tools; see
> [`specs/build/data-generation.md`](../build/data-generation.md) for the actual implementation.

## Motivation

Protocol Buffers require deserialization into Java objects before data can be used, allocating
heap memory proportional to catalog size. FlatBuffers provide zero-copy deserialization — the
binary format is accessed directly in memory, reducing startup time and GC pressure.

## Proposed Pipeline

```
Raw Catalogs (CSV, text)
         │
         ▼
    tools/generate.sh    (Kotlin tools module)
         │
         ▼
JSON Intermediate (human-readable, verifiable)
         │
         ▼
    tools/binary.sh (flatc compiler)
         │
         ▼
FlatBuffers Binary (zero-copy)
         │
         ▼
app/src/main/assets/*.bin
```

## Proposed Tools Module Structure

```
tools/
├── build.gradle.kts       # Gradle build config (Kotlin DSL)
├── generate.sh            # JSON generation script
├── binary.sh              # FlatBuffers binary conversion
├── data/                  # Raw source catalogs
│   ├── stars.csv
│   ├── constellations.txt
│   └── messier.csv
└── src/main/kotlin/
    └── com/stardroid/awakening/tools/
        ├── Main.kt                    # Entry point
        ├── StellarCatalogConverter.kt # Star processor
        ├── MessierCatalogConverter.kt # Messier processor
        └── ConstellationConverter.kt  # Constellation processor
```

## Proposed Build Config

```kotlin
// tools/build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

application {
    mainClass.set("com.stardroid.awakening.tools.MainKt")
}

dependencies {
    implementation(project(":datamodel"))
    implementation("com.google.flatbuffers:flatbuffers-java:24.3.25")
    implementation("com.google.code.gson:gson:2.10.1")
}
```

## Proposed Schema

The `datamodel/` module would switch from `source.proto` to a FlatBuffers schema (`source.fbs`).
The runtime library would switch from `protobuf-javalite` to `flatbuffers-java`.

## Proposed generate.sh

```bash
#!/bin/bash
./gradlew :tools:run --args="--type stars --input data/stars.csv --output data/stars.json"
./gradlew :tools:run --args="--type constellations --input data/constellations.txt --output data/constellations.json"
./gradlew :tools:run --args="--type messier --input data/messier.csv --output data/messier.json"
```

## Proposed binary.sh

```bash
#!/bin/bash
OUTPUT_DIR="../app/src/main/assets"
SCHEMA="../datamodel/src/main/fbs/source.fbs"

for catalog in stars constellations messier; do
    flatc --binary -o "${OUTPUT_DIR}" "${SCHEMA}" "data/${catalog}.json"
    mv "${OUTPUT_DIR}/data/${catalog}.bin" "${OUTPUT_DIR}/${catalog}.bin"
done
```

## Migration Notes

- The `datamodel/` module currently uses `source.proto` (proto2); this would be replaced
  by `source.fbs`
- Generated file extension would change from `*.binary` to `*.bin`
- The app would need to switch from `ProtobufAstronomicalSource` to FlatBuffers-generated accessors
- `AbstractFileBasedLayer` would need updating to read FlatBuffers instead of protobuf
