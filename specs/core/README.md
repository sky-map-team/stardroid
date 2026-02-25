# Core Domain Specification

This directory contains specifications for the **core domain layer** of Stardroid - the astronomical calculations, mathematical algorithms, and sensor fusion that drive the app.

## Purpose

The core domain is the **intellectual heart** of Stardroid - everything that makes this an astronomy app rather than a generic rendering engine.

## Navigation

| Spec | Purpose | Status |
|------|---------|--------|
| [algorithms.md](algorithms.md) | Mathematical algorithms, coordinate transforms, sensor fusion | TODO |
| [data-models.md](data-models.md) | Celestial objects, catalogs, FlatBuffers schema | TODO |
| [search-indexing.md](search-indexing.md) | Search functionality, data structures, algorithms | TODO |

## Core Domain Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Astronomy Module                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │   Stars       │  │  Constellations│  │   Planets    │    │
│  │ (RA/Dec,     │  │ (Boundaries,  │  │ (Ephemeris,  │    │
│  │  Magnitude)   │  │  Patterns)    │  │  Phase)       │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                 Catalogs (Read-only)                │   │
│  │  Stars, Constellations, Messier, Planets          │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Sensor Module                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │ Accelerometer │  │ Magnetometer │  │   Gyroscope  │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │             Sensor Fusion (Orientation)            │   │
│  │  Combine sensors → Device orientation in 3D         │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  Coordinate Transform                        │
│  Device (phone) → Horizontal (alt/az) → Celestial (RA/Dec)   │
│                                                             │
│  Also: J2000 → Apparent (precession)                      │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Search Module                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           Prefix Store (Trie data structure)          │   │
│  │  Enables fast prefix matching ("Sirius", "And")     │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Design Principles

### Rendering Agnostic

The core domain **does not know about rendering**:
- No OpenGL, Vulkan, or AR APIs
- No UI components
- No graphics primitives

Instead, it speaks in **astronomical concepts:**
- "Draw a star at RA 6.75h, Dec -16.71° with magnitude -1.46"
- "Draw a line from these two stars"
- "Show label for this object"

### Mathematical Correctness

**Precision matters:**
- Planetary positions: ±0.001° accuracy required
- Precession: Standard astronomical algorithms
- Coordinate transforms: Rigorous 3D rotation matrices
- Sensor fusion: Weighted averaging for smooth output

### Performance Awareness

**Core domain is fast:**
- Coordinate transform: O(1) matrix operations
- Sensor fusion: O(1) per sensor update
- Search: O(log n) prefix matching
- Catalog lookup: O(log n) binary search or hash

## Key Data Structures

### Celestial Coordinates

```kotlin
data class RaDec(
    val ra: Float,      // Right Ascension (0-360° or 0-24h)
    val dec: Float     // Declination (-90° to +90°)
)

data class AltAz(
    val alt: Float,    // Altitude (elevation, -90° to +90°)
    val az: Float      // Azimuth (compass, 0-360°)
)
```

### Celestial Objects

```kotlin
data class CelestialObject(
    val id: String,           // Unique identifier (e.g., "sirius")
    val name: String,         // Human-readable name
    val ra: Float,           // J2000 Right Ascension (hours)
    val dec: Float,          // J2000 Declination (degrees)
    val magnitude: Float,    // Apparent magnitude (brightness)
    val color: Color,        // Display color
    val size: Float,         // Angular size (degrees)
    val type: ObjectType     // STAR, PLANET, NEBULA, etc.
)
```

### Catalogs

**Star Catalog:** ~250,000 stars from Hipparcos/Tycho-2
- RA/Dec (J2000)
- Magnitude (brightness)
- Proper motion (annual change in RA/Dec)

**Constellation Catalog:** 88 IAU constellations
- Boundary polygons
- Connecting lines (stick figures)

**Planet Data:** Ephemeris positions
- RA/Dec at given time
- Angular size
- Phase (for moon/planets)

## Dependency Flow

```
Core Domain depends on:
├─ Data layer (catalogs, ephemeris)
├─ Sensor APIs (Android framework)
└─ Math utilities (trigonometry, matrices)

Core Domain is used by:
├─ Rendering layer (gets coordinates to draw)
├─ Search layer (queries objects by position)
└─ UI layer (displays information to user)
```

## Relationship to Other Specs

- **[../blueprint/core-domain.md](../blueprint/core-domain.md)** - Detailed core domain specification
- **[../sensors/orientation.md](../sensors/orientation.md)** - Sensor fusion details
- **[../data/catalogs.md](../data/catalogs.md)** - Catalog data formats
- **[../rendering/pipeline.md](../rendering/pipeline.md)** - How core domain is rendered
