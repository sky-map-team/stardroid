# Data Serialization Schema

> **NOTE — FUTURE vs CURRENT STATE**
>
> The body of this document describes a _possible planned_ migration to **FlatBuffers** serialization that has not been implemented. The current codebase uses **Protocol Buffers** (protobuf2), not FlatBuffers.
>
> See the **Current Implementation** section below for the real schema.

---

## Current Implementation: Protocol Buffers

Sky Map uses Protocol Buffers (proto2) for serializing astronomical data. The schema is defined in `datamodel/src/main/proto/source.proto` and the generated Java classes live in the `com.google.android.stardroid.source.proto` package.

### Current Schema (`source.proto`)

```proto
syntax = "proto2";
package stardroid_source;
option java_package = "com.google.android.stardroid.source.proto";
option java_outer_classname = "SourceProto";

enum Shape {
  CIRCLE = 0;
  STAR = 1;
  OPEN_CLUSTER = 2;
  GLOBULAR_CLUSTER = 3;
  DIFFUSE_NEBULA = 4;
  PLANETARY_NEBULA = 5;
  SUPERNOVA_REMNANT = 6;
  GALAXY = 7;
  OTHER = 8;
}

message GeocentricCoordinatesProto {
  optional float right_ascension = 1;
  optional float declination = 2;
}

message PointElementProto {
  optional GeocentricCoordinatesProto location = 1;
  optional uint32 color = 2 [default = 0xFFFFFFFF];
  optional int32 size = 3 [default = 3];
  optional Shape shape = 4 [default = CIRCLE];
}

message LabelElementProto {
  optional GeocentricCoordinatesProto location = 1;
  optional uint32 color = 2 [default = 0xFFFFFFFF];
  optional int32 strings_int_id = 3;
  optional string strings_str_id = 6;
  optional int32 font_size = 4 [default = 15];
  optional float offset = 5 [default = 0.02];
}

message LineElementProto {
  optional uint32 color = 1 [default = 0xFFFFFFFF];
  optional float line_width = 2 [default = 1.5];
  repeated GeocentricCoordinatesProto vertex = 3;
}

message AstronomicalSourceProto {
  repeated uint32 name_int_ids = 1;
  repeated string name_str_ids = 8;
  optional GeocentricCoordinatesProto search_location = 2;
  optional float search_level = 3 [default = 0.0];
  optional float level = 4 [default = 0.0];
  repeated PointElementProto point = 5;
  repeated LabelElementProto label = 6;
  repeated LineElementProto line = 7;
}

message AstronomicalSourcesProto {
  repeated AstronomicalSourceProto source = 1;
}
```

### Data Generation Pipeline

```
Raw catalogs → tools/Main.java → ASCII protobuf text → binary protobuf → app/src/main/assets/
                (StellarAsciiProtoWriter)               (AsciiToBinaryProtoWriter)
                (MessierAsciiProtoWriter)
```

---

## Planned Future State: FlatBuffers

The rest of this document describes a proposed migration to FlatBuffers. FlatBuffers would provide zero-copy deserialization, meaning data could be accessed directly from the binary buffer without parsing overhead.

### Why FlatBuffers? (Proposed rationale)

| Feature | FlatBuffers | Protocol Buffers |
|---------|-------------|------------------|
| Deserialization | Zero-copy (instant) | Requires parsing |
| Memory | Direct access to buffer | Creates new objects |
| Speed | O(1) field access | O(n) parsing |
| Mutability | Read-only by default | Mutable objects |
| Streaming | Random access | Sequential |

**Key motivation:** For 100k+ stars, FlatBuffers allows instant access without allocating memory for each star object.

## Why FlatBuffers?

| Feature | FlatBuffers | Protocol Buffers |
|---------|-------------|------------------|
| Deserialization | Zero-copy (instant) | Requires parsing |
| Memory | Direct access to buffer | Creates new objects |
| Speed | O(1) field access | O(n) parsing |
| Mutability | Read-only by default | Mutable objects |
| Streaming | Random access | Sequential |

