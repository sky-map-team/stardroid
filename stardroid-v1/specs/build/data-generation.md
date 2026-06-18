# Data Generation

This document describes how astronomical catalog data is converted to the binary format used by
Sky Map.

## Overview

Raw astronomical catalogs are processed offline using Java tools to create compact Protocol Buffer
binary files bundled with the app.

```
Raw Catalogs (CSV, text)
         │
         ▼
    tools/Main.java
         │
         ├── StellarAsciiProtoWriter.java   (stars)
         ├── MessierAsciiProtoWriter.java   (Messier objects)
         └── (constellation data)
         │
         ▼
ASCII Protobuf Text (intermediate, human-readable)
         │
         ▼
    AsciiToBinaryProtoWriter.java
         │
         ▼
Binary Protobuf (*.binary)
         │
         ▼
app/src/main/assets/*.binary
```

## Tools Module

### Directory Structure

```
tools/
├── build.gradle                        # Gradle build config (Groovy DSL)
├── data/                               # Raw source catalogs
│   ├── stars.csv                       # Hipparcos star data
│   ├── constellations.txt              # Stellarium constellation lines
│   └── messier.csv                     # Messier deep-sky objects
└── src/main/java/
    └── com/google/android/stardroid/data/
        ├── Main.java                         # Entry point
        ├── StellarAsciiProtoWriter.java      # Star catalog → ASCII protobuf
        ├── MessierAsciiProtoWriter.java      # Messier catalog → ASCII protobuf
        └── AsciiToBinaryProtoWriter.java     # ASCII protobuf → binary protobuf
```

### Build Configuration

```groovy
// tools/build.gradle
plugins {
    id 'java'
    id 'application'
}

dependencies {
    implementation project(':datamodel')
}
```

## Schema

The binary format is defined in `datamodel/src/main/proto/source.proto` (Protocol Buffers proto2).
See [`specs/data/protobuf-schema.md`](../data/protobuf-schema.md) for the full schema.

Key message types:
- `AstronomicalSourceProto` — top-level object (star, planet, deep-sky object)
- `PointElementProto` — renderable point with location, color, size, and shape
- `LabelElementProto` — text label with location
- `LineElementProto` — line connecting two points
- `GeocentricCoordinatesProto` — 3D unit vector on celestial sphere

## Generated Assets

| File | Source | Content |
|------|--------|---------|
| `stars.binary` | Hipparcos catalog | Star positions, magnitudes, colors |
| `constellations.binary` | Stellarium data | Constellation lines and labels |
| `messier.binary` | Messier catalog | Deep-sky objects |

These files live in `app/src/main/assets/` and are loaded at runtime by `AbstractFileBasedLayer`
and deserialized into `ProtobufAstronomicalSource` instances.

## Running Data Generation

Data generation is a prerequisite for building the app when catalog data changes. In practice,
the committed `*.binary` assets are rarely regenerated — only when catalog data or the protobuf
schema changes.

```bash
# Build the tools module
./gradlew :tools:build

# Run Main.java to regenerate data (see Main.java for exact arguments)
./gradlew :tools:run
```

## Updating Catalogs

1. Update raw data files in `tools/data/`
2. Regenerate binary files using the tools module
3. Verify output in `app/src/main/assets/`
4. Test app with new data
5. Commit both the updated source data and the new binary assets

## Future Direction

A proposed migration to FlatBuffers (for zero-copy deserialization) is documented in
[`specs/blueprint/future-data-generation.md`](../blueprint/future-data-generation.md).
