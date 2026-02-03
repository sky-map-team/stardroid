# Data Generation

This document describes how astronomical catalog data is converted to the binary format used by Sky Map.

## Overview

Raw astronomical catalogs are processed offline to create compact binary protocol buffer files bundled with the app.

```
Raw Catalogs (CSV, text)
         │
         ▼
    tools/generate.sh
         │
         ▼
ASCII Protocol Buffers (human-readable)
         │
         ▼
    tools/binary.sh
         │
         ▼
Binary Protocol Buffers (compact)
         │
         ▼
app/src/main/assets/*.binary
```

## Tools Module

### Directory Structure

```
tools/
├── build.gradle           # Gradle build config
├── generate.sh            # ASCII generation script
├── binary.sh              # Binary conversion script
├── data/                  # Raw source catalogs
│   ├── stars.csv
│   ├── constellations.txt
│   └── messier.csv
└── src/main/java/
    └── com/google/android/stardroid/data/
        ├── Main.java                    # Entry point
        ├── StellarAsciiProtoWriter.java # Star processor
        ├── MessierAsciiProtoWriter.java # Messier processor
        └── AsciiToBinaryProtoWriter.java # Binary converter
```

### Build Configuration

```groovy
// tools/build.gradle
plugins {
    id 'java'
    id 'application'
}

application {
    mainClass = 'com.google.android.stardroid.data.Main'
}

dependencies {
    implementation project(':datamodel')
    implementation 'com.google.protobuf:protobuf-java:3.24.0'
}
```

## Generation Scripts

### generate.sh

Converts raw catalogs to ASCII protocol buffers:

```bash
#!/bin/bash

# Set up classpath
CLASSPATH="build/classes/java/main:../datamodel/build/classes/java/main"
CLASSPATH="$CLASSPATH:$(find ~/.gradle -name 'protobuf-java*.jar' | head -1)"

# Generate star catalog
java -cp "$CLASSPATH" com.google.android.stardroid.data.Main \
    --type stars \
    --input data/stars.csv \
    --output data/stars.ascii

# Generate constellation catalog
java -cp "$CLASSPATH" com.google.android.stardroid.data.Main \
    --type constellations \
    --input data/constellations.txt \
    --output data/constellations.ascii

# Generate Messier catalog
java -cp "$CLASSPATH" com.google.android.stardroid.data.Main \
    --type messier \
    --input data/messier.csv \
    --output data/messier.ascii
```

### binary.sh

Converts ASCII to binary format:

```bash
#!/bin/bash

CLASSPATH="build/classes/java/main:../datamodel/build/classes/java/main"
CLASSPATH="$CLASSPATH:$(find ~/.gradle -name 'protobuf-java*.jar' | head -1)"

OUTPUT_DIR="../app/src/main/assets"

# Convert each catalog
for catalog in stars constellations messier; do
    java -cp "$CLASSPATH" \
        com.google.android.stardroid.data.AsciiToBinaryProtoWriter \
        --input "data/${catalog}.ascii" \
        --output "${OUTPUT_DIR}/${catalog}.binary"
done

echo "Binary files written to $OUTPUT_DIR"
```

## Converter Classes

### Main Entry Point

```java
public class Main {
    public static void main(String[] args) {
        Options options = parseArgs(args);

        switch (options.type) {
            case "stars":
                new StellarAsciiProtoWriter().process(
                    options.input, options.output);
                break;
            case "messier":
                new MessierAsciiProtoWriter().process(
                    options.input, options.output);
                break;
            case "constellations":
                new ConstellationProtoWriter().process(
                    options.input, options.output);
                break;
        }
    }
}
```

### StellarAsciiProtoWriter

Processes star catalog CSV:

