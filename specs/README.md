# Sky Map Specifications

This directory contains comprehensive specifications for the Sky Map Android planetarium application.

## Overview

Sky Map is an open-source Android planetarium app originally released by Google in 2009. It displays the night sky in real-time using device sensors and OpenGL rendering, allowing users to identify stars, planets, constellations, and other celestial objects by pointing their device at the sky.

## Specification Structure

```
specs/
├── README.md                    # This file - Navigation hub
├── overview.md                  # High-level app overview
│
├── blueprint/                   # FUTURE DESIGN (not implemented) — AR/Vulkan/Hilt/Compose target
│   ├── README.md                # Blueprint overview and navigation
│   ├── core-domain.md          # Astronomical core (algorithms, math, sensors)
│   ├── rendering-abstraction.md # Graphics abstraction layer
│   ├── ar-vulkan-target.md     # AR/Vulkan target architecture
│   ├── migration-roadmap.md    # Migration path from current to target
│   └── future-data-generation.md # Proposed FlatBuffers data pipeline
│
├── architecture/                # Current architecture documentation
│   ├── README.md               # Architecture overview
│   ├── dependency-injection.md # Dagger 2 component hierarchy
│   ├── module-structure.md     # app/, datamodel/, tools/ modules
│   └── data-flow.md            # Data pipeline from catalogs to rendering
│
├── core/                        # NEW: Core domain layer (future home)
│   ├── README.md               # Core domain navigation
│   ├── algorithms.md           # Math, coordinates, sensor fusion (from sensors/)
│   ├── data-models.md          # Celestial objects, catalogs (from data/)
│   └── search-indexing.md      # Search functionality (from features/search.md)
│
├── features/
│   ├── README.md               # Features overview
│   ├── star-map.md             # Main interactive star map
│   ├── search.md               # Celestial object search
│   ├── time-travel.md          # View sky at different dates/times
│   ├── layers.md               # Switchable celestial object layers
│   ├── settings.md             # User preferences
│   ├── image-gallery.md        # Astronomical image browsing
│   └── ar-mode.md              # Augmented reality mode (future)
│
├── rendering/
│   ├── README.md               # Rendering system overview
│   ├── pipeline.md             # Layer → Primitives → OpenGL
│   ├── primitives.md           # Points, lines, labels, images
│   └── managers.md             # Object managers and rendering
│
├── ui/                          # NEW: UI layer documentation (future home)
│   ├── README.md               # UI layer overview
│   ├── material-3.md           # Material 3 theme system
│   ├── activities.md           # Activity classes and roles
│   └── dialogs.md              # Bottom sheets, fragments
│
├── sensors/
│   ├── README.md               # Sensor system overview
│   ├── orientation.md          # Device orientation detection
│   ├── coordinate-transform.md # Phone to celestial coordinate transformation
│   └── manual-control.md       # Non-sensor navigation
│
├── data/
│   ├── README.md               # Data sources overview
│   ├── catalogs.md             # Star, constellation, Messier catalogs
│   ├── ephemeris.md            # Solar system calculations
│   ├── iss-tracking.md         # International Space Station
│   └── protobuf-schema.md      # Protocol Buffers schema definitions
│
└── build/
    ├── README.md               # Build system overview
    ├── flavors.md              # GMS vs F-Droid builds
    └── data-generation.md      # Catalog to binary conversion
```

## Reading Guide

### For Understanding the Current System
1. [overview.md](overview.md) - What Sky Map does
2. [architecture/README.md](architecture/README.md) - How it's built
3. [features/](features/README.md) - Feature specifications
4. [rendering/](rendering/README.md) - Graphics pipeline
5. [sensors/](sensors/README.md) - Sensor integration

### For Modernizing/Extending the System
1. [blueprint/README.md](blueprint/README.md) - Target architecture vision
2. [blueprint/core-domain.md](blueprint/core-domain.md) - Stable domain abstractions
3. [blueprint/rendering-abstraction.md](blueprint/rendering-abstraction.md) - Graphics abstraction
4. [blueprint/ar-vulkan-target.md](blueprint/ar-vulkan-target.md) - AR/Vulkan design
5. [blueprint/migration-roadmap.md](blueprint/migration-roadmap.md) - How to get there

## Quick Reference

| Specification | Description |
|--------------|-------------|
| [Overview](overview.md) | What Sky Map does and key features |
| [Architecture](architecture/README.md) | System design and patterns |
| [Features](features/README.md) | User-facing functionality |
| [Rendering](rendering/README.md) | OpenGL rendering pipeline |
| [Sensors](sensors/README.md) | Device orientation and coordinates |
| [Data](data/README.md) | Astronomical data sources |
| [Build](build/README.md) | Build system and flavors |

## Key Technologies

- **Layers**: 12 switchable celestial layers
- **Platform**: Android SDK 26+ (minSdk 26, compileSdk 35, targetSdk 36)
- **Language**: Mixed Java and Kotlin (layers/ is Kotlin; activities/, control/, renderer/ are predominantly Java)
- **Rendering**: OpenGL ES 1.x (GL10 API via `GLSurfaceView.Renderer`)
- **DI Framework**: Dagger 2 (not Hilt)
- **Data Format**: Protocol Buffers proto2 (binary assets in `app/src/main/assets/*.binary`)
- **Testing**: JUnit 4, Robolectric, Mockito, Espresso

## Version Constraints

| Dependency | Version | Notes |
|------------|---------|-------|
| Java | 17 | jvmToolchain(17) in build.gradle |
| AGP | 8.13.2 | Android Gradle Plugin |
| Kotlin | 2.0.20 | |
| Gradle | 8.13 | |
| compileSdk | 35 | |
| minSdk | 26 | Android 8.0+ |
