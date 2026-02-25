# Data Generation

This document describes how astronomical catalog data is converted to the binary format used by Stardroid Awakening.

## Overview

Raw astronomical catalogs are processed offline to create compact FlatBuffers binary files bundled with the app.

```
Raw Catalogs (CSV, text)
         │
         ▼
    tools/generate.sh
         │
         ▼
JSON Intermediate (human-readable)
         │
         ▼
    tools/binary.sh (flatc)
         │
         ▼
FlatBuffers Binary (zero-copy)
         │
         ▼
app/src/main/assets/*.bin
```

## Tools Module

### Directory Structure

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

### Build Configuration

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

## Generation Scripts

### generate.sh

Converts raw catalogs to JSON intermediate format:

```bash
#!/bin/bash

# Generate JSON from raw catalogs
./gradlew :tools:run --args="--type stars --input data/stars.csv --output data/stars.json"
./gradlew :tools:run --args="--type constellations --input data/constellations.txt --output data/constellations.json"
./gradlew :tools:run --args="--type messier --input data/messier.csv --output data/messier.json"
```

### binary.sh

Converts JSON to FlatBuffers binary:

```bash
#!/bin/bash

OUTPUT_DIR="../app/src/main/assets"
SCHEMA="../datamodel/src/main/fbs/source.fbs"

# Convert each catalog using flatc
for catalog in stars constellations messier; do
    flatc --binary -o "${OUTPUT_DIR}" "${SCHEMA}" "data/${catalog}.json"
    mv "${OUTPUT_DIR}/data/${catalog}.bin" "${OUTPUT_DIR}/${catalog}.bin"
done

echo "Binary files written to $OUTPUT_DIR"
```

## Converter Classes

### Main Entry Point

```kotlin
fun main(args: Array<String>) {
    val options = parseArgs(args)

    val converter = when (options.type) {
        "stars" -> StellarCatalogConverter()
        "messier" -> MessierCatalogConverter()
        "constellations" -> ConstellationConverter()
        else -> error("Unknown type: ${options.type}")
    }

    converter.process(options.input, options.output)
}
```

### StellarCatalogConverter

Processes star catalog CSV to JSON:

```kotlin
class StellarCatalogConverter {
    fun process(inputFile: String, outputFile: String) {
        val sources = mutableListOf<Map<String, Any>>()

        File(inputFile).useLines { lines ->
            lines.filter { !it.startsWith("#") }
                .forEach { line ->
                    val star = parseStar(line.split(","))
                    sources.add(star)
                }
        }

        val root = mapOf("sources" to sources)
        File(outputFile).writeText(Gson().toJson(root))
    }

    private fun parseStar(fields: List<String>): Map<String, Any> {
        val ra = fields[1].toFloat()
        val dec = fields[2].toFloat()
        val magnitude = fields[3].toFloat()
        val name = fields[4]
        val spectralType = fields[5]

        return buildMap {
            if (name.isNotEmpty()) {
                put("names", listOf(name))
            }
            put("search_location", mapOf(
                "right_ascension" to ra,
                "declination" to dec
            ))
            put("points", listOf(mapOf(
                "color" to spectralTypeToColor(spectralType),
                "size" to magnitudeToSize(magnitude),
                "location" to mapOf(
                    "right_ascension" to ra,
                    "declination" to dec
                ),
                "shape" to "Circle"
            )))
            if (name.isNotEmpty()) {
                put("labels", listOf(mapOf(
                    "text" to name,
                    "location" to mapOf(
                        "right_ascension" to ra,
                        "declination" to dec
                    )
                )))
            }
        }
    }

    private fun spectralTypeToColor(type: String): Long = when (type.firstOrNull()) {
        'O', 'B' -> 0xFFAAAAFFL  // Blue-white
        'A' -> 0xFFFFFFFFL      // White
        'F' -> 0xFFFFFFAAL      // Yellow-white
        'G' -> 0xFFFFFF00L      // Yellow
        'K' -> 0xFFFFAA00L      // Orange
        'M' -> 0xFFFF5500L      // Red
        else -> 0xFFFFFFFFL
    }

    private fun magnitudeToSize(magnitude: Float): Int =
        maxOf(1, (6 - magnitude).toInt())
}
```

### MessierCatalogConverter

Processes Messier catalog:

```kotlin
class MessierCatalogConverter {
    fun process(inputFile: String, outputFile: String) {
        val sources = mutableListOf<Map<String, Any>>()

        File(inputFile).useLines { lines ->
            lines.filter { !it.startsWith("#") }
                .forEach { line ->
                    val obj = parseMessierObject(line.split(","))
                    sources.add(obj)
                }
        }

        val root = mapOf("sources" to sources)
        File(outputFile).writeText(Gson().toJson(root))
    }

    private fun parseMessierObject(fields: List<String>): Map<String, Any> {
        val messierNumber = fields[0]
        val ra = fields[1].toFloat()
        val dec = fields[2].toFloat()
        val type = fields[3]
        val commonName = fields[4]

        val names = mutableListOf(messierNumber)
        if (commonName.isNotEmpty()) names.add(commonName)

        return mapOf(
            "names" to names,
            "search_location" to mapOf(
                "right_ascension" to ra,
                "declination" to dec
            ),
            "points" to listOf(mapOf(
                "color" to 0xFFAAFFAAL,  // Light green
                "size" to 3,
                "location" to mapOf(
                    "right_ascension" to ra,
                    "declination" to dec
                ),
                "shape" to typeToShape(type)
            )),
            "labels" to listOf(mapOf(
                "text" to messierNumber,
                "location" to mapOf(
                    "right_ascension" to ra,
                    "declination" to dec
                )
            ))
        )
    }

    private fun typeToShape(type: String): String = when (type.lowercase()) {
        "spiral galaxy" -> "SpiralGalaxy"
        "elliptical galaxy" -> "EllipticalGalaxy"
        "globular cluster" -> "GlobularCluster"
        "open cluster" -> "OpenCluster"
        "nebula" -> "Nebula"
        else -> "Circle"
    }
}
```

## Full Build Process

### build_skymap.sh

Complete build script:

```bash
#!/bin/bash

set -e

FDROID=false
QUICK=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --fdroid) FDROID=true; shift ;;
        --quick) QUICK=true; shift ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# Build tools module and generate data
if [ "$QUICK" = false ]; then
    echo "Building tools..."
    cd tools
    ../gradlew build

    echo "Generating JSON catalogs..."
    ./generate.sh

    echo "Converting to FlatBuffers binary..."
    ./binary.sh

    cd ..
fi

# Build app
if [ "$FDROID" = true ]; then
    echo "Building F-Droid APK..."
    ./gradlew assembleFdroidDebug
else
    echo "Building GMS APK..."
    ./gradlew assembleGmsDebug
fi

echo "Build complete!"
```

## Verification

### Check Binary Files

```bash
# List assets
ls -la app/src/main/assets/*.bin

# Check file sizes (FlatBuffers are compact)
du -h app/src/main/assets/*.bin
```

### Validate FlatBuffers

```bash
# Decode binary to JSON for verification
flatc --json --raw-binary -o /tmp datamodel/src/main/fbs/source.fbs -- app/src/main/assets/stars.bin
head -50 /tmp/stars.json
```

## Updating Catalogs

1. Update raw data files in `tools/data/`
2. Run `./build_skymap.sh` (without `--quick`)
3. Verify output in `app/src/main/assets/`
4. Test app with new data
5. Commit changes
