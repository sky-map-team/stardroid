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

## Git Worktrees

Five reusable worktrees live under `.worktrees/` in the repo root:

| Worktree | Path | Placeholder branch |
|---|---|---|
| stardroid-alpha | `.worktrees/stardroid-alpha` | `worktree/stardroid-alpha` |
| stardroid-beta | `.worktrees/stardroid-beta` | `worktree/stardroid-beta` |
| stardroid-gamma | `.worktrees/stardroid-gamma` | `worktree/stardroid-gamma` |
| stardroid-delta | `.worktrees/stardroid-delta` | `worktree/stardroid-delta` |
| stardroid-epsilon | `.worktrees/stardroid-epsilon` | `worktree/stardroid-epsilon` |

### Worktree lifecycle

**1. Claim a worktree for a new task** — fetch latest master, then branch from it:
```bash
git fetch origin
git -C .worktrees/stardroid-alpha checkout -b feature/my-task origin/master
```

**2. Reset a worktree when done** — return it to its placeholder branch so the feature branch
can be deleted:
```bash
git -C .worktrees/stardroid-alpha checkout worktree/stardroid-alpha
```
After resetting all affected worktrees, run `/clean-branches` to delete the merged feature
branches. That skill uses `git branch -d` (safe delete) and will skip any branch still checked
out in a worktree.

Each worktree already contains the build-critical files excluded from version control
(`stardroid-v1/local.properties`, `stardroid-v1/app/local.properties`,
`stardroid-v1/app/no-checkin.properties`, keystores, `stardroid-v1/fastlane/play-store-credentials.json`). If you add a new worktree, copy
these files from the main worktree (`stardroid-v1/app/`) before building.

### Deploying

Sky Map is hosted on the Google Play Store and FDroid.
To deploy to the Google Play Store use fastlane.  For example to build and deploy a new internal release:

```
bundle exec fastlane internal
```

Other commands may be found at fastlane/README.md

### Data Generation

`.worktrees/` is listed in `.gitignore` so worktree directories are never accidentally committed.

## Build Flavors

- **gms** - Includes Google Play Services (Analytics, Location). Requires `no-checkin.properties`
  for release builds.
- **fdroid** - Pure open source, no Google dependencies.

Always specify the flavor: use `assembleGmsDebug`, not `assembleDebug`. See the `/build` skill for
all build, test, deploy, and data-generation commands.

## Architecture

See `stardroid-v1/docs/ARCHITECTURE.md` for a full overview.

* New files should be written in Kotlin.

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
## Threading and Concurrency

- **No Raw Threads:** Never use `Thread { ... }.start()` or `new Thread()`. Raw threads are inefficient and difficult to manage/cancel.
- **Background Executor:** For background tasks (e.g. geocoding, I/O), inject the shared `ScheduledExecutorService` provided by `ApplicationModule`.
- **UI Thread:** Use `Handler(Looper.getMainLooper())` or `activity.runOnUiThread` (in fragments) to post results back to the UI thread.
- **Coroutines:** While preferred for new Kotlin code, ensure they are integrated with the existing Hilt-managed scopes if used.


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
