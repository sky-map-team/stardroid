# AGENTS.md

This file provides project context for AI coding assistants working in this repository.

## Project Overview

Sky Map is an open-source Android planetarium app that displays the night sky in real-time using device sensors and OpenGL rendering. It was originally created by Google as "Google Sky Map" and open-sourced in 2011. The project is now community-maintained. The original internal codename "Stardroid" is still present in package names and the codebase.

The app uses the device's sensors (accelerometer, magnetometer, gyroscope) and GPS to determine orientation and location, then renders the appropriate sky view using OpenGL.

The codebase is written in Java and Kotlin, targeting Android SDK 26–36.

## Module Structure

- **app/** - Main Android application (~171 source files)
- **datamodel/** - Protocol buffer definitions for astronomical objects
- **tools/** - Standalone utilities for converting star catalogs to binary protobuf format

## Build Commands

### Prerequisites

- Android SDK installed
- `local.properties` in the root directory:
  ```
  sdk.dir=<path to your Android SDK>
  ```

### Standard Development

```bash
# Build debug APK (GMS flavor with Google Analytics)
./gradlew assembleGmsDebug

# Build F-Droid flavor (no analytics/Google services)
./gradlew assembleFdroidDebug

# Full rebuild including data generation
./build_skymap.sh          # GMS
./build_skymap_fdroid.sh   # F-Droid

# Build a release bundle
./gradlew :app:bundleGmsRelease
```

Note: The build script has to fix Gradle's generated classpath — see `build_skymap.sh` for details.

### Testing

```bash
# Run all unit tests
./gradlew test

# Run unit tests for the app module only
./gradlew app:test

# Run instrumented tests (requires connected device/emulator)
./gradlew app:connectedAndroidTest
```

### Linting

```bash
./gradlew lint
```

### Data Generation

When modifying star catalogs or astronomical data:

```bash
cd tools
./generate.sh  # Creates ASCII protocol buffers
./binary.sh    # Converts to binary format in app/src/main/assets/
```

### Deployment

```bash
./deploy.sh       # Deploy to a connected device or emulator
./deploy.sh -d    # Deploy a debug build
./undeploy.sh     # Uninstall the app
```

## Build Flavors

- **gms** - Includes Google Play Services (Analytics, Location). Requires `no-checkin.properties` for release builds.
- **fdroid** - Pure open source, no Google dependencies.

Always specify the flavor when building: use `assembleGmsDebug`, not `assembleDebug`.

## Architecture

See `docs/ARCHITECTURE.md` for a full architectural overview. Key patterns are summarized below.

### Dependency Injection (Dagger 2)

The app uses a two-level Dagger 2 component hierarchy (not Hilt):

1. **ApplicationComponent** - Singleton, app-level dependencies (created in `StardroidApplication`)
2. **Activity Components** - Per-activity scoped components (e.g., `DynamicStarMapComponent`)

Each activity defines its own Dagger component that depends on `ApplicationComponent`. Activities annotated with `@PerActivity` scope receive activity-specific instances.

Key injection points:
- `StardroidApplication.onCreate()` - Initializes `DaggerApplicationComponent`
- Activity components inject via `inject(activity)` method

### Rendering Pipeline

**Layer → AstronomicalSource → Primitives → OpenGL**

1. **Layers** (`layers/` package) - 12 switchable layers (stars, constellations, planets, ISS, etc.)
   - Data-driven layers extend `AbstractFileBasedLayer` (loads protobuf data)
   - Simple layers like `HorizonLayer` contain their own `AbstractAstronomicalSource` subclass

2. **Sources** - Astronomical objects implement `AstronomicalSource` interface
   - `ProtobufAstronomicalSource` - Objects loaded from binary data
   - `PlanetSource` - Solar system objects with calculated positions

3. **Primitives** - Four types rendered by OpenGL:
   - `PointPrimitive` - Stars, planets (`PointObjectManager`)
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

1. **Phone coordinates** (`_p` suffix) — from rotation sensor (fused accelerometer + magnetometer + gyroscope) or legacy raw sensors
2. **Celestial coordinates** (`_c` suffix) — Up = zenith from latitude/sidereal time; North = Earth's axis projected to ground; East = North × Up
3. **Transformation**: Matrix `M` from these vectors maps phone pointing direction to RA/Dec

See `docs/design/sensors.md` for the full mathematical explanation.

### Data Flow

```
Raw catalogs → tools/Main.java → ASCII protobuf → binary protobuf → app/src/main/assets/
                (StellarAsciiProtoWriter)  (AsciiToBinaryProtoWriter)
                (MessierAsciiProtoWriter)
```

Runtime: Binary files loaded by `AbstractFileBasedLayer`, deserialized into `ProtobufAstronomicalSource` objects.

## Code Style

Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html):
- 100 character line wrap
- Do **not** prefix member variables with `m` (unlike common Android convention)
- Use Java 17 toolchain features

## Key Files

- [`StardroidApplication.kt`](app/src/main/java/com/google/android/stardroid/StardroidApplication.kt) - Application entry point, Dagger initialization, sensor detection
- [`DynamicStarMapActivity.java`](app/src/main/java/com/google/android/stardroid/activities/DynamicStarMapActivity.java) - Main interactive star map activity
- [`AstronomerModel.java`](app/src/main/java/com/google/android/stardroid/control/AstronomerModel.java) - Coordinate transformation logic
- [`SkyRenderer.java`](app/src/main/java/com/google/android/stardroid/renderer/SkyRenderer.java) - OpenGL rendering
- [`source.proto`](datamodel/src/main/proto/source.proto) - Protocol buffer schema for astronomical objects

## Testing

- Unit tests use JUnit 4, Robolectric, Mockito, and Truth
- Instrumented tests use Espresso
- Test structure mirrors main source: `app/src/test/` and `app/src/androidTest/`
