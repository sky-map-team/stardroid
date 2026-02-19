# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Sky Map is an Android planetarium app that displays the night sky in real-time using device sensors and OpenGL rendering. The codebase is written in Java and Kotlin, targeting Android SDK 26-35.

## Build Commands

### Standard Development

```bash
# Build debug APK (GMS flavor with Google Analytics)
./gradlew assembleGmsDebug

# Build F-Droid flavor (no analytics/Google services)
./gradlew assembleFdroidDebug

# Full rebuild including data generation
./build_skymap.sh  # or ./build_skymap.sh --fdroid for F-Droid
```

When creating new files DO NOT include a Google Copyright notice even though this exists in older
code.

### Testing

```bash
# Run all unit tests
./gradlew test

# Run unit tests for specific module
./gradlew app:test

# Run instrumented tests (requires connected device/emulator)
./gradlew app:connectedAndroidTest
```

### Data Generation

When modifying star catalogs or astronomical data:

```bash
# Regenerate binary data files from source catalogs
cd tools
./generate.sh  # Creates ASCII protocol buffers
./binary.sh    # Converts to binary format in app/src/main/assets/
```

Note: The build script has to fix Gradle's generated classpath - see `build_skymap.sh` for details.

## Architecture

### Module Structure

- **app/** - Main Android application with 171 source files
- **datamodel/** - Protocol buffer definitions for astronomical objects
- **tools/** - Standalone utilities for converting star catalogs to binary protobuf format

### Dependency Injection (Dagger 2)

The app uses a two-level Dagger component hierarchy:

1. **ApplicationComponent** - Singleton, app-level dependencies (created in `StardroidApplication`)
2. **Activity Components** - Per-activity scoped components (e.g., `DynamicStarMapComponent`)

Each activity defines its own Dagger component that depends on `ApplicationComponent`. Activities annotated with `@PerActivity` scope receive activity-specific instances.

Key injection points:
- `StardroidApplication.onCreate()` - Initializes `DaggerApplicationComponent`
- Activity components - Inject activity-specific dependencies via `inject(activity)` method

### Rendering Pipeline

The rendering system transforms astronomical objects into OpenGL primitives:

**Layer → AstronomicalSource → Primitives → OpenGL**

1. **Layers** (`layers/` package) - 13 switchable layers (stars, constellations, planets, ISS, etc.)
   - Data-driven layers extend `AbstractFileBasedLayer` (loads protobuf data)
   - Simple layers like `HorizonLayer` contain their own `AbstractAstronomicalSource` subclass

2. **Sources** - Astronomical objects implement `AstronomicalSource` interface
   - `ProtobufAstronomicalSource` - Objects loaded from binary data
   - `PlanetSource` - Solar system objects with calculated positions

3. **Primitives** - Four primitive types rendered by OpenGL:
   - `PointPrimitive` - Stars, planets (rendered by `PointObjectManager`)
   - `LinePrimitive` - Constellation lines, grids (`LineObjectManager`)
   - `TextPrimitive` - Labels (`LabelObjectManager`)
   - `ImagePrimitive` - Images (`ImageObjectManager`)

4. **Renderer** - `RendererController` manages `RendererObjectManager`s
   - Layers register with `RendererController`
   - Updates queued as `RendererObjectManager.UpdateType`
   - OpenGL rendering in `SkyRenderer`

### Coordinate Transformation

The core challenge is transforming device orientation into celestial coordinates:

**Phone Coordinates → Transformation Matrix → Celestial Coordinates**

Key class: `AstronomerModel` (`control/` package)

The algorithm calculates North, Up, and East vectors in both coordinate systems:

1. **Phone coordinates** (`_p` suffix):
   - Modern: Uses Android's rotation sensor (fused accelerometer + magnetometer + gyroscope)
   - Legacy: Calculates from raw accelerometer and magnetometer readings
   - Vectors form rotation matrix rows

2. **Celestial coordinates** (`_c` suffix):
   - Up vector: Zenith based on user's latitude and local sidereal time
   - North vector: Projection of Earth's axis along the ground
   - East vector: Cross product of North × Up

3. **Transformation**: Matrix `M` constructed from these vectors transforms phone pointing direction to RA/Dec coordinates

See `docs/design/sensors.md` for detailed mathematical explanation.

### Data Flow

Astronomical catalog data pipeline:

```
Raw catalogs → tools/Main.java → ASCII protobuf → binary protobuf → app/src/main/assets/
                (StellarAsciiProtoWriter)  (AsciiToBinaryProtoWriter)
                (MessierAsciiProtoWriter)
```

Runtime: Binary files loaded by `AbstractFileBasedLayer`, deserialized into `ProtobufAstronomicalSource` objects.

## Code Style

Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html):
- 100 character line wrap
- Do NOT prefix member variables with `m` (unlike common Android convention)
- Use Java 17 toolchain features

## Build Flavors

- **gms** - Includes Google Play Services (Analytics, Location). Requires `no-checkin.properties` for release builds.
- **fdroid** - Pure open source, no Google dependencies

Always specify flavor when building: `assembleGmsDebug` not `assembleDebug`.

## Key Files

- [StardroidApplication.kt](app/src/main/java/com/google/android/stardroid/StardroidApplication.kt) - Application entry point, Dagger initialization, sensor detection
- [DynamicStarMapActivity](app/src/main/java/com/google/android/stardroid/activities/DynamicStarMapActivity.java) - Main interactive star map
- [AstronomerModel](app/src/main/java/com/google/android/stardroid/control/AstronomerModel.java) - Coordinate transformation logic
- [SkyRenderer](app/src/main/java/com/google/android/stardroid/renderer/SkyRenderer.java) - OpenGL rendering
- [source.proto](datamodel/src/main/proto/source.proto) - Protocol buffer schema for astronomical objects

## Testing Notes

- Unit tests use JUnit 4, Robolectric, Mockito, and Truth
- Instrumented tests use Espresso
- Test structure mirrors main source: `app/src/test/` and `app/src/androidTest/`
