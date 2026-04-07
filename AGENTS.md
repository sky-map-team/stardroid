# AGENTS.md

This file provides project context for AI coding assistants working in this repository.

## Project Overview

Sky Map is an open-source Android planetarium app that displays the night sky in real-time using
device sensors and OpenGL rendering. Originally "Google Sky Map" (open-sourced 2011), now
community-maintained. The internal codename "Stardroid" remains in package names.

Codebase: Java and Kotlin, targeting Android SDK 26–36.
github: https://github.com/sky-map-team/stardroid

## Module Structure

- **app/** - Main Android application (~171 source files)
- **datamodel/** - Protocol buffer definitions for astronomical objects
- **tools/** - Standalone utilities for converting star catalogs to binary protobuf format

Read specs in `specs/` before undertaking complex investigations, starting with the overview.md file
to know which specs to read.

## Build Flavors

- **gms** - Includes Google Play Services (Analytics, Location). Requires `no-checkin.properties`
  for release builds.
- **fdroid** - Pure open source, no Google dependencies.

Always specify the flavor: use `assembleGmsDebug`, not `assembleDebug`. See the `/build` skill for
all build, test, deploy, and data-generation commands.

## Architecture

See `docs/ARCHITECTURE.md` for a full overview.

### Dependency Injection

Uses Hilt.

### Rendering Pipeline

Layers → AstronomicalSource → Primitives (Point/Line/Text/Image) → OpenGL via `RendererController` /
`SkyRenderer`. See `docs/ARCHITECTURE.md` for full detail.

### Coordinate Transformation

`AstronomerModel` maps phone sensor coordinates to celestial RA/Dec via a transformation matrix
derived from zenith and North vectors. See `docs/design/sensors.md` for the math.

### Data Flow

```
Raw catalogs → tools/Main.java → ASCII protobuf → binary protobuf → app/src/main/assets/
                (StellarAsciiProtoWriter)  (AsciiToBinaryProtoWriter)
```

Runtime: Binary files loaded by `AbstractFileBasedLayer`, deserialized into
`ProtobufAstronomicalSource`.

### Adding Dialog Fragments

Follow the pattern in `AbstractDynamicStarMapModule`:

1. Add a `@Provides @PerActivity` method returning `new XyzDialogFragment()`
2. Add `XyzDialogFragment.ActivityComponent` to `DynamicStarMapComponent` interface
3. Inject the fragment in `DynamicStarMapActivity` and handle in `onOptionsItemSelected`

## Code Style

No copyright header on new files.

Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html):

- 100 character line wrap
- Do **not** prefix member variables with `m` (unlike common Android convention)
- Use Java 17 toolchain features

### Strings
Remember to properly escape any text added as Android resource strings (e.g. ' must be escaped
with a single backslash as \')

### Colors

Never hardcode color integers in Java/Kotlin. Declare in `app/src/main/res/values/colors.xml` and
reference via `R.color.*`.

Status colors follow a two-tier naming scheme:
| Resource | Day-mode meaning | Night-mode pair |
|---|---|---|
| `status_good` | Green — everything OK | `night_status_good` |
| `status_ok` | Yellow — acceptable | `night_status_ok` |
| `status_warning` | Orange — degraded | `night_status_warning` |
| `status_bad` | Red — error/missing | `night_status_bad` |
| `status_absent` | Grey — hardware absent | `night_status_absent` |

Night-mode variants are red-shifted; brighter = better (mirrors day-mode meaning).

## Key Files

- [`StardroidApplication.kt`](app/src/main/java/com/google/android/stardroid/StardroidApplication.kt) - Application entry point, Dagger initialization, sensor detection
- [
  `DynamicStarMapActivity.java`](app/src/main/java/com/google/android/stardroid/activities/DynamicStarMapActivity.java) -
  Main interactive star map activity
- [
  `AstronomerModel.java`](app/src/main/java/com/google/android/stardroid/control/AstronomerModel.java) -
  Coordinate transformation logic
- [`SkyRenderer.java`](app/src/main/java/com/google/android/stardroid/renderer/SkyRenderer.java) -
  OpenGL rendering
- [`source.proto`](datamodel/src/main/proto/source.proto) - Protocol buffer schema for astronomical
  objects

## Testing

Unit tests: JUnit 4, Robolectric, Mockito, Truth. Instrumented: Espresso.
Structure mirrors main source: `app/src/test/` and `app/src/androidTest/`.
