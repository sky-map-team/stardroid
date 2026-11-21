# Sky Map v2 — Code Overview

**Status:** checked against master on 2026-11-15, after the iOS port. This document supplements the per-area design docs in
[design/](design/) with a single top-down tour of what actually exists, where it lives, and
which design docs are still the authoritative reference for each area. It repeats as little as
possible — where a design doc is current, this doc just points at it.

Related reading order for a newcomer: this doc → [design/high-level-architecture.md](design/high-level-architecture.md)
→ the per-area doc for whatever you're touching → decisions.md for the "why"
behind any surprising choice (code comments cite decision ids like `D33` throughout).

---

## 1. What this is

Sky Map v2 is a ground-up Kotlin rewrite of the v1 Java planetarium app, now for Android and
iOS from one Kotlin Multiplatform build: a full-screen sky map driven by device sensors, with
catalog-backed and computed layers (stars, DSOs, constellations, solar system, meteor showers,
two grids, ecliptic, horizon, and satellites behind a flag), search, time travel, object info
cards, a gallery, diagnostics, settings, onboarding and notifications. Android also has
home-screen widgets, and camera-AR behind a flag.

- **Android:** a single-activity Jetpack Compose app, minSdk 28 (Android 9 best-effort only,
  see D9), targetSdk 36, two flavors (`gms` with Firebase/Play Services, `fdroid` fully FOSS),
  drawn by GLES1 or GLES3.
- **iOS:** a Swift shell hosting the Kotlin `SkyMapKit` framework, with the same Compose
  Multiplatform screens and a Metal renderer. Deployment target iOS 15.

### Where to find what's current

This doc describes the code, not the release train. For what has shipped, what is in progress
and what is built but switched off:

- [README.md](README.md) — the index of every design doc (built, proposed or reference, with
  the decision that landed it), implementation status slice by slice, and the standing "Not
  built" list. Where a design doc's status matters, look there; this doc doesn't repeat it.
- [CHANGELOG.md](../../CHANGELOG.md) — what each release shipped.
- §6's Experiments list — the features that are built but default off.

---

## 2. Module graph and build structure

