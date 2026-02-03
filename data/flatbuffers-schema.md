# FlatBuffers Schema

Sky Map uses FlatBuffers for efficient serialization of astronomical data. FlatBuffers provides zero-copy deserialization, meaning data can be accessed directly from the binary buffer without parsing overhead.

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
