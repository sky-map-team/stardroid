# Data Flow

This document describes how data flows through Sky Map, from raw astronomical catalogs to rendered pixels.

## Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           BUILD TIME                                      │
│  Raw Catalogs ─► JSON Intermediate ─► FlatBuffers Binary ─► App Assets   │
└──────────────────────────────────────────────────────────────────────────┘
                                                              │
                                                              ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                           RUNTIME                                         │
│  Assets ─► Layers ─► AstronomicalSource ─► Primitives ─► OpenGL          │
└──────────────────────────────────────────────────────────────────────────┘
```

## Build-Time Data Pipeline

### 1. Raw Astronomical Catalogs

Source data in various formats:
- **Hipparcos Catalog**: Star positions, magnitudes, spectral types
- **Stellarium Data**: Constellation lines and names
- **Messier Catalog**: Deep-sky object positions and types

### 2. JSON Intermediate Generation

`tools/generate.sh` runs data converters:

```bash
./gradlew :tools:run --args="--input stellar_catalog.csv --output stars.json"
```

**Converter Classes (Kotlin)**:
- `StellarCatalogConverter` - Processes star catalogs
- `MessierCatalogConverter` - Processes Messier objects
- `ConstellationConverter` - Processes constellation data

### 3. FlatBuffers Binary Conversion

`tools/binary.sh` converts JSON to FlatBuffers binary:

```bash
flatc --binary -o app/src/main/assets/ \
    datamodel/src/main/fbs/source.fbs \
    tools/data/stars.json
```

**Output Location**: `app/src/main/assets/`

### 4. Final Assets

| Asset File | Size | Contents |
|------------|------|----------|
| `stars.binary` | ~2MB | Star positions, colors, magnitudes |
| `constellations.binary` | ~50KB | Lines and labels |
| `messier.binary` | ~20KB | Deep-sky objects |

## Runtime Data Flow

### 1. Asset Loading

`AbstractFileBasedLayer` loads FlatBuffers on first access (zero-copy):

```kotlin
override fun initialize() {
    val bytes = context.assets.open(assetFilename).use { it.readBytes() }
    val buffer = ByteBuffer.wrap(bytes)
    val data = AstronomicalSources.getRootAsAstronomicalSources(buffer)
    sources = (0 until data.sourcesLength).map { i ->
        FlatBufferAstronomicalSource(data.sources(i)!!)
    }
}
```

### 2. Layer Registration

Layers register their sources with the renderer:

```kotlin
// In Layer implementation
override fun registerWithRenderer(controller: RendererController) {
    controller.queueAddAll(getRenderables())
}
```

### 3. Source to Primitives

`AstronomicalSource` provides renderables:

```kotlin
interface AstronomicalSource {
    fun getPoints(): List<PointPrimitive>
    fun getLines(): List<LinePrimitive>
    fun getLabels(): List<TextPrimitive>
    fun getImages(): List<ImagePrimitive>
}
```

### 4. Rendering Queue

`RendererController` manages update types:

```kotlin
enum class UpdateType {
    Reset,           // Complete data reload
    UpdatePositions, // Recompute coordinates
    UpdateImages     // Reload textures
}

// Queueing updates
controller.queueUpdate(UpdateType.UpdatePositions)
```

### 5. Object Managers

Specialized managers handle each primitive type:

| Manager | Primitive | OpenGL |
|---------|-----------|--------|
| `PointObjectManager` | `PointPrimitive` | Point sprites |
| `PolyLineObjectManager` | `LinePrimitive` | Line strips |
| `LabelObjectManager` | `TextPrimitive` | Textured quads |
| `ImageObjectManager` | `ImagePrimitive` | Textured quads |

### 6. OpenGL Rendering

`SkyRenderer` executes the rendering pipeline:

```kotlin
override fun onDrawFrame(gl: GL10) {
    // Apply transformation matrix
    Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, projectionMatrix, 0)

    // Render each manager in depth order
    for (manager in managers) {
        manager.draw(gl)
    }
}
```

## Real-Time Data Sources

Some data is computed or fetched at runtime:

### Solar System (Ephemeris)

```
User Location + Time ─► Ephemeris Calculations ─► Planet Positions
```

Computed using orbital elements and algorithms from:
- Meeus, "Astronomical Algorithms"
- JPL Solar System Dynamics

### ISS Tracking

```
TLE Data (Network) ─► Orbital Propagation ─► ISS Position
```

Two-Line Element data fetched from:
- wheretheiss.at API
- Celestrak NORAD catalog

### Meteor Showers

```
Hardcoded Dates ─► Peak Rate Calculation ─► Radiant Position
```

Based on IMO (International Meteor Organization) calendar.

## Coordinate Transformations

Data flows through coordinate systems:

```
Catalog Coordinates (RA/Dec)
         │
         ▼
Geocentric Coordinates (x, y, z on celestial sphere)
         │
         ▼
View Coordinates (transformed by phone orientation)
         │
         ▼
Screen Coordinates (projected to 2D)
```

See [Coordinate Transform](../sensors/coordinate-transform.md) for details.

## Update Triggers

| Trigger | Update Type | Affected Layers |
|---------|-------------|-----------------|
| Time change | `UpdatePositions` | Solar system, grids |
| Location change | `UpdatePositions` | Horizon, grids |
| Orientation change | View matrix only | All (via renderer) |
| Layer toggle | `Reset` | Toggled layer |
| Preference change | Varies | Dependent layers |
