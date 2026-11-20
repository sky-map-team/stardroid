# Detailed Design: Build, Modules, and CI Tooling

**Status: IMPLEMENTED** (slice 1, D20; this doc refreshed 2026-08-02 to match the as-built
graph, 2026-09-23 for the pure modules' move to Kotlin Multiplatform — iOS port phase 0, and
2026-11-15 to make its module graph the one full list of modules).
Records the build scaffolding the other increments assume (G13) and the CI guardrails for the
architecture (D20) and performance (D19).

## Module graph

**This is the one full list of v2's modules.** The README, `AGENTS.md`,
[code-overview.md](../code-overview.md) and [high-level-architecture.md](high-level-architecture.md)
describe the code by folder or layer and link here. When a change adds, removes or rewires a
module in `settings.gradle.kts`, update this table in the same change, and only this table.

One Gradle build makes both apps. The pure core and the shared middle layer are Kotlin
Multiplatform; each app is a platform shell over them, with its own renderer backend:

```
 Android                                   iOS
 :app                                      ios/SkyMap  (Swift, Xcode — not a Gradle module)
  │                                         ├─ links SkyMapKit.framework, built by :app-ios
  │                                         └─ its widgets link SkyMapWidgetKit, by :app-ios-widgets
  ├─ :render:gles1, :render:gles3              └─ :render:metal
  └───────────────────────┬────────────────────────┘
                          │ both use
            :shared:ui, :shared:viewmodels, :shared:layers,
            :shared:model, :shared:surfaces, :data
                          │
            :render:api, :core:*
```