**Key Advantage:** For 100k+ stars, FlatBuffers allows instant access without allocating memory for each star object.

## Schema Location

```
datamodel/src/main/fbs/source.fbs
```

## Schema Definition

### Namespace and Includes

```fbs
namespace com.stardroid.awakening.data;

// File identifier for validation
file_identifier "STAR";
file_extension "bin";
```

### Geocentric Coordinates

Position on the celestial sphere:

```fbs
struct GeocentricCoordinates {
    // Right ascension in degrees (0-360)
    right_ascension: float;

    // Declination in degrees (-90 to +90)
    declination: float;
}
```

### Shape Enum

```fbs
enum Shape : byte {
    Circle = 0,
    Star = 1,
    EllipticalGalaxy = 2,
    SpiralGalaxy = 3,
    IrregularGalaxy = 4,
    LenticularGalaxy = 5,
    GlobularCluster = 6,
    OpenCluster = 7,
    Nebula = 8,
    HubbleDeepField = 9
}
```

### Point Element

Stars, planets, and point-like objects:

```fbs
table PointElement {
    // Color in ARGB format
    color: uint32;

    // Display size (related to magnitude)
    size: int32;

    // Position on celestial sphere
    location: GeocentricCoordinates (required);

    // Shape for rendering
    shape: Shape = Circle;
}
```

### Label Element

Text labels for object names:

```fbs
table LabelElement {
    // Label text (or string resource ID)
    text: string (required);

    // Position on celestial sphere
    location: GeocentricCoordinates (required);

    // Color in ARGB format
    color: uint32;

    // Text offset from point (pixels)
    offset: int32;

    // Font size enum
    font_size: int32;
}
```

### Line Element

Lines connecting points (constellation lines, grid lines):

```fbs
table LineElement {
    // Color in ARGB format
    color: uint32;

    // Line width in pixels
    line_width: float = 1.5;

    // Vertices defining the line strip
    vertices: [GeocentricCoordinates] (required);
}
```

### Astronomical Source

Represents a single astronomical object (star, constellation, galaxy, etc.):

```fbs
table AstronomicalSource {
    // Searchable names for this object
    names: [string];

    // Search location (center point for search targeting)
    search_location: GeocentricCoordinates;

    // Level of detail for visibility filtering
    level: int32;

    // Visual elements
    points: [PointElement];
    labels: [LabelElement];
    lines: [LineElement];
}
```

### Root Table

```fbs
table AstronomicalSources {
    sources: [AstronomicalSource];
}

root_type AstronomicalSources;
```

## Complete Schema

```fbs
// source.fbs - Astronomical data schema for Stardroid Awakening

namespace com.stardroid.awakening.data;

file_identifier "STAR";
file_extension "bin";

enum Shape : byte {
    Circle = 0,
    Star = 1,
    EllipticalGalaxy = 2,
    SpiralGalaxy = 3,
    IrregularGalaxy = 4,
    LenticularGalaxy = 5,
    GlobularCluster = 6,
    OpenCluster = 7,
    Nebula = 8,
    HubbleDeepField = 9
}

// Struct: fixed size, inline storage (8 bytes)
struct GeocentricCoordinates {
    right_ascension: float;
    declination: float;
}

table PointElement {
    color: uint32;
    size: int32;
    location: GeocentricCoordinates (required);
    shape: Shape = Circle;
}

table LabelElement {
    text: string (required);
    location: GeocentricCoordinates (required);
    color: uint32;
    offset: int32;
    font_size: int32;
}

table LineElement {
    color: uint32;
    line_width: float = 1.5;
    vertices: [GeocentricCoordinates] (required);
}

table AstronomicalSource {
    names: [string];
    search_location: GeocentricCoordinates;
    level: int32;
    points: [PointElement];
    labels: [LabelElement];
    lines: [LineElement];
}

table AstronomicalSources {
    sources: [AstronomicalSource];
}

root_type AstronomicalSources;
```

## Binary Format

### Wire Format

FlatBuffers uses a direct-access binary format:

