# Sky Map v2 — Code Overview

**Status:** snapshot of the codebase as of 2026-08-05 (master, through the 2.0.0-alpha04
"Apollo" release prep). This document supplements the per-area design docs in
[design/](design/) with a single top-down tour of what actually exists, where it lives, and
which design docs are still the authoritative reference for each area. It repeats as little as
possible — where a design doc is current, this doc just points at it.

Related reading order for a newcomer: this doc → [design/high-level-architecture.md](design/high-level-architecture.md)
→ the per-area doc for whatever you're touching → decisions.md for the "why"
behind any surprising choice (code comments cite decision ids like `D33` throughout).

---

## 1. What this is

Sky Map v2 is a ground-up Kotlin rewrite of the v1 Java planetarium app: a full-screen OpenGL
sky map driven by device sensors, with catalog-backed and computed layers (stars, DSOs,
constellations, solar system, meteor showers, grid/ecliptic/horizon), search, time travel,
object info cards, a gallery, diagnostics, settings, onboarding, home-screen widgets, and
notifications. Single-activity Jetpack Compose app, minSdk 29, targetSdk 36, two flavors
(`gms` with Firebase/Play Services, `fdroid` fully FOSS).

### Where the moving parts are

- **master** — everything through camera-AR mode (#67), the UI-nit pass (#69), localization
  (#70), the widgets/notifications stickiness stack (#72–74), data-pack design (D79), and the
  experiment-flag work that gates camera-AR/share alongside widgets/notifications (#76, D80).
  Also merged: a "disable unreleased features" pass that flips all five `Experiment` flags
  (moon widget, tonight widget, notifications, camera-AR, share) to default-off pending
  announcement — the code is complete and live behind the gate, not removed (see §6).
- **2.0.0-alpha04 "Apollo"** is the current release-prep tip.

### Documentation health

| Doc | Status |
|---|---|
| [design/high-level-architecture.md](design/high-level-architecture.md) | Current at the architecture level; still the best statement of intent |
| [design/core-math-astronomy.md](design/core-math-astronomy.md), [design/render-api.md](design/render-api.md), [design/catalog-and-schema.md](design/catalog-and-schema.md), [design/data-layer.md](design/data-layer.md) | Current; post-doc additions (Icon primitive, Glow mesh, rise/set solver, lunar phase) are recorded in decisions D35/D40/D50/D51 |
| [design/screens-and-startup.md](design/screens-and-startup.md), [design/map-hud.md](design/map-hud.md), [design/ux-polish.md](design/ux-polish.md) | Current |
| [design/camera-ar-mode.md](design/camera-ar-mode.md) | Current — shipped (D64 + AR-track D67–D71), now also gated by `CAMERA_AR`/`SHARE_SKY` (D80) |
| [design/widgets-and-notifications.md](design/widgets-and-notifications.md) | Current — shipped (D75–D77), now default-off pending announcement (see §6) |
| [design/color-scheme-proposal.md](design/color-scheme-proposal.md) | Current — accepted and implemented (D73); kept as the rationale and before/after reference |
| [design/sensor-correction-model.md](design/sensor-correction-model.md) | Reference, not a design — the error physics behind drag-to-align (D68) |
| [design/ephemeris-accuracy.md](design/ephemeris-accuracy.md) | Split status: §1–2 are a reference audit whose precession fix shipped (D84); §3's Astronomy Engine evaluation is proposed and undecided |
| [design/data-packs.md](design/data-packs.md) | Design only, not implemented (D79) |
| [design/localization.md](design/localization.md) | Proposed; the shipped localization work (D78) is 31 salvaged `values-*` locales plus the split help document — see §7 |
| [design/layers-and-app.md](design/layers-and-app.md) | Current (status header refreshed 2026-08-03; the body's `AppGraph` sketch is superseded by Hilt per D59, which the header notes) |
| [design/build-and-tooling.md](design/build-and-tooling.md) | Refreshed 2026-08-03 to the as-built state: 10-module graph, `:data:generator`, `:konsist`, the smoke-not-benchmark perf gate, CI at the monorepo root |
| [README.md](README.md) | Groups every doc as built / proposed / reference, tracks implementation slice-by-slice, and carries the standing "Not built" list; also lists [improvements-over-v1.md](improvements-over-v1.md) (release-notes/store-copy source), which this doc does not duplicate |

decisions.md runs D1–D84 with no gaps or duplicates as of this writing. An earlier draft of
this doc warned that D67–D71 existed twice (an AR-track set and a mainline set assigned on
parallel branches) — the mainline decisions were since renumbered to D75–D81, so that hazard
no longer applies.

---

## 2. Module graph and build structure

Ten Gradle modules (`settings.gradle.kts`), split hard into **pure Kotlin** (no Android SDK on
the classpath — `import android.*` is a compile error) and **Android**:

| Module | Type | Main / test LOC | Purpose |
|---|---|---|---|
| `:core:math` | pure | 405 / 477 | `Vector3`, `Matrix3`, `RaDec`, `LatLong`, angles, geometry |
| `:core:astronomy` | pure | 1,197 / 1,001 | Ephemeris, sky model, sensor fusion math, time, rise/set, lunar phase |
| `:core:catalog` | pure | 592 / 245 | Celestial-object domain model, repository interfaces, locale fallback, name normalization |
| `:core:events` | pure | 368 / 140 | The "tonight's sky" events engine behind widgets/notifications (D70) |
| `:render:api` | pure | 614 / 509 | Renderer contract: primitives, camera, **the shared projection** |
| `:data:generator` | pure (build-time JVM tool) | 807 / 429 | Deterministic catalog-DB generator over `source-data/` |
| `:render:gles1` | Android lib | 2,292 / 1,021 | OpenGL ES 1.0 backend implementing `:render:api` |
| `:data` | Android lib | 1,058 / 1,035 | Room catalog store implementing `:core:catalog` |
| `:app` | Android app | 19,572 / 6,583 | Compose UI, ViewModels, Hilt, sensors, location, widgets, notifications |
| `:konsist` | test-only | — | Architecture gate (D20) |

Dependency arrows point inward only: `:app → {:render:*, :data, :core:*}`,
`:render:gles1 → :render:api → :core:math`, `:data → :core:*`. Pure modules use constructor
injection only (no Hilt/Koin) so a KMP conversion is a build-file change — this was a stated
design goal (high-level-architecture.md) and it has held: the only JVM-only import in any pure
module's main sources is `java.text.Normalizer` in `core/catalog/.../NameNormalizer.kt`.

### Convention plugins (`build-logic/`)

Four plugins carry all shared build config (see build-and-tooling.md for rationale):

- `skymap.pure-kotlin` — `kotlin("jvm")` + ktlint + JUnit 5/Truth test stack, toolchain 17.
  Applying this plugin *is* the purity enforcement: no Android SDK on the classpath.
- `skymap.android-library` / `skymap.android-app` — AGP config (compileSdk 36, minSdk 29),
  the app variant adds Compose, KSP, Hilt (wired automatically), ktlint.
- `skymap.android-room` — KSP + Room, checked-in schema JSON at `data/schemas/` (the exported
  schema's identity hash must match the generated DB for `createFromAsset` validation).

### Flavors and the platform seam

Dimension `sourciness`: `gms` (Firebase analytics/config, fused location) vs `fdroid` (no
Google bits). The entire seam is **one object, `FlavorEdges`**, defined once per source set
(`app/src/gms/kotlin/.../FlavorEdges.kt`, `app/src/fdroid/kotlin/.../FlavorEdges.kt`) and
consumed by Hilt's `AppModule` — no per-flavor Hilt modules. It supplies `Analytics`
(Firebase adapter vs no-op), `ExperimentConfig` (Remote Config vs static defaults),
`LocationProvider` (fused-with-platform-fallback vs platform), and the per-flavor analytics
opt-in default (gms opt-out, fdroid opt-in).

### Catalog DB generation

`source-data/` (see its README — the best doc for the data formats) holds the harvested v1
catalog as CSV/JSON: `stars.csv`, `dso.csv`, `constellations/iau.json`, `objects.json`,
`types.json`, plus 31 locales of names and 30 of info cards. The `:data:generator` tool parses
Room's **exported schema JSON** and executes Room's own CREATE statements (including the
identity-hash insert), producing a byte-for-byte deterministic `skymap.db` — an F-Droid
reproducibility requirement. The Gradle task `:data:generateCatalogDb` registers its output as
a generated asset per variant; the DB is never checked in. `NameNormalizer` is shared between
generator and app via `:core:catalog` so normalized names cannot drift (D33).

### Testing and CI

- `./gradlew check` = unit tests (JUnit 5 + Truth), ktlint, and the Konsist gate. Must pass
  before any commit.
- **Konsist gate** (`konsist/src/test/kotlin/.../ArchitectureTest.kt`): non-vacuity guard, "no
  Android imports in pure modules", and an inward-only-deps allow-list. *Known gap:* the path
  regex and allow-list predate `:core:events`, so that pure module is currently unguarded.
- **Generator reproducibility gate** (`data/generator/src/test/.../CatalogDbGeneratorTest.kt`):
  runs against the real schema + source-data; asserts byte-identical output across runs,
  identity-hash match, FTS diacritic folding, plus content spot checks.
- **Instrumented**: `:data` androidTest against real SQLite (FTS4 + Room invalidation);
  `:app`'s `RendererPerfTest` — currently a smoke gate (5 frames on the CI emulator); the real
  D19 target (30 fps at 100k points on a Pixel 3a) is verified manually at release time.
- **CI** lives at the *monorepo root*: `.github/workflows/android.yml` runs `check` plus
  connected tests on API 34/35 emulators for every PR.
- Release: R8 minification on (empty `proguard-rules.pro` — all deps ship consumer rules);
  fastlane lanes for internal → alpha → beta → production; version code continues v1's
  sequence.

---

## 3. The pure core

### `:core:math` and `:core:astronomy`

Ported from v1 with golden tests against v1 outputs (D6/D25). `Ephemeris` is the swappable
interface (`geocentricPosition`, `topocentricPosition`, `phaseAngleDeg`, `magnitude`,
`earthDistanceAu`, `maxAngularVelocityDegPerDay`); `KeplerianEphemeris` is the v1-ported
implementation — a VSOP87/ELP one could slot in behind it. Time currency is
`kotlinx.datetime.Instant` throughout. Beyond the port: `RiseSet.kt` (Astronomical Almanac
hour-angle iteration, works for ephemeris bodies *and* fixed RA/Dec, D50/D51), `LunarPhase.kt`
(analytic next-phase-event, replacing v1's stepping loop), `MoonWidgetModel.kt`.

`SkyModel.kt` is the heart of pointing: `LocalFrame` (observer north/east/up in equatorial
coordinates, with a magnetic pair pre-rotated by declination — v1's trick of rotating the
*celestial axes* rather than correcting each sensor reading), and
`pointing(localFrame, orientationMatrix, viewDirection)` mapping the phone frame onto the sky.
It is entirely pure; sensors are an app-side edge.

### `:core:catalog` and `:core:events`

`CatalogRepository` (Flow-returning interfaces), the object/type domain model, `LocaleSpec`
with its fallback chain (`pt-BR → pt → en → ""`), `NameNormalizer`. `:core:events` holds
`TonightSky` — twilight window, well-placed planets, shower peaks vs moonlight, moon phase
events — consumed by widgets and notifications (see
[design/widgets-and-notifications.md](design/widgets-and-notifications.md)).

---

## 4. The renderer

Authoritative design: [design/render-api.md](design/render-api.md). Short version:

### `:render:api` — the contract (pure)

`SkyRenderer` is three thread-safe methods: `submit(layerId, scene?)` (replace a whole layer's
immutable `LayerScene`; null removes), `setCamera(camera)`, `setRenderState(state)`. This is
the D13 "retained scenes + per-frame camera" model: producers publish complete scenes only when
*their* content changes; the camera changes every frame. Five primitive kinds — points (with
`Stellar(magnitude, colorIndex)` vs `Fixed` vs `Icon` appearance, D12), lines, world-anchored
images, screen-space labels (with declutter priority), and glow meshes. Positions are unit
geocentric vectors, sizes are dp, colors abstract `Rgba`; night mode is the backend's job.

The single most port-relevant decision (D21): **the view-projection pipeline lives in the pure
module.** `SkyProjection`/`Matrix4` build the byte-identical matrix used by the GL backend
(`glLoadMatrixf`) and by every Compose overlay that needs sky↔screen math — the search arrow
(`ui/search/SearchGeometry.kt`) and tap-to-identify (`ui/objectinfo/IdentifyGeometry.kt`)
invert the same projection. FOV spans the *short* viewport side (portrait/landscape parity).

### `:render:gles1` — the backend

`GLSkyRenderer` implements `SkyRenderer` + `GLSurfaceView.Renderer`; cross-thread state is
immutable snapshots swapped atomically, with per-primitive-family GPU caches keyed by scene
identity so unrelated state changes don't force rebuilds. Fixed-function GLES1 drawers:
`PointDrawer` (native `glPointSize` — zoom-invariant for free), `LineDrawer` (+
`GreatCircleSubdivision`), `ImageDrawer` (dual day/night textures), `IconDrawer`,
`GlowDrawer` (additive Gouraud bands), `SkyGradientDrawer` (v1's SkyBox dome),
`CameraScrimDrawer` (AR video dimmer). Labels are text-to-texture: `android.graphics.Canvas`
rasterizes white-on-transparent into atlas pages (`LabelAtlasPacker`), tinting happens at draw
time via `glColor4f`, and `LabelDeclutterer` does per-frame priority decluttering. Notably,
`StellarStyler`, `GreatCircleSubdivision`, `LabelAtlasPacker`, and `LabelDeclutterer` are
GL-free pure Kotlin with plain JUnit tests — backend policy, not GL code.

Render loop: `RENDERMODE_WHEN_DIRTY` (D23); the app-side `RenderConnector` is the only owner
of `requestRender()`. Perf harness: `testscene/RendererTestActivity` (drives the real catalog
or a seeded 100k-star synthetic scene; `app/src/debug/`, never in a release build) +
`RendererPerfTest`.

---

## 5. Data storage

Authoritative design: [design/catalog-and-schema.md](design/catalog-and-schema.md) and
[design/data-layer.md](design/data-layer.md). Key facts:

- **Room catalog** (`data/src/main/kotlin/.../data/`): pack-based provenance (bundled pack
  `"core"`; `PackDao.applyPack/removePack` is the future download path), hierarchical object
  types as *data* (`galaxy.spiral` inherits rendering from `galaxy`), deliberately no foreign
  keys (cross-pack references must dangle gracefully), FTS4 external-content name search with
  diacritic folding, year-agnostic meteor-shower activity windows, constellation figures as
  vertex rows. Locale fallback is whole-object via `LocaleSpec.fallbackChain`, resolved
  Kotlin-side in `RoomCatalogRepository`.
- **Bundled DB**: `createFromAsset("skymap.db")` with D24 recovery (probe; on failure delete
  and re-copy once). The DB holds **no user state** — that invariant is what makes wholesale
  replacement and recovery safe.
- **Search**: word-prefix FTS with operator-injection-proof tokenization; ranked by
  whole-name-prefix, primary-ness, brightness; moons resolve to their parent's position;
  `planet/*` ids bridge to live ephemeris positions (`SolarSystemIds.kt`).
- **Settings** (`settings/Settings.kt`, `DataStoreSettings.kt`): one Preferences DataStore
  file (`"settings"`, shared with startup state); everything is `Flow<T>` + suspend setters;
  enums stored by name with unknown-value fallback; `IOException` → empty preferences. v1
  SharedPreferences deliberately not migrated (D1).
- **Startup state** (`startup/DataStoreStartupState.kt`): EULA version int, warm-welcome/
  what's-new seen versions, sensor-warning suppression — consumed by `StartupRouter` (v1's
  gating ported verbatim, D48).

---

## 6. The app shell

Authoritative designs: [design/layers-and-app.md](design/layers-and-app.md),
[design/screens-and-startup.md](design/screens-and-startup.md),
[design/map-hud.md](design/map-hud.md), [design/ux-polish.md](design/ux-polish.md).

### DI and wiring

`di/AppModule.kt` holds the whole singleton graph (Hilt since D59): settings, startup state,
the shared **clock bus** (`TimeController`, D42) and **location bus** (`LocationController`,
D44), sensors, sound effects, and the `FlavorEdges`-supplied platform services.
Suspend-initialized state (catalog open, layer registry) lives in `CatalogAccess` — a
mutex-guarded first-use open with recovery.

The app's locale is deliberately *not* one of the captured singletons: `locale/LocaleSource`
re-reads the configuration per request (`current`) and re-emits it on a configuration change
(`specs`). A language switch recreates the activities but not the process, so a captured
`LocaleSpec` would leave every catalog-sourced string — names, map labels, info-card prose —
in the old language until the process happened to die.

`ui/MainActivity.kt` is the single activity: it owns the `GLSurfaceView` + `GLSkyRenderer`,
and `RenderBinder` bridges Flows → renderer inside `repeatOnLifecycle(STARTED)`: camera and
render state from `MapViewModel`, and per-layer
`enabled(id).flatMapLatest { on -> if (on) layer.scenes() else flowOf(null) }`. The GL surface
is passed *into* the composition (`AndroidView` in `MapScreen`); scene data never flows through
Compose. `ui/SkyMapNavHost.kt` has 8 destinations (map, welcome, settings, gallery,
diagnostics, help, what's new, calibration); search/time-travel/object-info/location/layers
stay map-owned dialogs and sheets.

### Layers

`layers/SkyLayer` is `id` + `depth` + `scenes(): Flow<LayerScene>`. `LayerRegistry` builds
eight: three `CatalogLayer` instances differing only in `PrimitiveMapping` policy (stars →
stellar points, DSOs → tinted type icons resolved by walking up the type hierarchy,
constellations → figure lines), plus computed Grid, Ecliptic, Horizon, SolarSystem (Earth-
distance-sorted for eclipse occlusion, D18/D54 topocentric Moon), and MeteorShowers. Styling
is layer policy from `SkyColors` (D40), never DB data. Each layer re-emits only on the inputs
it cares about — the stars layer ignores the clock entirely; the solar-system layer re-submits
only when a body could have moved past a visible threshold.

### The map screen and sensor pipeline

`MapViewModel` (pure JVM-testable — no GL or Android types) is the junction: SENSOR mode maps
`OrientationSource` matrices through `SkyModel.pointing` into a `SkyCamera`; MANUAL mode ports
v1's drag/stretch/rotate/fling/slew semantics. The declination correction model (magnetic
correction toggle + manual offset, cached against time-travel's fast clock) and the D65/D66
HUD state (RA/Dec, Alt/Az, FOV, correction row) live here too, with pure formatting helpers in
`HudFormats`. `sensors/SensorOrientationSource` prefers the rotation-vector sensor with v1's
accel+magnetometer fusion fallback and speed/damping settings; `SensorAccuracyMonitor` drives
calibration prompts.

`MapChrome` implements the three-zone chrome (D56/D57): layer rail (with `ifroom` overflow
semantics), action cluster, overflow sheet — all tagged with `ChromeTourTarget` so the warm
welcome can spotlight the *real* chrome with canned state.

### Supporting screens

Warm welcome (3-slide pager: live chrome tour, real Crab Nebula info card, sensor check),
object info cards (photo/description/data rows/see-also chips + tap-to-identify), gallery
(downsampled decode + LRU, no Coil), diagnostics, help (with the DSO symbol key rendered from
real map textures), what's new, settings (five sections over generic row kinds), EULA. Startup:
AndroidX splash held until the startup-state read, then `StartupRouter` gating, then a
once-per-cold-start version banner.

### Platform services

- **Analytics**: `AnalyticsEvents` keeps v1's event names verbatim; opt-in respected before
  the first event.
- **Experiments**: `ExperimentConfig` (`MOON_WIDGET`, `TONIGHT_WIDGET`, `NOTIFICATIONS`,
  `CAMERA_AR`, `SHARE_SKY` — `WARM_WELCOME` retired per D81, onboarding is unconditional now);
  gms backs it with Remote Config including a static-source fallback that works around the
  `setDefaultsAsync` cold-start race (a real D69 bug). All five remaining flags default to
  `false` as of the "disable unreleased features" pass — the features are complete and
  merged, just not yet announced (D80).
- **Location**: `LocationController` state machine over the flavor `LocationProvider`;
  Greenwich default; saved location seeds the sky at startup; Geoapify static map + geocoding
  in the location sheet.

### Widgets, notifications, camera-AR (the newest surfaces — all shipped, all gated off by default)

All five surfaces below are complete and merged but currently default-off via `ExperimentConfig`
(D80) pending a public announcement — see "Experiments" above.

- **Widgets** (D75/D76, PR #72–73): three Glance widgets — moon phase (custom-drawn disc),
  tonight's sky, shower countdown — fed by the pure `TonightSky` engine;
  `WidgetScheduler` keeps WorkManager refresh jobs alive only while a widget is placed, with
  an app-start catch-up sweep. Kill-switched via experiments (`WidgetGate`).
- **Notifications** (D77, PR #74): one slot per night — a planner worker arms a poster for
  sunset + 30 min; the poster recomputes at post time; two channels mapping 1:1 to settings
  rows, off by default (both the `NOTIFICATIONS` experiment and the per-channel settings rows).
- **Camera-AR** (D64/D67–D71, merged #67, gated per D80): CameraX `PreviewView` *under* a
  translucent GL surface; `CameraFov` computes the FILL_CENTER-cropped FOV to lock the map's
  camera to the lens; the GL `CameraScrimDrawer` dims the video plane; two exposure levers
  (scrim + AE compensation); `SkyShare` composites GL + camera stills via `PixelCopy` for the
  share sheet, gated independently via `SHARE_SKY` so camera-on/share-off keeps the AR controls
  panel and drops only the shutter. See [design/camera-ar-mode.md](design/camera-ar-mode.md).

---

## 7. Localization

Two-track: **catalog text** (object names, info cards) ships *in the generated DB* — ~30
locales already — while **UI strings** were English-only until PR #70 (merged), which adds 31
locale `values-*` directories (191 of 337 strings salvaged from v1 with exact-match-only
guards), splits the 11 KB `help_text` into 14 per-section keys, and adds `.tmconfig.toml` for
the translation pipeline. See `tools/salvage-translations/review-manifest.md` for the
remainder not yet salvaged, and D78 for the analysis.