The module list (every Gradle module, the convention plugin it applies, what it depends on and
what it holds) and the convention plugins themselves are in
[design/build-and-tooling.md](design/build-and-tooling.md#module-graph). This doc doesn't
repeat them.

In outline: `:core:*` and `:render:api` are the pure core, Kotlin Multiplatform for JVM and iOS
with no Android SDK, so `import android.*` there is a compile error. `:data` and `:shared:*` are
the app's middle layer, moved out of `:app` during the iOS port and shared by both apps. Each
app is a platform shell over them. On Android that's `:app`, with the `:render:gles1` and
`:render:gles3` backends. On iOS it's `:app-ios` plus the Swift shell in `ios/SkyMap`, with
`:render:metal`. Pure modules use constructor injection only (no Hilt/Koin), so a KMP conversion
would be a build-file change. That was a stated design goal (high-level-architecture.md) and it
held: the conversion (iOS port phase 0) needed one `expect`/`actual`, for `NameNormalizer`'s use
of `java.text.Normalizer`.

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

- `./gradlew check` = unit tests (JUnit 5 + Truth; the pure modules' kotlin.test suites run on
  the JVM and, with Xcode installed, the iOS simulator), ktlint, and the Konsist gate. Must pass
  before any commit. A macOS CI job runs the pure modules' suites on the iOS simulator.
- **Konsist gate** (`konsist/src/test/kotlin/.../ArchitectureTest.kt`): non-vacuity guard, "no
  Android imports in pure modules", an inward-only-deps allow-list, and "no JDK imports in the
  shared modules' common code".
- **Generator reproducibility gate** (`data/generator/src/test/.../CatalogDbGeneratorTest.kt`):
  runs against the real schema + source-data; asserts byte-identical output across runs,
  identity-hash match, FTS diacritic folding, plus content spot checks.
- **Instrumented**: `:data`'s device tests against real SQLite (FTS4 + Room invalidation;
  `connectedAndroidDeviceTest`, and its `commonTest` suites also run on the iOS simulator);
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
interface (`geocentricPosition`, `topocentricPosition`, `phaseAngleDeg`, `illuminatedFraction`,
`angularDiameterDeg`, `magnitude`, `earthDistanceAu`, `maxAngularVelocityDegPerDay`).
`KeplerianEphemeris` is the v1-ported baseline. `MeeusEphemeris`, which both apps use, slots in
behind the same interface: it computes the Sun and Moon from full analytic series, since the
baseline placed some eclipse maxima ~30 minutes out, and delegates every other body to the
baseline ([design/ephemeris-accuracy.md](design/ephemeris-accuracy.md)). Time currency is
`kotlinx.datetime.Instant` throughout. Beyond the port: `RiseSet.kt` (Astronomical Almanac
hour-angle iteration, works for ephemeris bodies *and* fixed RA/Dec, D50/D51), `LunarPhase.kt`
(analytic next-phase-event, replacing v1's stepping loop), `LunarEclipse.kt` (D106),
`MoonWidgetModel.kt`, and the satellite maths: `Tle`, `Sgp4`, `SatelliteEphemeris`,
`SatellitePass`, `SatelliteMagnitude` ([design/satellite-tracking.md](design/satellite-tracking.md)).

`SkyModel.kt` is the heart of pointing: `LocalFrame` (observer north/east/up in equatorial
coordinates, with a magnetic pair pre-rotated by declination — v1's trick of rotating the
*celestial axes* rather than correcting each sensor reading), and
`pointing(localFrame, orientationMatrix, viewDirection)` mapping the phone frame onto the sky.
It is entirely pure. Reading the sensors is each platform's job (§6); the smoothing they share
is in `:shared:model`.

### `:core:catalog`, `:core:events` and `:core:text`

`CatalogRepository` (Flow-returning interfaces), the object/type domain model, `LocaleSpec`
with its fallback chain (`pt-BR → pt → en → ""`), `NameNormalizer` (the `expect`/`actual` the
KMP move needed). `:core:events` holds `TonightSky` — twilight window, well-placed planets,
shower peaks vs moonlight, moon phases, lunar eclipses — which the widgets and the notification
planner read (see [design/widgets-and-notifications.md](design/widgets-and-notifications.md)).
`:core:text` is how shared logic asks for translated text: it names each string by `TextId`, and
each platform answers from its own resources (`AndroidTextSource`, `IosTextSource`). The
notification planner is its first user.

---

## 4. The renderer

Authoritative designs: [design/render-api.md](design/render-api.md) for the contract, and
[design/render-gles3.md](design/render-gles3.md) and [design/render-metal.md](design/render-metal.md)
for the newer backends. Short version:

### `:render:api` — the contract (pure)

`SkyRenderer` is three thread-safe methods: `submit(layerId, scene?)` (replace a whole layer's
immutable `LayerScene`; null removes), `setCamera(camera)`, `setRenderState(state)`. This is
the D13 "retained scenes + per-frame camera" model: producers publish complete scenes only when
*their* content changes; the camera changes every frame. The primitives are points (with a
`Stellar(magnitude, colorIndex)`, `Fixed` or `Icon` appearance, D12), lines, world-anchored
images (which can carry a phase `Terminator` or an `EclipseShadow`), and screen-space labels
(with declutter priority). Positions are unit geocentric vectors, sizes are dp, colors abstract
`Rgba`; night mode is the backend's job.

The single most port-relevant decision (D21): **the view-projection pipeline lives in the pure
module.** `SkyProjection`/`Matrix4` build the matrix every backend draws with and every Compose
overlay that needs sky↔screen math inverts: the search arrow (`SearchGeometry.kt`) and
tap-to-identify (`IdentifyGeometry.kt`), both in `:shared:viewmodels`. FOV spans the *short*
viewport side (portrait/landscape parity).

Rendering policy that isn't graphics-API code lives here too, so all three backends share it:
`StellarRamps` (magnitude and colour index → size, brightness and tint), `GreatCircleSubdivision`,
the label pipeline (`LabelAtlas`, `LabelAtlasPacker`, `LabelDeclutterer`, `LabelFader`),
`PhaseGeometry` and `MoonShading`, `EclipseGeometry`, `GroundRamp`. All of it is tested in
`commonTest` on the JVM and the iOS simulator.

### The backends

- **`:render:gles1`** — `GLSkyRenderer` implements `SkyRenderer` + `GLSurfaceView.Renderer`;
  cross-thread state is immutable snapshots swapped atomically, with per-primitive-family GPU
  caches keyed by scene identity so unrelated state changes don't force rebuilds. Fixed-function
  drawers: `PointDrawer` (native `glPointSize` — zoom-invariant for free), `LineDrawer`,
  `ImageDrawer` (with `PhaseCompositor` for phases), `IconDrawer`, `GroundDrawer`,
  `SkyGradientDrawer` (v1's SkyBox dome), `CameraScrimDrawer` (AR video dimmer), `LabelDrawer`.
  Labels are text-to-texture: `android.graphics.Canvas` rasterizes white-on-transparent into
  atlas pages, and tinting happens at draw time via `glColor4f`.
- **`:render:gles3`** — `GLES3SkyRenderer`: the same drawers on GLSL shaders
  (`src/main/assets/shaders/`), adding the analytic sky, a shaded ground, an eclipse shader and
  label halos. It is Android's default while the `GLES3_RENDERER` flag is on and the device
  supports GLES 3. `createRendererBackend` (`app/.../render/RendererBackends.kt`) makes the
  choice and falls back to GLES1; Settings → Advanced → Graphics engine overrides it. The two GL
  backends are siblings, and Konsist stops either importing the other.
- **`:render:metal`** — `MetalSkyRenderer`, Kotlin/Native calling Metal, with labels rasterized
  by `UIKitGlyphRasterizer`. It draws everything `:render:gles3` does. Its shaders in
  `render/metal/shaders/*.metal` are hand-transcribed from `:render:gles3`'s GLSL, so a change
  to either must be mirrored in the other (`MetalShaderConstantParityTest` catches only some
  drift). The on-device perf gate is still to come.

Render loop: on Android, `RENDERMODE_WHEN_DIRTY` (D23), with `RenderConnector` bridging the
backend to the `GLSurfaceView` and requesting a frame after each change. On iOS, a display link
that starts paused (`DisplayLinkFrameTicker`, `PausedDisplayLink`). Harnesses: Android's
`testscene/RendererTestActivity` (`app/src/debug/`, never in a release build) +
`RendererPerfTest`, and iOS's `ios/RendererHarness` over `:render:metal-harness`. Both draw
`:render:testscene`'s seeded scene.

---

## 5. Data storage

Authoritative design: [design/catalog-and-schema.md](design/catalog-and-schema.md) and
[design/data-layer.md](design/data-layer.md). Key facts:

- **Room catalog** (`data/src/commonMain/kotlin/.../data/`, shared with iOS): pack-based
  provenance (bundled pack `"core"`; `PackDao.applyPack/removePack` is the future download
  path), hierarchical object types as *data* (`galaxy.spiral` inherits rendering from
  `galaxy`), deliberately no foreign keys (cross-pack references must dangle gracefully), FTS4
  external-content name search with diacritic folding, year-agnostic meteor-shower activity
  windows, constellation figures as vertex rows. Locale fallback is whole-object via
  `LocaleSpec.fallbackChain`, resolved Kotlin-side in `RoomCatalogRepository`.
- **Bundled DB**: the factory re-copies `skymap.db` when its schema or content version
  (stamped into the SQLite header at build time) differs from the on-device copy's, with D24
  recovery (probe; on failure delete and re-copy once). iOS opens a copy with Room's
  bundled-SQLite driver; Android
  sets no driver and keeps the platform SQLite (D127). The DB holds **no user state** — that
  invariant is what makes wholesale replacement and recovery safe.
- **Search**: word-prefix FTS with operator-injection-proof tokenization; ranked by
  whole-name-prefix, primary-ness, brightness; moons resolve to their parent's position;
  `planet/*` ids bridge to live ephemeris positions (`SolarSystemIds.kt`).
- **Satellite elements** (`data/.../satellites/`): `CelesTrakClient` (`HttpURLConnection` on
  Android, `URLSession` on iOS), `SatelliteElementsRepository` caching what was fetched, and
  `SatelliteFetchPolicy` deciding when to fetch. Off by default since #1269 (D147/D148); see
  [design/background-network-audit.md](design/background-network-audit.md).
- **Settings** (`:shared:model`'s `settings/Settings.kt`, `DataStoreSettings.kt`): one
  Preferences DataStore file, shared with startup state. Android opens it as
  `preferencesDataStore(name = "settings")`; iOS opens the same format by path under Application
  Support (`SettingsDataStore.kt`). Everything is `Flow<T>` + suspend setters; enums stored by
  name with unknown-value fallback; `IOException` → empty preferences. v1 SharedPreferences
  deliberately not migrated (D1).
- **Startup state** (`startup/DataStoreStartupState.kt`, also `:shared:model`): EULA version
  int, warm-welcome/what's-new seen versions, sensor-warning suppression — consumed by
  `StartupRouter` (v1's gating ported verbatim, D48).

---

## 6. The apps

Authoritative designs: [design/layers-and-app.md](design/layers-and-app.md),
[design/screens-and-startup.md](design/screens-and-startup.md),
[design/map-hud.md](design/map-hud.md), [design/ux-polish.md](design/ux-polish.md).

Most of the app is shared: the layers, the ViewModels and the screens are in `:shared:*`, so both
apps run the same code. Each platform adds its wiring and the edges only it can provide: `:app`
on Android, and `:app-ios` + `ios/SkyMap` on iOS.

### Layers (`:shared:layers`)

`SkyLayer` is `id` + `depth` + `scenes(): Flow<LayerScene>`. `LayerRegistry` builds three
`CatalogLayer` instances differing only in `PrimitiveMapping` policy (stars → stellar points,
DSOs → tinted type icons resolved by walking up the type hierarchy, constellations → figure
lines), plus computed Grid, Alt/Az grid, Ecliptic, Horizon, SolarSystem (Earth-distance-sorted
for eclipse occlusion, D18/D54 topocentric Moon), MeteorShowers, and Satellites, which is
registered only when the `SATELLITES` flag is on. Styling is layer policy from `SkyColors`
(D40), never DB data. Each layer re-emits only on the inputs it cares about — the stars layer
ignores the clock entirely; the solar-system layer re-submits only when a body could have moved
past a visible threshold.

`RenderBinder` bridges Flows → renderer: camera and render state from `MapViewModel`, and per
layer `enabled(id).flatMapLatest { on -> if (on) layer.scenes() else flowOf(null) }`. Each
platform runs it while the map is visible; scene data never flows through Compose.

### Screens and ViewModels (`:shared:viewmodels`, `:shared:ui`)

The ViewModels (map, search, time travel, layers, location, object info, gallery, settings,
startup, compass calibration, diagnostics) are on androidx.lifecycle's multiplatform
`ViewModel`, hold no GL or platform types, and are tested in `commonTest`. The screens are
Compose Multiplatform in `:shared:ui` (D134), with their strings as Android-format resources
(§7). The map screen takes the sky as a slot that each platform fills with its renderer's view
(D137).

`MapViewModel` is the junction: SENSOR mode maps the platform's `OrientationSource` through
`SkyModel.pointing` into a `SkyCamera`; MANUAL mode ports v1's drag/stretch/rotate/fling/slew
semantics. The declination correction model (magnetic correction toggle + manual offset, cached
against time-travel's fast clock) and the D65/D66 HUD state (RA/Dec, Alt/Az, FOV, correction
row) live here too, with pure formatting helpers in `HudFormats`. Both platforms' sensor sources
share `:shared:model`'s `OneEuroQuaternionSmoother`, and `SensorAccuracyMonitor` drives
calibration prompts.

`MapChrome` implements the three-zone chrome (D56/D57): layer rail (with `ifroom` overflow
semantics), action cluster, overflow sheet — all tagged as `ChromeTourTarget`s so the warm
welcome can spotlight the *real* chrome with canned state.

The other screens: warm welcome (3-slide pager: live chrome tour, real Crab Nebula info card,
sensor check), object info cards (photo, description, data rows, rise/set, "Now" facts and
"Upcoming" events per D141, see-also chips) + tap-to-identify, gallery (downsampled decode +
LRU cache), time travel, diagnostics, help (with the DSO symbol key rendered from real map
textures), what's new, settings (Controls, Appearance, Sensors, Notifications and Other, over
generic row kinds), support, EULA. Startup runs `StartupRouter`'s gating, then a
once-per-cold-start version banner.

### Android (`:app`)

`di/AppModule.kt` holds the whole singleton graph (Hilt since D59): settings, startup state,
the shared **clock bus** (`TimeController`, D42) and **location bus** (`LocationController`,
D44), sensors, and the `FlavorEdges`-supplied platform services. Suspend-initialized state
(catalog open, layer registry) lives in `CatalogAccess` — a mutex-guarded first-use open with
recovery.

The app's locale is deliberately *not* one of the captured singletons: `locale/LocaleSource`
re-reads the configuration per request (`current`) and re-emits it on a configuration change
(`specs`). A language switch recreates the activities but not the process, so a captured
`LocaleSpec` would leave every catalog-sourced string — names, map labels, info-card prose —
in the old language until the process happened to die.

`ui/MainActivity.kt` is the single activity: it owns the `GLSurfaceView` and the backend
`createRendererBackend` picks, and runs `RenderBinder` inside `repeatOnLifecycle(STARTED)`. The
GL surface goes into the map screen's sky slot as an `AndroidView` (`ui/map/MapRoute.kt`).
`ui/SkyMapNavHost.kt` has 9 destinations (map, welcome, settings, gallery, diagnostics, help,
what's new, support, calibration); search/time-travel/object-info/location/layers stay
map-owned dialogs and sheets. The AndroidX splash is held until the startup-state read.
`sensors/SensorOrientationSource` prefers the rotation-vector sensor, with v1's
accel+magnetometer fusion as the fallback.

Platform services:

- **Analytics**: `AnalyticsEvents` (in `:shared:model`) keeps v1's event names verbatim, so the
  GA4 stream doesn't fork. gms sends them to Firebase; fdroid's `Analytics` is a no-op. What is
  collected, and the opt-out, are in [PRIVACY_POLICY.md](../../PRIVACY_POLICY.md).
- **Experiments**: `ExperimentConfig` (in `:shared:model`) over the `Experiment` flags.
  `NOTIFICATIONS` and `GLES3_RENDERER` default on; `CAMERA_AR`, `SHARE_SKY`, `SATELLITES` and
  `ANNOUNCEMENTS` default off. `SATELLITES` reads the key `satellites_v2_enabled`, because
  2.3.0–2.3.2 read the old key with on-by-default behaviour (#1269). Retired: `WARM_WELCOME`
  (D81) and `MOON_WIDGET`/`TONIGHT_WIDGET`, once the widgets launched. gms backs the flags with
  Remote Config, including a static-source fallback that works around the `setDefaultsAsync`
  cold-start race (a real D69 bug); fdroid uses the static defaults.
- **Location**: `LocationController` (in `:shared:model`) is a state machine over the flavor's
  `LocationProvider`; Greenwich default; the saved location seeds the sky at startup. Place
  names come from the platform `Geocoder`, and the location sheet's map is a Geoapify static
  map. That map is the only thing Coil loads (`RemoteImage` on Android, through
  `SkyMapApplication`'s `ImageLoader`); the gallery and info cards decode their own images.

Surfaces beyond the map:

- **Widgets** (D75/D76): three Glance widgets — moon phase (custom-drawn disc), tonight's sky,
  shower countdown — fed by the pure `TonightSky` engine and launched. `WidgetScheduler` keeps
  WorkManager refresh jobs alive only while a widget is placed, with an app-start catch-up
  sweep.
- **Notifications** (D77, D106): `:shared:surfaces`' `NotificationPlanner` decides which alert
  is due tonight and when it fires; `notifications/` schedules and posts it through WorkManager.
  Channels: meteor showers, the tonight digest, lunar eclipses, satellite passes, and
  announcements.
- **Satellites** (D92–D97, D147/D148): the layer, pass prediction, pass alerts and the CelesTrak
  fetch, all behind `SATELLITES`, which has been off since #1269. The app side is
  `satellites/` (`SatelliteScheduler`, `SatelliteRefreshWorker`, `PassAlerts`).
- **Announcements** ([design/remote-announcements.md](design/remote-announcements.md)):
  remotely authored messages on the notification, widget and interstitial surfaces, in
  `announcements/`, behind `ANNOUNCEMENTS`.
- **Camera-AR** (D64/D67–D71, still off behind `CAMERA_AR` and `SHARE_SKY`): CameraX
  `PreviewView` *under* a translucent GL surface; `CameraFov` computes the FILL_CENTER-cropped
  FOV to lock the map's camera to the lens; `CameraScrimDrawer` dims the video plane; two
  exposure levers (scrim + AE compensation); `SkyShare` composites GL + camera stills via
  `PixelCopy` for the share sheet, gated independently via `SHARE_SKY` so camera-on/share-off
  keeps the AR controls panel and drops only the shutter. See
  [design/camera-ar-mode.md](design/camera-ar-mode.md); the launch plan is
  [design/camera-ar-launch.md](design/camera-ar-launch.md) (proposed).

### iOS (`:app-ios` + `ios/SkyMap`)

`IosAppGraph` builds by hand what Android's Hilt modules provide. `SkyMapScreen` is the root the
Swift shell hosts: the shared map screen over the Metal sky (`MapViewController`, whose UIKit
recognizers take every gesture), with full-screen pages standing in for Android's navigation
routes. The iOS edges are Kotlin too: Core Motion orientation and sensor status, Core Location,
the display link, geocoding, local notifications, the satellite background refresh, and the tip
jar. The Swift in `ios/SkyMap/Sources` is the entry point plus adapters for SDKs used from
Swift — Firebase Analytics and Remote Config, StoreKit, `UNUserNotificationCenter`, and the
background task — passed into Kotlin through the shared interfaces. Not on iOS so far: widgets
and camera-AR. [design/ios-platform-surfaces.md](design/ios-platform-surfaces.md) plans the
widget and notification work, and [`ios/README.md`](../ios/README.md) covers building.

---

## 7. Localization

Two tracks, each translated by the `tm` pipeline (`.tmconfig.toml`; see `AGENTS.md`'s
Translations section for when to run it):

- **Catalog text** (object names, info cards) ships *in the generated DB*, from per-locale
  files under `source-data/names/` and `source-data/info_cards/`, so it is translated on both
  platforms.
- **UI strings** are Android-format `values-*` resources in two places:
  `app/src/main/res/` for the Android-only surfaces, and `shared/ui/src/commonMain/res/` for
  the shared screens, which Compose Multiplatform also bundles into the iOS app. Shared logic
  that needs text outside a screen (the notification planner) goes through `:core:text`.

`.tmconfig.toml`'s `primary_languages` lists the 28 core languages. `ca`, `hu` and `ru` exist as
partial locale directories and are intentionally excluded. The first locales were salvaged from
v1 (PR #70, D78; `tools/salvage-translations/review-manifest.md`). iOS's layer labels are still
English (`EnglishLayerStrings`) until iOS reads localized layer strings; catalog names are
unaffected.