```java
public class StellarAsciiProtoWriter {
    public void process(String inputFile, String outputFile) throws IOException {
        AstronomicalSourcesProto.Builder sources =
            AstronomicalSourcesProto.newBuilder();

        try (BufferedReader reader = new BufferedReader(
                new FileReader(inputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) continue;  // Skip comments

                String[] fields = line.split(",");
                AstronomicalSourceProto star = parseStar(fields);
                sources.addSource(star);
            }
        }

        // Write ASCII protocol buffer
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            TextFormat.print(sources.build(), writer);
        }
    }

    private AstronomicalSourceProto parseStar(String[] fields) {
        // Fields: id, ra, dec, magnitude, name, spectral_type
        float ra = Float.parseFloat(fields[1]);
        float dec = Float.parseFloat(fields[2]);
        float magnitude = Float.parseFloat(fields[3]);
        String name = fields[4];
        String spectralType = fields[5];

        AstronomicalSourceProto.Builder builder =
            AstronomicalSourceProto.newBuilder();

        // Add name if present
        if (!name.isEmpty()) {
            builder.addNames(name);
        }

        // Add point element
        builder.addPoint(PointElementProto.newBuilder()
            .setColor(spectralTypeToColor(spectralType))
            .setSize(magnitudeToSize(magnitude))
            .setLocation(GeocentricCoordinatesProto.newBuilder()
                .setRightAscension(ra)
                .setDeclination(dec))
            .setShape(Shape.CIRCLE));

        // Add label if named star
        if (!name.isEmpty()) {
            builder.addLabel(LabelElementProto.newBuilder()
                .setStringsStringsId(name)
                .setLocation(GeocentricCoordinatesProto.newBuilder()
                    .setRightAscension(ra)
                    .setDeclination(dec)));
        }

        return builder.build();
    }

    private int spectralTypeToColor(String type) {
        switch (type.charAt(0)) {
            case 'O': case 'B': return 0xFFAAAAFF;  // Blue-white
            case 'A': return 0xFFFFFFFF;           // White
            case 'F': return 0xFFFFFFAA;           // Yellow-white
            case 'G': return 0xFFFFFF00;           // Yellow
            case 'K': return 0xFFFFAA00;           // Orange
            case 'M': return 0xFFFF5500;           // Red
            default: return 0xFFFFFFFF;
        }
    }

    private int magnitudeToSize(float magnitude) {
        // Brighter = larger
        return Math.max(1, (int)(6 - magnitude));
    }
}
```

### MessierAsciiProtoWriter

Processes Messier catalog:

```java
public class MessierAsciiProtoWriter {
    public void process(String inputFile, String outputFile) throws IOException {
        AstronomicalSourcesProto.Builder sources =
            AstronomicalSourcesProto.newBuilder();

        try (BufferedReader reader = new BufferedReader(
                new FileReader(inputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) continue;

                String[] fields = line.split(",");
                AstronomicalSourceProto object = parseMessierObject(fields);
                sources.addSource(object);
            }
        }

        try (PrintWriter writer = new PrintWriter(outputFile)) {
            TextFormat.print(sources.build(), writer);
        }
    }

    private AstronomicalSourceProto parseMessierObject(String[] fields) {
        // Fields: messier_number, ra, dec, type, common_name
        String messierNumber = fields[0];
        float ra = Float.parseFloat(fields[1]);
        float dec = Float.parseFloat(fields[2]);
        String type = fields[3];
        String commonName = fields[4];

        AstronomicalSourceProto.Builder builder =
            AstronomicalSourceProto.newBuilder();

        builder.addNames(messierNumber);
        if (!commonName.isEmpty()) {
            builder.addNames(commonName);
        }

        builder.addPoint(PointElementProto.newBuilder()
            .setColor(0xFFAAFFAA)  // Light green
            .setSize(3)
            .setLocation(GeocentricCoordinatesProto.newBuilder()
                .setRightAscension(ra)
                .setDeclination(dec))
            .setShape(typeToShape(type)));

        builder.addLabel(LabelElementProto.newBuilder()
            .setStringsStringsId(messierNumber)
            .setLocation(GeocentricCoordinatesProto.newBuilder()
                .setRightAscension(ra)
                .setDeclination(dec)));

        return builder.build();
    }

    private Shape typeToShape(String type) {
        switch (type.toLowerCase()) {
            case "spiral galaxy": return Shape.SPIRAL_GALAXY;
            case "elliptical galaxy": return Shape.ELLIPTICAL_GALAXY;
            case "globular cluster": return Shape.GLOBULAR_CLUSTER;
            case "open cluster": return Shape.OPEN_CLUSTER;
            case "nebula": return Shape.NEBULA;
            default: return Shape.CIRCLE;
        }
    }
}
```

### AsciiToBinaryProtoWriter

Converts ASCII to binary:

```java
public class AsciiToBinaryProtoWriter {
    public static void main(String[] args) throws IOException {
        String inputFile = args[0];
        String outputFile = args[1];

        // Read ASCII format
        AstronomicalSourcesProto.Builder builder =
            AstronomicalSourcesProto.newBuilder();

        try (FileReader reader = new FileReader(inputFile)) {
            TextFormat.merge(reader, builder);
        }

        // Write binary format
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            builder.build().writeTo(fos);
        }

        System.out.printf("Converted %s -> %s (%d sources)%n",
            inputFile, outputFile, builder.getSourceCount());
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

# Build tools module
if [ "$QUICK" = false ]; then
    echo "Building tools..."
    cd tools
    ../gradlew build

    echo "Generating ASCII catalogs..."
    ./generate.sh

    echo "Converting to binary..."
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
ls -la app/src/main/assets/*.binary

# Check file sizes
du -h app/src/main/assets/*.binary
```

### Validate Protocol Buffers

```bash
# Decode binary to verify content
protoc --decode_raw < app/src/main/assets/stars.binary | head -50
```

## Updating Catalogs

1. Update raw data files in `tools/data/`
2. Run `./build_skymap.sh` (without `--quick`)
3. Verify output in `app/src/main/assets/`
4. Test app with new data
5. Commit changes
