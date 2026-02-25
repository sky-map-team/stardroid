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
Celestial objects organized into 12 independent, toggleable layers:
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

## Key Files

| File | Purpose |
|------|---------|
| `StardroidApplication.kt` | App entry point, Dagger initialization |
| `DynamicStarMapActivity.kt` | Main star map UI |
| `AstronomerModel.kt` | Coordinate transformation |
| `SkyRenderer.kt` | OpenGL rendering |
| `LayerManager.kt` | Layer visibility management |
