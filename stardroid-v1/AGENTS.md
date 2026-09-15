# stardroid-v1 — Agent Notes

Context for AI coding assistants working in `stardroid-v1/`, the legacy Sky Map app (Java,
Apache-2.0, Android SDK 26–36). See the repository root [AGENTS.md](../AGENTS.md) for branching,
worktrees, and other cross-cutting conventions.

## Module Structure

- **app/** - Main Android application (~171 source files)
- **datamodel/** - Protocol buffer definitions for astronomical objects
- **tools/** - Standalone utilities for converting star catalogs to binary protobuf format

Read specs in `specs/` before undertaking complex investigations, starting with the overview.md
file to know which specs to read.

## Architecture

See `docs/ARCHITECTURE.md` for a full overview.

* New files should be written in Kotlin.

### Dependency Injection

Uses Hilt for dependency injection. Common activity-scoped dependencies are in `ActivityBindingsModule`, while activity-specific ones are in modules like `DynamicStarMapActivityModule`.

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

No copyright header on new files (Apache 2.0 governs; existing Google-authored files retain
their original headers).

Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html):

- 100 character line wrap
- Do **not** prefix member variables with `m` (unlike common Android convention)
- Use Java 17 toolchain features

## Threading and Concurrency

- **No Raw Threads:** Never use `Thread { ... }.start()` or `new Thread()`. Raw threads are inefficient and difficult to manage/cancel.
- **Background Executor:** For background tasks (e.g. geocoding, I/O), inject the shared `ScheduledExecutorService` provided by `ApplicationModule`.
- **UI Thread:** Use `Handler(Looper.getMainLooper())` or `activity.runOnUiThread` (in fragments) to post results back to the UI thread.
- **Coroutines:** While preferred for new Kotlin code, ensure they are integrated with the existing Hilt-managed scopes if used.

## Colors

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

Night-mode variants are red-shifted; brighter = better (mirrors day-mode meaning). Note the
color palette in @docs/design/visual_design.md.

## Key Files

- [`StardroidApplication.kt`](app/src/main/java/com/google/android/stardroid/StardroidApplication.kt) - Application entry point, Hilt initialization, sensor detection
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

This is the shipping legacy app; the definitive reference for existing behavior when porting to
v2. Avoid changes beyond maintenance fixes.

## Testing

Unit tests: JUnit 4, Robolectric, Mockito, Truth. Instrumented: Espresso.
Structure mirrors main source: `app/src/test/` and `app/src/androidTest/`.

Standard Gradle unit tests; remember to specify the flavor (e.g. `testGmsDebugUnitTest`).