| Component | Description |
|-----------|-------------|
| Root offset | 4-byte offset to root table |
| vtable | Field offsets for tables |
| Tables | Offset-based field access |
| Structs | Inline, fixed-size data |
| Vectors | Length-prefixed arrays |
| Strings | Null-terminated UTF-8 |

### Memory Layout

```
+------------------------------------------+
|  File Header (4 bytes)                   |
|  - Root table offset                     |
+------------------------------------------+
|  Root Table: AstronomicalSources         |
|  - vtable offset                         |
|  - sources vector offset                 |
+------------------------------------------+
|  Sources Vector                          |
|  - length (4 bytes)                      |
|  - offsets to each AstronomicalSource    |
+------------------------------------------+
|  AstronomicalSource[0]                   |
|  - names vector offset                   |
|  - search_location (inline struct)       |
|  - points vector offset                  |
|  - ...                                   |
+------------------------------------------+
|  ...more sources...                      |
+------------------------------------------+
```

### File Structure Example

```
AstronomicalSources
|-- sources[0]: AstronomicalSource
|   |-- names: ["Sirius", "Alpha CMa"]
|   |-- search_location: {ra: 101.3, dec: -16.7}
|   |-- points[0]: {color: 0xFFFFFFFF, size: 5, location: {...}}
|   +-- labels[0]: {text: "Sirius", location: {...}}
|-- sources[1]: AstronomicalSource
|   +-- ...
+-- sources[N]: ...
```

## Data Generation

### JSON Format (Intermediate)

Human-readable format for debugging and editing:

```json
{
  "sources": [
    {
      "names": ["Sirius", "Alpha Canis Majoris"],
      "search_location": {
        "right_ascension": 101.287,
        "declination": -16.716
      },
      "points": [
        {
          "color": 4294967295,
          "size": 5,
          "location": {
            "right_ascension": 101.287,
            "declination": -16.716
          },
          "shape": "Circle"
        }
      ],
      "labels": [
        {
          "text": "Sirius",
          "location": {
            "right_ascension": 101.287,
            "declination": -16.716
          }
        }
      ]
    }
  ]
}
```

### Conversion Pipeline

```kotlin
// JSON to Binary converter using flatc
class JsonToBinaryConverter {
    fun convert(jsonFile: String, outputFile: String) {
        // Use flatc compiler
        ProcessBuilder(
            "flatc", "--binary",
            "-o", outputFile,
            "source.fbs",
            jsonFile
        ).start().waitFor()
    }
}

// Programmatic builder
class CatalogBuilder {
    fun build(stars: List<StarData>): ByteArray {
        val builder = FlatBufferBuilder(1024 * 1024)  // 1MB initial

        val sourceOffsets = stars.map { star ->
            val nameOffsets = star.names.map { builder.createString(it) }
            val namesVector = AstronomicalSource.createNamesVector(builder, nameOffsets.toIntArray())

            val pointOffsets = star.points.map { point ->
                PointElement.createPointElement(
                    builder,
                    point.color,
                    point.size,
                    point.ra,
                    point.dec,
                    point.shape
                )
            }
            val pointsVector = AstronomicalSource.createPointsVector(builder, pointOffsets.toIntArray())

            AstronomicalSource.createAstronomicalSource(
                builder,
                namesVector,
                star.ra, star.dec,
                star.level,
                pointsVector,
                0,  // no labels
                0   // no lines
            )
        }

        val sourcesVector = AstronomicalSources.createSourcesVector(builder, sourceOffsets.toIntArray())
        val root = AstronomicalSources.createAstronomicalSources(builder, sourcesVector)
        builder.finish(root)

        return builder.sizedByteArray()
    }
}
```

## Runtime Usage

### Loading Data (Zero-Copy)

```kotlin
class AbstractFileBasedLayer {
    protected fun loadData(filename: String): AstronomicalSources {
        val assets = context.assets
        val stream = assets.open(filename)

        // Read entire buffer (or memory-map for large files)
        val bytes = stream.readBytes()
        val buffer = ByteBuffer.wrap(bytes)

        // Zero-copy access - no parsing!
        return AstronomicalSources.getRootAsAstronomicalSources(buffer)
    }
}
```

