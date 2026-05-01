# AGENTS.md

This file provides project context for AI coding assistants working in this repository.

## Project Overview

Sky Map is an open-source Android planetarium app that displays the night sky in real-time using
device sensors and OpenGL rendering. Originally "Google Sky Map" (open-sourced 2011), now
community-maintained. The internal codename "Stardroid" remains in package names.

Codebase: Java and Kotlin, targeting Android SDK 26–36.
github: https://github.com/sky-map-team/stardroid

## Module Structure

- **stardroid-v1/app/** - Main Android application (~171 source files)
- **stardroid-v1/datamodel/** - Protocol buffer definitions for astronomical objects
- **stardroid-v1/tools/** - Standalone utilities for converting star catalogs to binary protobuf format

Read specs in `stardroid-v1/specs/` before undertaking complex investigations, starting with the overview.md file
to know which specs to read.

## Branching

Always make code changes on a feature branch, never directly on `master`. Create a branch before
starting any work:

```
git checkout -b feature/<short-description>
```

Only commit to `master` when explicitly instructed to do so.

## Build Flavors

- **gms** - Includes Google Play Services (Analytics, Location). Requires `no-checkin.properties`
  for release builds.
- **fdroid** - Pure open source, no Google dependencies.

Always specify the flavor: use `assembleGmsDebug`, not `assembleDebug`. See the `/build` skill for
all build, test, deploy, and data-generation commands.

## Architecture

See `stardroid-v1/docs/ARCHITECTURE.md` for a full overview.

*NEW FILES SHOULD BE WRITTEN IN KOTLIN*

### Dependency Injection

Uses Hilt for dependency injection. Common activity-scoped dependencies are in `ActivityBindingsModule`, while activity-specific ones are in modules like `DynamicStarMapActivityModule`.

### Rendering Pipeline

Layers → AstronomicalSource → Primitives (Point/Line/Text/Image) → OpenGL via `RendererController` /
`SkyRenderer`. See `stardroid-v1/docs/ARCHITECTURE.md` for full detail.

### Coordinate Transformation

`AstronomerModel` maps phone sensor coordinates to celestial RA/Dec via a transformation matrix
derived from zenith and North vectors. See `stardroid-v1/docs/design/sensors.md` for the math.

### Data Flow

```
Raw catalogs → stardroid-v1/tools/Main.java → ASCII protobuf → binary protobuf → stardroid-v1/app/src/main/assets/
                (StellarAsciiProtoWriter)  (AsciiToBinaryProtoWriter)
```

Runtime: Binary files loaded by `AbstractFileBasedLayer`, deserialized into
`ProtobufAstronomicalSource`.

### Adding Dialog Fragments

Dialog fragments are instantiated on demand in the host activity — never stored as fields or
pre-created in `onCreate`. All fragments must be shown via the activity's `showDialog` helper,
which guards against duplicate dialogs after activity recreation (e.g. rotation).

**Pattern for a new dialog:**

1. Create your `DialogFragment` class with `@AndroidEntryPoint` for Hilt-injected dependencies.
2. Add a `public static newInstance()` factory method (use `setArguments(Bundle)` for any data;
   data objects must be `Parcelable` — use `@Parcelize` on Kotlin data classes).
3. Show it from the host activity via showDialog(XyzDialogFragment.newInstance(), XyzDialogFragment.class.getSimpleName())

**Do not:**
- Store dialog fragment instances as activity fields.
- Pass data to a showing fragment via setter methods — use `newInstance()` + Bundle args so the
  data survives configuration changes.

## Code Style

No copyright header on new files.

Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html):

- 100 character line wrap
- Do **not** prefix member variables with `m` (unlike common Android convention)
- Use Java 17 toolchain features

### Strings
Remember to properly escape any text added as Android resource strings (e.g. ' must be escaped
with a single backslash as \'). New strings should be in *US English* - translations to
other locales will be done after features are implemented by a separate pipeline.

### Colors

Never hardcode color integers in Java/Kotlin. Declare in `stardroid-v1/app/src/main/res/values/colors.xml` and
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

- [`StardroidApplication.kt`](stardroid-v1/app/src/main/java/com/google/android/stardroid/StardroidApplication.kt) - Application entry point, Hilt initialization, sensor detection
- [
  `DynamicStarMapActivity.java`](stardroid-v1/app/src/main/java/com/google/android/stardroid/activities/DynamicStarMapActivity.java) -
  Main interactive star map activity
- [
  `AstronomerModel.java`](stardroid-v1/app/src/main/java/com/google/android/stardroid/control/AstronomerModel.java) -
  Coordinate transformation logic
- [`SkyRenderer.java`](stardroid-v1/app/src/main/java/com/google/android/stardroid/renderer/SkyRenderer.java) -
  OpenGL rendering
- [`source.proto`](stardroid-v1/datamodel/src/main/proto/source.proto) - Protocol buffer schema for astronomical
  objects

## Testing

Unit tests: JUnit 4, Robolectric, Mockito, Truth. Instrumented: Espresso.
Structure mirrors main source: `stardroid-v1/app/src/test/` and `stardroid-v1/app/src/androidTest/`.
