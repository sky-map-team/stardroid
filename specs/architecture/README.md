# Architecture Overview

This section documents the architectural patterns and system design of Sky Map.

## Contents

| Specification | Description |
|--------------|-------------|
| [Dependency Injection](dependency-injection.md) | Dagger 2 component hierarchy |
| [Module Structure](module-structure.md) | app/, datamodel/, tools/ modules |
| [Data Flow](data-flow.md) | Data pipeline from catalogs to rendering |

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android UI Layer                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  Activities  │  │   Dialogs    │  │    Preferences       │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
└─────────┼─────────────────┼─────────────────────┼───────────────┘
          │                 │                     │
          ▼                 ▼                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Controller Layer                            │
│  ┌────────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │ SensorOrientation  │  │  ManualControl  │  │  Location   │  │
│  │    Controller      │  │   Controller    │  │ Controller  │  │
│  └─────────┬──────────┘  └────────┬────────┘  └──────┬──────┘  │
└────────────┼──────────────────────┼─────────────────┬┼──────────┘
             │                      │                 ││
             ▼                      ▼                 ▼▼
┌─────────────────────────────────────────────────────────────────┐
│                      Model Layer                                 │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    AstronomerModel                          │ │
│  │  • Coordinate transformation (phone → celestial)            │ │
│  │  • Field of view calculation                                │ │
│  │  • Pointing direction                                       │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Layer System                                │
│  ┌──────────┐ ┌──────────────┐ ┌──────────┐ ┌───────────────┐  │
│  │  Stars   │ │ Constellations│ │ Planets  │ │    Grid       │  │
│  └────┬─────┘ └───────┬──────┘ └────┬─────┘ └───────┬───────┘  │
│       │               │             │               │           │
│       └───────────────┴─────────────┴───────────────┘           │
│                              │                                   │
│                              ▼                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                 AstronomicalSource                          │ │
│  │  • Points (stars, planets)                                  │ │
│  │  • Lines (constellations, grids)                            │ │
│  │  • Labels (names)                                           │ │
│  │  • Images (deep-sky objects)                                │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Rendering Layer                               │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                  RendererController                         │ │
│  │  • Queues updates from layers                               │ │
│  │  • Manages object managers                                  │ │
│  └─────────────────────────┬──────────────────────────────────┘ │
│                            ▼                                     │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────┐ │
│  │PointManager │ │ LineManager  │ │ LabelManager │ │ Image  │ │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └───┬────┘ │
│         │                │                │             │       │
│         └────────────────┴────────────────┴─────────────┘       │
│                                  │                               │
│                                  ▼                               │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                     SkyRenderer                             │ │
│  │  • OpenGL ES 2.0 rendering                                  │ │
│  │  • Coordinate transformation                                │ │
│  │  • Texture management                                       │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## Key Design Patterns

### Dependency Injection (Dagger 2)
Two-level component hierarchy:
1. **ApplicationComponent** - App-wide singletons
2. **Activity Components** - Per-activity scoped instances

See [Dependency Injection](dependency-injection.md) for details.

### Layer Pattern
Celestial objects organized into 12 independent, toggleable layers (Stars, Constellations, SolarSystem, Grid, Horizon, Messier, MeteorShower, ISS, Ecliptic, SkyGradient, Comets, StarOfBethlehem):
- Each layer implements the `Layer` interface
- Layers register with `RendererController`
- Visibility controlled via SharedPreferences

### Controller Pattern
`ControllerGroup` manages input controllers:
- `SensorOrientationController` - Sensor-based orientation
- `ManualOrientationController` - Touch-based navigation
- Controllers are mutually exclusive, switched based on mode

### Update Queue Pattern
Layers publish updates via `UpdateType`:
- `Reset` - Complete data reload
- `UpdatePositions` - Recompute coordinates
- `UpdateImages` - Reload textures

## Coordinate Transformation

The core challenge is transforming device orientation into celestial coordinates:

```
Phone Coordinates → Transformation Matrix → Celestial Coordinates
```

Key class: `AstronomerModel.java` in the `control/` package.

The algorithm calculates North, Up, and East vectors in both coordinate systems:

1. **Phone coordinates** — Two paths:
   - *Modern:* Uses Android's rotation sensor (fused accelerometer + magnetometer + gyroscope)
   - *Legacy:* Calculates from raw accelerometer and magnetometer readings

2. **Celestial coordinates:**
   - Up vector: Zenith based on user's latitude and local sidereal time
   - North vector: Projection of Earth's axis along the ground
   - East vector: Cross product of North × Up

3. **Transformation:** Matrix `M` constructed from these vectors transforms phone pointing direction to RA/Dec coordinates.

See [sensors/orientation.md](../sensors/orientation.md) and [sensors/coordinate-transform.md](../sensors/coordinate-transform.md) for the detailed mathematical explanation. For the original design documents, see [docs/design/sensors.md](../../docs/design/sensors.md) and [docs/design/sensor_dataflow.md](../../docs/design/sensor_dataflow.md).

## Key Entry Points

| File | Role |
|------|------|
| `app/src/main/java/com/google/android/stardroid/StardroidApplication.kt` | Application entry point, Dagger initialization, sensor detection |
| `app/src/main/java/com/google/android/stardroid/activities/DynamicStarMapActivity.java` | Main interactive star map |
| `app/src/main/java/com/google/android/stardroid/control/AstronomerModel.java` | Coordinate transformation logic |
| `app/src/main/java/com/google/android/stardroid/renderer/SkyRenderer.java` | OpenGL rendering |
| `datamodel/src/main/proto/source.proto` | Protocol buffer schema for astronomical objects |