Dependencies point inward only (see [high-level-architecture.md](high-level-architecture.md)).
The table lists each module's project dependencies in its main source sets; test-only ones
(`:core:testing`, `:shared:testing`, `:render:testscene` in `:render:metal`'s tests) are left
out. The plugin decides which platforms a module builds for — see
[Convention plugins](#convention-plugins-build-logic) below.

| Module | Plugin | Depends on | What it holds |
|---|---|---|---|
| `:core:math` | `pure-kmp` | — | `Vector3`, `Matrix3`, `RaDec`, `LatLong`, angles, geometry |
| `:core:astronomy` | `pure-kmp` | `:core:math` | Ephemeris, sky model, sensor-fusion maths, time, rise/set, lunar phase ([core-math-astronomy.md](core-math-astronomy.md)) |
| `:core:catalog` | `pure-kmp` | `:core:astronomy`, `:core:math` | Celestial-object domain model, repository interfaces, locale fallback, name normalization |
| `:core:events` | `pure-kmp` | `:core:catalog`, `:core:astronomy`, `:core:math` | The "tonight's sky" events engine behind widgets, notifications and the info card (D70) |
| `:core:text` | `pure-kmp` | — | `TextSource`, the one interface shared logic asks for translated text through; each platform maps the ids to its own resources ([ios-platform-surfaces.md](ios-platform-surfaces.md) §10) |
| `:core:testing` | `pure-kmp` | — | Test support: a Truth-shaped `assertThat` for multiplatform tests (Truth is JVM-only) |
| `:render:api` | `pure-kmp` | `:core:math` | The renderer contract: primitives, camera, the shared projection and vertex builders ([render-api.md](render-api.md)) |
| `:render:testscene` | `pure-kmp` | `:render:api` | The seeded synthetic scene every renderer harness draws |
| `:render:gles1` | `android-library` | `:render:api` | OpenGL ES 1.0 backend, written to match v1's rendering |
| `:render:gles3` | `android-library` | `:render:api` | OpenGL ES 3.0 backend; a flag and a setting choose it over `:render:gles1` ([render-gles3.md](render-gles3.md)) |
| `:render:metal` | `ios-library` | `:render:api` | Metal backend in Kotlin/Native; its `.metal` shaders mirror `:render:gles3`'s GLSL by hand ([render-metal.md](render-metal.md), D128) |
| `:render:metal-harness` | `ios-library` | `:render:metal`, `:render:testscene` | The `SkyMapHarness` framework that `ios/RendererHarness` hosts: the Metal backend drawing the test scene. A dev tool, not shipped |
| `:data` | `kmp-android-library` + `kmp-room` | `:core:catalog`, `:core:astronomy`, `:core:math` | The Room catalog store implementing `:core:catalog`, and the satellite elements fetcher, for Android and iOS ([data-layer.md](data-layer.md)) |
| `:data:generator` | `pure-kotlin` | `:core:catalog` | Build-time JVM tool: the deterministic catalog-DB generator over `source-data/` (sqlite-jdbc, kotlinx-serialization) |
| `:shared:model` | `pure-kmp` | `:render:api`, `:core:astronomy`, `:core:math` | The app's state contracts (`Settings`, `StartupState`, `ExperimentConfig`, `Analytics`, location, geocoding, the sensor sources), the controllers over them (time, location, startup routing), orientation smoothing, and the DataStore-backed `Settings` and `StartupState` both platforms use |
| `:shared:layers` | `kmp-android-library` | `:shared:model`, `:data`, `:render:api`, `:core:astronomy`, `:core:catalog`, `:core:math` | The sky layers and `LayerRegistry`; `RenderBinder` feeds them, the camera and the render state into a `SkyRenderer` ([layers-and-app.md](layers-and-app.md)) |
| `:shared:viewmodels` | `kmp-android-library` | `:shared:layers`, `:shared:model`, `:data`, `:core:events` | The screens' ViewModels, on androidx.lifecycle's multiplatform `ViewModel`, and the state they compute |
| `:shared:ui` | `kmp-compose` | `:shared:viewmodels`, `:render:api`, `:core:catalog` | The screens both apps draw, in Compose Multiplatform (D134). Their strings are Android-format `values-*/` resources in `src/commonMain/res`, which `tm` translates like `:app`'s |
| `:shared:surfaces` | `pure-kmp` | `:core:events`, `:core:text` | What notifications and widgets decide: which alert is due tonight and when it fires, the Moon widget's timeline, disc geometry and palette, and the object links widgets open. Posting and drawing stay with each platform ([ios-platform-surfaces.md](ios-platform-surfaces.md) §5, §6) |
| `:shared:testing` | `pure-kmp` | `:shared:model`, `:core:catalog` | Test support: fakes of the shared interfaces, for `:shared` and `:app` tests |
| `:app` | `android-app` | `:shared:{ui,viewmodels,layers,model,surfaces}`, `:data`, `:render:{api,gles1,gles3}`, `:core:{math,astronomy,catalog,events}`; `:render:testscene` in debug builds | The Android app: `MainActivity` and the nav host over `:shared:ui`'s screens, Hilt wiring, sensors, location, camera, widgets, notifications, announcements, satellite scheduling, the `gms`/`fdroid` flavors |
| `:app-ios` | `ios-compose` | `:shared:{ui,viewmodels,layers,model,surfaces}`, `:app-ios-widgets`, `:data`, `:render:metal`, `:core:text` | The iOS app's Kotlin, built as the `SkyMapKit` framework that `ios/SkyMap` links: the app graph, the iOS platform edges (Core Motion, Core Location, display link, notifications, geocoding, satellites, tip jar) and the Compose root |
| `:app-ios-widgets` | `ios-library` | `:shared:surfaces` | The iOS widget extension's Kotlin, built as the `SkyMapWidgetKit` framework: the widget timelines over the shared astronomy, the App Group store, and the widget strings' export. No Compose, Metal or database, for the extension's memory limit ([ios-platform-surfaces.md](ios-platform-surfaces.md) §6) |
| `:konsist` | `pure-kotlin` | — | Test-only: the architecture gate (D20) |

**Outside Gradle:**

- `ios/SkyMap` — the iOS app's Swift shell, an Xcode project that XcodeGen generates from
  `project.yml`. It holds the entry point, Swift adapters for Firebase, StoreKit, the
  notification center and background refresh, `Info.plist` and the privacy manifest, the UI
  tests, and the widget extension (`Widgets/`, linking `SkyMapWidgetKit`). A build phase runs
  Gradle for `SkyMapKit` and the catalog DB. `:app-ios` and `ios/SkyMap` are two halves of one
  app, because Gradle can't build an iOS app and Xcode can't build Kotlin. [`ios/README.md`](../../ios/README.md) covers building it.
- `ios/RendererHarness` — the same kind of shell, for `:render:metal-harness`.
- `build-logic/` — the convention plugins below: an included build, JVM only.
- `source-data/` — the catalog's CSV/JSON sources; its README documents the formats.
- `tools/` — maintenance scripts, including the asset-licence check CI runs.

The catalog generator originally sketched here as `:tools:catalog-gen` landed as
`:data:generator` (D34): `:data` wires it through a dedicated `catalogGenerator` configuration
and a `GenerateCatalogDbTask` whose output is registered as a per-variant generated asset.

## Convention plugins (`build-logic/`)

Convention plugins keep module build scripts to a few lines and make module *kind* a
declaration, not a copy-paste of config. They are the structural half of D20. The module table
above says which one each module applies. The multiplatform plugins all build on
`skymap.kmp-base` (the iOS targets, the `commonTest` stack, and the skip rule for Macs without
Xcode), which no module applies directly; every plugin lints through `skymap.ktlint`.

| Plugin | Builds for | Applies |
|---|---|---|
| `skymap.pure-kmp` | JVM + iOS | `kotlin("multiplatform")`, kotlin.test + `:core:testing`, **no Android plugin** |
| `skymap.kmp-android-library` | Android + iOS | `kotlin("multiplatform")` with AGP's multiplatform Android target (`com.android.kotlin.multiplatform.library`) |
| `skymap.kmp-compose` | Android + iOS | `skymap.kmp-android-library` plus the Compose compiler and Compose Multiplatform, with the module's strings written as Android resources (D134) |
| `skymap.ios-library` | iOS | `kotlin("multiplatform")` with the iOS targets only, kotlin.test + `:core:testing` |
| `skymap.ios-compose` | iOS | `skymap.ios-library` plus the Compose compiler and Compose Multiplatform, which copies `:shared:ui`'s resources into the app bundle |
| `skymap.pure-kotlin` | JVM | `kotlin("jvm")`, JUnit5/Truth, **no Android plugin** |
| `skymap.android-library` | Android | `com.android.library` + Kotlin, common Android config |
| `skymap.android-app` | Android | `com.android.application` + Kotlin + Compose + Hilt + flavors |
| `skymap.kmp-room` | (adds to a KMP module) | KSP + Room for every target, Room's Gradle plugin, checked-in exported schema (`data/schemas/`) |

Because neither pure plugin ever puts the Android SDK on the classpath, `import android.*` in
a pure module is a **compile error** — the primary, structural guarantee that the pure/Android
boundary holds (D20 layer 1). A pure module that needs Android has applied the wrong plugin,
which is the visible mistake. Under `skymap.pure-kmp` the same holds for the JDK: `commonMain`
is also compiled for iOS, so `import java.*` there is a compile error too. `:data`
(`skymap.kmp-android-library`) is not a pure module — its `androidMain` sees the Android SDK —
but its `commonMain` is held to the same two rules by the same compiler.

Shared versions live in a Gradle **version catalog** (`gradle/libs.versions.toml`): Kotlin,
AGP, Compose BOM, Hilt, Room, DataStore, Glance + WorkManager (widgets, D69),
kotlinx-datetime/serialization/coroutines, Coil, Konsist, Truth, Turbine (Flow testing).
(Robolectric and androidx-benchmark, listed in earlier drafts, are not in use — see the
performance-gate note below.)

**SDK levels.** v2 uses `minSdk 28`, `compileSdk 36`, `targetSdk 36`, set once in
`gradle/libs.versions.toml` (`androidMinSdk` etc., plus `jdk`) and read by the convention plugins
(`android-library`, `android-app`, `kmp-android-library`, `pure-kmp`, `pure-kotlin`). v1's "SDK 26–36" range does not apply to
v2 — see the note in the repo-root `AGENTS.md`.

D9 was originally decided as a deliberate raise to `minSdk 29` over v1's `minSdk 26`:
sub-Android-10 devices are ~1.6% of installs, and letting them keep v1 via in-place upgrade
looked like the cheaper way to avoid supporting an older API surface long-term (see #1033 for
what that cost looks like in practice). Revisiting it (issue #1029) found nothing in v2 — no
library in `gradle/libs.versions.toml` — actually *requires* API 29; the floor was a maintenance
boundary, not a technical dependency. D9 is now: **`minSdk 28`, Android 9 support is
best-effort and unsupported.**

- No future code change needs to preserve API 28 compatibility. Write code assuming API 29+ is
  available, exactly as before; don't add version guards or test against API 28 on its account.
- The moment a change actually needs an API 29+ (Android 10+) feature, bump `minSdk` back to 29
  (or whatever the floor is by then) rather than working around it — that was the whole point
  of dropping the guarantee instead of the number staying frozen forever.
- **One approved exception:** `MainActivity.kt`'s `window.isNavigationBarContrastEnforced = false`
  already required API 29 and lint only caught it once the floor dropped below that. Rather than
  raise `minSdk` back to 29 for one cosmetic nav-bar call, it's guarded with a `Build.VERSION_CODES.Q`
  check — a maintainer-approved one-off, not a precedent. Do not add further `Build.VERSION_CODES`
  forks for Android 9 on the strength of this exception; the default is still to bump `minSdk`
  when a real API 29+ dependency shows up.
- CI only builds/tests at the declared floor and up, so API 28 gets no dedicated test coverage;
  it runs on a best-effort basis only, on whatever happens to still work.
- If Android-9-specific bug reports start showing up, the fix is to raise `minSdk` back to 29,
  not to patch around API 28 — v1 remains the only actively Android-9-supported version.

## Kotlin Multiplatform — the shared core (iOS port, phase 0)

The pure modules other than the JVM tools are Kotlin Multiplatform, so the iOS app can share the
math, astronomy, catalog, events and renderer-contract code rather than reimplement it. For
Android nothing changes: the Android modules and `:data:generator` consume each module's `jvm`
target exactly as they consumed the old `kotlin("jvm")` jar.

- **Targets:** `jvm`; `iosArm64` (devices) and `iosSimulatorArm64` (Apple-silicon simulators,
  including CI's); `iosX64` for simulators on Intel Macs, to be dropped when Kotlin retires the
  x86_64 Apple targets.
- **Layout:** `src/commonMain` (all production code), `src/commonTest` (all tests). Platform
  code appears only as an `expect`/`actual` pair, with the JVM side in `jvmMain` and the iOS
  side in `appleMain` — for example `NameNormalizer`'s canonical decomposition
  (`java.text.Normalizer` on the JVM, Foundation's `decomposedStringWithCanonicalMapping` on
  iOS). The modules with an Android target use `androidMain` and `iosMain` instead.
- **Tests run on every target.** Truth is JVM-only, so `:core:testing` provides a
  multiplatform stand-in with Truth's names and semantics for exactly the subset in use (a
  suite moved over by changing its imports; its own tests pin that each assertion can fail).
  Grow it when a test needs more; don't turn it into a general library.
- **Search agrees across platforms.** The catalog DB stores names normalized on the JVM at
  generation time, but each platform normalizes search queries with its own Unicode tables.
  `NameNormalizerCorpusTest` (`:core:catalog`) re-normalizes every catalog name on every
  target and compares it with the JVM result. That result is exported by
  `:data:generator:exportNameCorpus`, which the test tasks run first, passing the file's path
  in `SKYMAP_NAME_CORPUS` (`SIMCTL_CHILD_SKYMAP_NAME_CORPUS` for the simulator, since
  `simctl` forwards only prefixed variables). `:core:testing`'s `environmentVariable` /
  `readTextFile` are the common-code half of that pattern.
- **Xcode:** compiling iOS klibs needs only the Kotlin/Native toolchain, but linking and running
  test binaries needs a full Xcode. On a Mac with only the Command Line Tools, `check` skips
  the iOS link/test tasks with a warning rather than failing, so Android-only setups keep
  working; the macOS CI job always runs them.

## Build flavors (D3)

`:app` carries the `gms` / `fdroid` product flavors from v1. Flavor-specific source sets supply
the analytics, location, and (future) attestation implementations behind interfaces; the
`fdroid` flavor pulls no Play Services / Firebase. No proprietary SDK appears in any pure or
`fdroid`-path module.

## CI guardrails

### Architecture enforcement (D20, layer 2)

A Konsist test (the `:konsist` module — an ordinary JVM unit test that fails the build on
violation; `konsist/src/test/kotlin/.../ArchitectureTest.kt`) asserts the invariants that the
compiler alone can't fully cover:

- a non-vacuity guard (the path regex actually matches files, so the gate can't silently
  rot);
- no type in a pure module imports `android.*` / `androidx.*` (plus a catch for
  `*.android.*` artifacts like coroutines-android);
- the module dependency arrows point inward only, via an in-project import **allow-list**
  (`math`/`astronomy`/`catalog`/`events`/`render.api`, plus `testing` for test sources),
  because `:app`'s namespace is the package root and a denylist would miss it.
- no common source set of a multiplatform module imports `java.*` / `javax.*` — every source
  set of a `pure-kmp` module except `jvm*`, and of `:data` except `android*`.

The `:konsist` build file registers every `**/*.kt` as a task input because Konsist scans the
filesystem and would otherwise sit UP-TO-DATE and silently skip. (The 2026-08-02 gap — the gate
predating `:core:events` — was closed with the KMP conversion.)

Layer 3 — a KMP-readiness denylist of JVM-only packages inside core logic — was deferred until
KMP work began (D20). It began, and the layer turned out to be structural rather than a list:
`commonMain` compiles for iOS, so the compiler rejects JDK APIs there, and Konsist carries the
import rule above as the executable spec. JVM-only code goes in `jvmMain` behind an
`expect`/`actual`. `java.nio.FloatBuffer` legitimately lives in the GL backends (the GL upload
boundary), not in pure modules.

### Reproducible catalog DB (D3, [catalog-and-schema.md](catalog-and-schema.md))

`:data:generator` produces `skymap.db` deterministically (fixed page size, no timestamps,
stable row order). The generator's JVM test suite (`CatalogDbGeneratorTest`) runs against the
**real** checked-in Room schema and `source-data/` and asserts byte-identical output across
runs, identity-hash/user_version match with the exported schema, FTS diacritic folding, and
content spot checks — the F-Droid build-from-source guarantee, enforced per commit via
`check`. The generated DB is a build artifact, never checked in.

### Performance gate (D19) — as built

The androidx-benchmark module sketched here was not built. What exists instead:

- `testscene/RendererTestActivity` — a dev harness driving the real catalog or a seeded
  ~100k-point synthetic scene, doubling as the v1/v2 screenshot-comparison rig (D31). It
  lives in `app/src/debug/`, with its own debug manifest, so it never ships in a release
  APK or AAB (audit-2026-08 H1); `androidTest` builds against the debug variant, so
  `RendererPerfTest` still sees it.
- `RendererPerfTest` (`:app` androidTest) — 2 s warmup, 5 s measurement. **In CI it is a
  smoke gate** (≥ 5 frames on the software-rendered AVD); the real D19 target (≥ 30 fps at
  100k points on a Pixel 3a) is a release-time manual check, and the test WARNs rather than
  fails below target.

The trigger logic stands: missing the pan/zoom target on real hardware is the cue to add a
backend region index before relying further on D13's no-point-culling stance.

### CI wiring

The workflow lives at the **monorepo root** (`.github/workflows/android.yml`, working
directory `stardroid-v2/`): a `check` job (unit tests, ktlint, Konsist, generator gate) plus
connected instrumented tests on API 34 and 35 emulators for every PR, and a macOS job that runs
the pure-kmp modules' tests on the iOS simulator (the Linux `check` job cannot build Apple
targets, so it covers them on the JVM only).

## Testing toolchain (summary; per-area detail lives in each design doc)

| Layer | Tools |
|---|---|
| Pure modules (`:core:*`, `:render:api`) | kotlin.test + `:core:testing`'s Truth-shaped assertions in `commonTest`, run on the JVM (JUnit 5) and the iOS simulator; kotlin property tests; golden fixtures vs. v1 (D6) |
| Flow behavior | Turbine |
| `:data` (Room) | tests against real SQLite (FTS4 + Room invalidation need it), so on Android they are **instrumented** (`connectedAndroidDeviceTest`); the ones in `commonTest` also run on the iOS simulator. Fixture pack; generator golden test on the JVM; satellite logic as JVM host tests |
| ViewModels (`:shared:viewmodels`) | `commonTest` with `:shared:testing`'s fakes (the point of the decomposition), run on the JVM and the iOS simulator |
| Compose screens (`:shared:ui`) | No Compose UI tests on Android yet; the screens' non-UI logic (text, HTML, help links, rail slots, the diagnostics report) has `commonTest` suites. On iOS, XCUITests in `ios/SkyMap/UITests` drive most screens of the same shared UI end to end |
| Architecture | Konsist (D20) |
| Performance | `RendererPerfTest` smoke gate in CI; real D19 budget checked manually at release (see above) |
| Renderer visual | dev-only test activity + v1/v2 screenshot comparison (intentional D12 diffs noted) |
