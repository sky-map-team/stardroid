# Data Flow

This document describes how data flows through Sky Map, from raw astronomical catalogs to rendered pixels.

## Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           BUILD TIME                                      │
│  Raw Catalogs ─► ASCII Protobuf Text ─► Binary Protobuf ─► App Assets    │
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

### 2. ASCII Protobuf Text Generation

`tools/src/main/java/com/google/android/stardroid/data/Main.java` is the entry point. It runs the converter classes to produce ASCII-format protobuf text files:

**Converter Classes (Java)**:
- `StellarAsciiProtoWriter` - Processes star catalogs
- `MessierAsciiProtoWriter` - Processes Messier objects
- `AsciiToBinaryProtoWriter` - Converts ASCII protobuf to binary

### 3. Binary Protobuf Conversion

`AsciiToBinaryProtoWriter` converts ASCII protobuf to binary format using the `AstronomicalSourcesProto` message type defined in `datamodel/src/main/proto/source.proto`.

**Output Location**: `app/src/main/assets/`

### 4. Final Assets

| Asset File | Contents |
|------------|----------|
| `stars.binary` | Star positions, colors, magnitudes |
| `constellations.binary` | Lines and labels |
| `messier.binary` | Deep-sky objects |

## Runtime Data Flow

### 1. Asset Loading

`AbstractFileBasedLayer` loads binary protobuf assets and wraps them in `ProtobufAstronomicalSource` objects:

```kotlin
override fun initialize() {
    val assetStream = context.assets.open(assetFilename)
    val sources = AstronomicalSourcesProto.parseFrom(assetStream)
    // Each source wrapped in ProtobufAstronomicalSource
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
