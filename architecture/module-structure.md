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
| Java files | ~140 |
| Kotlin files | ~30 |
| Test files | 50+ |

## datamodel/ - FlatBuffers

Defines the serialization format for astronomical objects.

### Structure

```
datamodel/
└── src/main/proto/
    └── source.fbs        # Protobuf schema
```

### Protocol Buffer Schema

Defines messages for astronomical data:

```FlatBuffers
message AstronomicalSourceProto {
  repeated PointElementProto point = 1;
  repeated LabelElementProto label = 2;
  repeated LineElementProto line = 3;
  optional string search_location = 4;
  // ...
}

message PointElementProto {
  required int32 color = 1;
  required int32 size = 2;
  required GeocentricCoordinatesProto location = 3;
  optional Shape shape = 4;
}
```

### Shape Types

```FlatBuffers
enum Shape {
  CIRCLE = 0;
  STAR = 1;
  ELLIPTICAL_GALAXY = 2;
  SPIRAL_GALAXY = 3;
  IRREGULAR_GALAXY = 4;
  LENTICULAR_GALAXY = 5;
  GLOBULAR_CLUSTER = 6;
  OPEN_CLUSTER = 7;
  NEBULA = 8;
  HUBBLE_DEEP_FIELD = 9;
}
```

## tools/ - Data Generation

Standalone utilities for converting astronomical catalogs to binary FlatBuffers format.

### Structure

```
tools/
├── build.gradle            # Tool build config
├── generate.sh             # ASCII FlatBuffers generation
├── binary.sh               # Binary conversion
└── src/main/java/
    └── com/google/android/stardroid/data/
        ├── Main.java                    # Entry point
        ├── StellarAsciiProtoWriter.java # Star catalog processor
        ├── MessierAsciiProtoWriter.java # Messier catalog processor
        └── AsciiToBinaryProtoWriter.java # Binary converter
```

### Data Pipeline

```
Raw Catalogs → tools/Main.java → ASCII Protobuf → Binary Protobuf
                     │                  │               │
           (StellarAsciiProtoWriter)    │    (AsciiToBinaryProtoWriter)
           (MessierAsciiProtoWriter)    │               │
                                        ▼               ▼
                              tools/data/*.ascii    app/src/main/assets/*.binary
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

### app/build.gradle
```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'kotlin-kapt'
}

dependencies {
    implementation project(':datamodel')
    kapt 'com.google.dagger:dagger-compiler:2.x'
    // ...
}
```

### datamodel/build.gradle
```groovy
plugins {
    id 'java-library'
    // FlatBuffers uses flatc compiler, configured via custom task
}
```

### tools/build.gradle
```groovy
plugins {
    id 'java'
    id 'application'
}

dependencies {
    implementation project(':datamodel')
}
```
