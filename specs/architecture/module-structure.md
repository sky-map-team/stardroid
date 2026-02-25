# Module Structure

Sky Map is organized into three Gradle modules with distinct responsibilities.

## Module Overview

```
stardroid/
├── app/                 # Main Android application
├── datamodel/           # FlatBuffers definitions
└── tools/               # Data generation utilities
```

## app/ - Main Application

The core Android application containing all UI, rendering, and business logic.

### Package Structure

```
com.google.android.stardroid/
├── activities/          # Android Activities
├── control/             # Controllers and models
├── data/                # Data access layer
├── ephemeris/           # Solar system calculations
├── gallery/             # Image gallery feature
├── inject/              # Dagger DI components
├── layers/              # Celestial object layers
├── math/                # Math utilities
├── provider/            # Content providers
├── renderer/            # OpenGL rendering
├── renderables/         # Renderable primitives
├── search/              # Search functionality
├── space/               # Coordinate types
├── touch/               # Gesture handling
├── util/                # Utilities
└── views/               # Custom views
```

### Key Packages

#### activities/
Android Activities and DialogFragments:
- `DynamicStarMapActivity` - Main star map
- `SplashScreenActivity` - App launch
- `EditSettingsActivity` - Preferences
- `ImageGalleryActivity` - Image browser
- `DiagnosticActivity` - Debug info

#### control/
Model and controller classes:
- `AstronomerModel` - Core coordinate transformation
- `ControllerGroup` - Input controller management
- `TimeTravelClock` - Time travel feature
- `LocationController` - Location management

#### layers/
Celestial object display layers:
- `AbstractLayer` - Base layer class
- `StarsLayer`, `ConstellationsLayer` - File-based layers
- `SolarSystemLayer`, `GridLayer` - Computed layers
- `LayerManager` - Layer visibility control

#### renderer/
OpenGL rendering system:
- `SkyRenderer` - Main renderer
- `RendererController` - Update queue
- `*ObjectManager` - Point/Line/Label/Image managers
- `TextureManager` - Texture loading

### Source Statistics

| Metric | Count |
|--------|-------|
| Source files | 171+ |
| Kotlin files | ~170 |
| Test files | 50+ |

**Note:** This is a pure Kotlin codebase. No Java source files.

## datamodel/ - FlatBuffers

Defines the serialization format for astronomical objects.

### Structure

```
datamodel/
└── src/main/fbs/
    └── source.fbs        # FlatBuffers schema
```

### FlatBuffers Schema

Defines tables for astronomical data:

```fbs
table AstronomicalSource {
    names: [string];
    search_location: GeocentricCoordinates;
    level: int32;
    points: [PointElement];
    labels: [LabelElement];
    lines: [LineElement];
}

table PointElement {
    color: uint32;
    size: int32;
    location: GeocentricCoordinates (required);
    shape: Shape = Circle;
}
```

### Shape Types

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

## tools/ - Data Generation

Standalone utilities for converting astronomical catalogs to FlatBuffers binary format.

### Structure

```
tools/
├── build.gradle.kts        # Tool build config
├── generate.sh             # JSON intermediate generation
├── binary.sh               # FlatBuffers binary conversion
└── src/main/kotlin/
    └── com/stardroid/awakening/tools/
        ├── Main.kt                    # Entry point
        ├── StellarCatalogConverter.kt # Star catalog processor
        ├── MessierCatalogConverter.kt # Messier catalog processor
        └── ConstellationConverter.kt  # Constellation processor
```

### Data Pipeline

```
Raw Catalogs → tools/Main.kt → JSON Intermediate → FlatBuffers Binary
                     │                  │                   │
           (StellarCatalogConverter)    │          (flatc compiler)
           (MessierCatalogConverter)    │                   │
                                        ▼                   ▼
                              tools/data/*.json    app/src/main/assets/*.bin
```

### Generated Assets

| File | Source | Content |
|------|--------|---------|
| `stars.binary` | Hipparcos catalog | ~100k stars |
| `constellations.binary` | Stellarium data | 88 constellations |
| `messier.binary` | Messier catalog | ~110 deep-sky objects |

## Dependencies Between Modules

```
     app/
      │
      ├── depends on ─► datamodel/
      │                    (FlatBuffers classes)
      │
      └── uses output from ─► tools/
                                (binary assets)
```

## Build Configuration

### app/build.gradle.kts
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":datamodel"))
    implementation("com.google.flatbuffers:flatbuffers-java:24.3.25")
    ksp("com.google.dagger:dagger-compiler:2.x")
}
```

### datamodel/build.gradle.kts
```kotlin
plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("com.google.flatbuffers:flatbuffers-java:24.3.25")
}

// FlatBuffers code generation
tasks.register<Exec>("generateFlatBuffers") {
    commandLine("flatc", "--kotlin", "-o", "src/main/kotlin", "src/main/fbs/source.fbs")
}
```

### tools/build.gradle.kts
```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":datamodel"))
}
```
