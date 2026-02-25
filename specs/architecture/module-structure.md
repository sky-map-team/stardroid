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
Android Activities and DialogFragments (mostly Java):
- `DynamicStarMapActivity.java` - Main star map
- `SplashScreenActivity.java` - App launch
- `EditSettingsActivity.java` - Preferences
- `ImageGalleryActivity.java` - Image browser
- `DiagnosticActivity.java` - Debug info

#### control/
Model and controller classes (mostly Java):
- `AstronomerModel.java` - Core coordinate transformation
- `ControllerGroup.java` - Input controller management
- `TimeTravelClock.java` - Time travel feature
- `LocationController.java` - Location management

#### layers/
Celestial object display layers (Kotlin):
- `AbstractLayer.kt` - Base layer class
- `StarsLayer.kt`, `ConstellationsLayer.kt` - File-based layers
- `SolarSystemLayer.kt`, `GridLayer.kt` - Computed layers
- `LayerManager.kt` - Layer visibility control

#### renderer/
OpenGL rendering system (mostly Java):
- `SkyRenderer.java` - Main renderer
- `RendererController.java` - Update queue
- `*ObjectManager.java` - Point/Line/Label/Image managers
- `TextureManager.java` - Texture loading

### Source Statistics

| Metric | Count |
|--------|-------|
| Source files | 171+ |
| Test files | 50+ |

**Note:** The codebase is mixed Java and Kotlin. The `layers/` package is Kotlin; `activities/`, `control/`, and `renderer/` packages are predominantly Java.

## datamodel/ - Protocol Buffers

Defines the serialization format for astronomical objects using Protocol Buffers (proto2).

### Structure

```
datamodel/
└── src/main/proto/
    └── source.proto      # Protocol buffer schema
```

### Protobuf Schema

Defines messages for astronomical data (see `specs/data/flatbuffers-schema.md` for full schema):

```proto
message AstronomicalSourceProto {
  repeated string name_str_ids = 8;
  optional GeocentricCoordinatesProto search_location = 2;
  optional float level = 4;
  repeated PointElementProto point = 5;
  repeated LabelElementProto label = 6;
  repeated LineElementProto line = 7;
}

message PointElementProto {
  optional GeocentricCoordinatesProto location = 1;
  optional uint32 color = 2 [default = 0xFFFFFFFF];
  optional int32 size = 3 [default = 3];
  optional Shape shape = 4 [default = CIRCLE];
}
```

### Shape Types

```proto
enum Shape {
  CIRCLE = 0;
  STAR = 1;
  OPEN_CLUSTER = 2;
  GLOBULAR_CLUSTER = 3;
  DIFFUSE_NEBULA = 4;
  PLANETARY_NEBULA = 5;
  SUPERNOVA_REMNANT = 6;
  GALAXY = 7;
  OTHER = 8;
}
```

## tools/ - Data Generation

Standalone Java utilities for converting astronomical catalogs to binary protobuf format.

### Structure

```
tools/
├── build.gradle            # Tool build config
└── src/main/java/
    └── com/google/android/stardroid/data/
        ├── Main.java                   # Entry point
        ├── StellarAsciiProtoWriter.java # Star catalog → ASCII protobuf
        ├── MessierAsciiProtoWriter.java # Messier catalog → ASCII protobuf
        └── AsciiToBinaryProtoWriter.java # ASCII protobuf → binary protobuf
```

### Data Pipeline

```
Raw Catalogs → tools/Main.java → ASCII Protobuf Text → Binary Protobuf
                     │                                        │
           (StellarAsciiProtoWriter)               (AsciiToBinaryProtoWriter)
           (MessierAsciiProtoWriter)                          │
                                                              ▼
                                               app/src/main/assets/*.binary
```

### Generated Assets

| File | Source | Content |
|------|--------|---------|
| `stars.binary` | Hipparcos catalog | Star positions, magnitudes, colors |
| `constellations.binary` | Stellarium data | Constellation lines and labels |
| `messier.binary` | Messier catalog | Deep-sky objects |

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
    id("com.google.dagger.hilt.android")  // or Dagger 2 kapt
}

dependencies {
    implementation(project(":datamodel"))
    // Protocol Buffers runtime (for deserializing binary assets)
    implementation("com.google.protobuf:protobuf-java:3.x")
}
```

### datamodel/build.gradle
The `datamodel` module uses the protobuf Gradle plugin to compile `source.proto` into Java classes.

### tools/build.gradle
```groovy
plugins {
    id("java")
    id("application")
}

dependencies {
    implementation(project(":datamodel"))
}
```