### Accessing Fields (Direct Memory Access)

```kotlin
class FlatBufferAstronomicalSource(
    private val source: AstronomicalSource
) : AstronomicalSourceInterface {

    override fun getPoints(): List<PointPrimitive> {
        return (0 until source.pointsLength).map { i ->
            val p = source.points(i)!!
            PointPrimitive(
                GeocentricCoordinates(
                    p.location().rightAscension(),
                    p.location().declination()
                ),
                p.color().toInt(),
                p.size(),
                Shape.fromFlatBuffer(p.shape())
            )
        }
    }

    override fun getNames(): List<String> {
        return (0 until source.namesLength).map { source.names(it)!! }
    }

    // Direct field access - no intermediate objects created
    val searchRA: Float get() = source.searchLocation()?.rightAscension() ?: 0f
    val searchDec: Float get() = source.searchLocation()?.declination() ?: 0f
}
```

### Memory-Mapped Access (Large Files)

For very large catalogs, use memory-mapped files:

```kotlin
class MemoryMappedCatalog(private val file: File) {
    private val channel: FileChannel = RandomAccessFile(file, "r").channel
    private val buffer: MappedByteBuffer = channel.map(
        FileChannel.MapMode.READ_ONLY, 0, file.length()
    )

    val sources: AstronomicalSources =
        AstronomicalSources.getRootAsAstronomicalSources(buffer)

    fun close() {
        channel.close()
    }
}
```

## Size Optimization

### Compression Comparison

| Format | stars.* Size | Notes |
|--------|--------------|-------|
| JSON | ~15 MB | Human-readable |
| FlatBuffers | ~2 MB | Binary, zero-copy |
| FlatBuffers + zstd | ~800 KB | Compressed for storage |

### Size Breakdown (Binary)

| Field | Contribution |
|-------|--------------|
| Coordinates (structs) | ~60% |
| Colors (uint32) | ~15% |
| Sizes (int32) | ~10% |
| Names (strings) | ~10% |
| Structure overhead | ~5% |

### Optimization Tips

1. **Use structs for coordinates** - Inline storage, no offset overhead
2. **Share strings** - FlatBuffers deduplicates identical strings
3. **Order fields by size** - Reduces padding
4. **Use appropriate types** - `byte` for enums, not `int32`

## Gradle Configuration

### datamodel/build.gradle.kts

```kotlin
plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("com.google.flatbuffers:flatbuffers-java:24.3.25")
}

// FlatBuffers code generation task
tasks.register<Exec>("generateFlatBuffers") {
    commandLine(
        "flatc",
        "--kotlin",
        "-o", "src/main/kotlin",
        "src/main/fbs/source.fbs"
    )
}

tasks.named("compileKotlin") {
    dependsOn("generateFlatBuffers")
}
```

### app/build.gradle.kts

```kotlin
dependencies {
    implementation(project(":datamodel"))
    implementation("com.google.flatbuffers:flatbuffers-java:24.3.25")
}
```

## Performance Benchmarks

### Loading 100k Stars

| Operation | Protocol Buffers | FlatBuffers |
|-----------|-----------------|-------------|
| Parse time | ~200ms | ~0ms (zero-copy) |
| Memory allocation | ~10MB objects | 0 (buffer reuse) |
| First star access | After full parse | Instant |
| Random access | O(1) after parse | O(1) always |

### Memory Usage

| Metric | Protocol Buffers | FlatBuffers |
|--------|-----------------|-------------|
| File buffer | Discarded | Retained |
| Object graph | Created | None |
| Total heap | ~15MB | ~2MB (buffer only) |

## Key Files

| File | Purpose |
|------|---------|
| `source.fbs` | Schema definition |
| `AstronomicalSources.kt` | Generated Kotlin accessors |
| `JsonToBinaryConverter.kt` | Format converter |
| `FlatBufferAstronomicalSource.kt` | Runtime wrapper |
