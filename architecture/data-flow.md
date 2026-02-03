# Data Flow

This document describes how data flows through Sky Map, from raw astronomical catalogs to rendered pixels.

## Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           BUILD TIME                                      │
│  Raw Catalogs ─► ASCII Protobuf ─► Binary Protobuf ─► App Assets         │
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

### 2. ASCII Protobuf Generation

`tools/generate.sh` runs data converters:

```bash
java -cp $CLASSPATH com.google.android.stardroid.data.Main \
    --input stellar_catalog.csv \
    --output stars.ascii
```

**Converter Classes**:
- `StellarAsciiProtoWriter` - Processes star catalogs
- `MessierAsciiProtoWriter` - Processes Messier objects
- `ConstellationProtoWriter` - Processes constellation data

### 3. Binary Conversion

`tools/binary.sh` converts ASCII to binary format:

```bash
java -cp $CLASSPATH com.google.android.stardroid.data.AsciiToBinaryProtoWriter \
    --input stars.ascii \
    --output stars.binary
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

`AbstractFileBasedLayer` loads binary protobufs on first access:

```kotlin
override fun initialize() {
    val proto = resources.openRawResource(assetId).use { stream ->
        AstronomicalSourcesProto.parseFrom(stream)
    }
    sources = proto.sourceList.map { ProtobufAstronomicalSource(it) }
}
```

### 2. Layer Registration

Layers register their sources with the renderer:

```java
// In Layer implementation
@Override
public void registerWithRenderer(RendererController controller) {
    controller.queueAddAll(getRenderables());
}
```

### 3. Source to Primitives

`AstronomicalSource` provides renderables:

```java
interface AstronomicalSource {
    List<PointPrimitive> getPoints();
    List<LinePrimitive> getLines();
    List<TextPrimitive> getLabels();
    List<ImagePrimitive> getImages();
}
```

### 4. Rendering Queue

`RendererController` manages update types:

```java
enum UpdateType {
    Reset,           // Complete data reload
    UpdatePositions, // Recompute coordinates
    UpdateImages     // Reload textures
}

// Queueing updates
controller.queueUpdate(UpdateType.UpdatePositions);
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

```java
@Override
public void onDrawFrame(GL10 gl) {
    // Apply transformation matrix
    Matrix.multiplyMM(mvpMatrix, transform, viewMatrix, projectionMatrix);

    // Render each manager in depth order
    for (RendererObjectManager manager : managers) {
        manager.draw(gl);
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
