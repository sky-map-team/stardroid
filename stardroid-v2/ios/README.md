# `:ios` — experimental iOS shell (MobiVM / RoboVM)

**Status: EXPERIMENT.** This module compiles v2's shared Kotlin code into a native iOS app with
[MobiVM](https://github.com/MobiVM/robovm), the maintained RoboVM fork that libGDX ships on.
RoboVM compiles JVM bytecode ahead of time to native ARM64 and binds UIKit and the other Apple
frameworks as Java classes, so the pure modules run on iOS unchanged: no Kotlin Multiplatform
migration, no rewrite.

## What it is

One screen: stars (`source-data/stars.csv`), IAU constellation figures, the Sun, Moon and planets
(`MeeusEphemeris`), and the horizon with cardinal points. You drag to look around and pinch to
zoom. Scenes go through the same `:render:api` `SkyRenderer` contract the Android GLES backend
implements.

| Code | Runs on | Role |
|---|---|---|
| `:core:math`, `:core:astronomy`, `:render:api` | shared, unchanged | ephemeris, sky model, projection, scene contract |
| `scene/` — `SkyData`, `SkyScenes`, `LookDirection` | JVM + iOS | catalog parsing, layer building, drag/zoom camera |
| `render/` — `FramePlanner`, `RetainedSkyRenderer` | JVM + iOS | `SkyRenderer` impl → screen-space draw list |
| `SkyView`, `SkyViewController`, `SkyMapApp` | iOS only | CoreGraphics replay of the draw list, gestures, app entry |
| `compat/RoboVmBackports` | iOS only | Java 8 statics RoboVM lacks (see below) |

The renderer is a deliberately small CoreGraphics backend. It draws points, lines and labels,
with depth ordering, magnitude limit, night mode, label scale and declutter. It does **not** draw
images (planet discs, phases), glows or the sky gradient. A GLES/Metal backend is future work.

## Building

Everything except the final native link runs on any OS:

```bash
./gradlew :ios:check        # compile, unit tests, ktlint, and the RoboVM API check (below)
```

On macOS with Xcode installed:

```bash
./gradlew :ios:launchIPhoneSimulator    # build and run in the simulator
./gradlew :ios:launchIOSDevice          # needs a signing identity + provisioning profile
./gradlew :ios:createIPA
```

The Gradle plugin downloads the MobiVM SDK (~200 MB) into `~/.robovm-sdks` on first use.
App configuration lives in `robovm.xml`, `robovm.properties` and `Info.plist.xml`.

## What was verified, and what wasn't

When the module was set up on Linux (no Xcode), the RoboVM 2.3.26 compiler was run directly on
the app, with a fake `xcode-select` so it would accept an iOS target. It compiled all 3,830
classes (every Sky Map class, the Kotlin stdlib, kotlinx-datetime and the UIKit bindings) to
arm64 iOS object code. It then linked 5,198 classes / 70,489 methods into the image and
stopped at the native link, which needs Xcode's `clang`/`ld64` and the iOS SDK.

On macOS (Xcode 26.3, Intel host), `./gradlew :ios:launchIPhoneSimulator` builds, links and
runs the app in the iPhone SE (3rd generation) / iOS 17.5 simulator. It draws the stars,
constellation figures, labels, horizon and cardinal points. **It has not yet been run on a
physical device.**

## RoboVM compatibility: what it took

RoboVM's class library is Android's **Java-7-era libcore**. Four things had to change:

1. **No `java.time`.** kotlinx-datetime's JVM artifact (the time type across
   `:core:astronomy`) is built on it. The `javaTimeJar` task relocates
   [ThreeTen-BP](https://www.threeten.org/threetenbp/) (the JSR-310 backport, same API) from
   `org.threeten.bp` to `java.time`, including its `TZDB.dat` and `ServiceLoader` entry.
   `robovm.xml` adds it to the RoboVM bootclasspath.
2. **No `StringConcatFactory`.** Kotlin compiles string templates to `invokedynamic` on JVM 9+
   targets. `skymap.pure-kotlin` now passes `-Xstring-concat=inline` (plain `StringBuilder`),
   which makes no difference on Android, where D8 desugars the indy form anyway.
3. **No Java 8+ methods on existing classes.** `Boolean.hashCode(boolean)` shows up in every
   Kotlin data class with a `Boolean` property (e.g. `RenderState`), and
   `Math.addExact`/`multiplyExact` in kotlinx-datetime's `Instant` arithmetic. These compile
   fine and crash on the device with `NoSuchMethodError`. `RoboVmBackportTransform` (in
   build-logic) is a Gradle artifact transform, applied **only** to this module's runtime
   classpath. It rewrites those call sites to the identical-signature methods in
   `compat/RoboVmBackports`, which are tested against the JDK originals.
4. **No `java.util.function`, streams or `Optional`.** Nothing reachable uses them today, and
   the check below keeps it that way.

### `checkRoboVmApi`: the Mac-free "does it build for iOS" gate

The RoboVM compiler only warns about missing *classes*; a missing *method* is a run-time crash.
`checkRoboVmApi` (source in `src/apiCheck`) walks the call graph from every
`com.google.android.stardroid` class through the transformed app classpath. It resolves each
JDK class, method and field reference against the real `robovm-rt` jar plus the `java.time`
backport, and fails on anything missing. It runs as part of `./gradlew check`, so a Java 8+ API
creeping into a shared module fails CI on Linux. The report is written to
`build/reports/robovm-api-check.txt`.

When it fails:

- a missing **static** method with a simple Java 8 implementation → add it to
  `RoboVmBackportTransform.BACKPORTED` and `RoboVmBackports` (with a JDK-comparison test);
- anything else → avoid the API in shared code. Only if it's genuinely unreachable at run time,
  add it to `robovm-api-allowlist.txt` **with the reason**. The current entries are
  kotlinx-datetime's optional serialization support and regex named groups.

## Known gaps and next steps

- Run it on the simulator and a device (needs macOS + Xcode). Check the ThreeTen-BP zone rules
  load from the bundle and the CSV resources are found.
- **Share the layers.** The Android layers in `:app/layers` use almost no Android APIs
  (coroutines + kotlinx-datetime). Moving them into a pure module would replace `SkyScenes` and
  bring deep-sky objects, the grid, the ecliptic, meteor showers and satellites along.
  `kotlinx-coroutines-core` would need a pass through `checkRoboVmApi`.
- The catalog: Android reads a Room/SQLite DB. iOS could open the same generated `skymap.db`
  through SQLite (RoboVM ships `libsqlite3` bindings) behind the `:core:catalog` repository
  interface.
- Sensors (CoreMotion → `SkyModel.pointing`) and location (CoreLocation). The observer is
  hard-coded to Greenwich.
- A GPU backend (Metal via RoboVM's bindings, or GLES on iOS's deprecated-but-present OpenGL ES)
  for images, glows and the sky gradient.
- Strings: English only; there's no link to the translation pipeline yet.
