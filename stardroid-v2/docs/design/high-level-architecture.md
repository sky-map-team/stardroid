# High-Level Architecture

**Status: APPROVED** (D15, 2026-06-12).

## Goals (from the rewrite brief)

Faithful functional port of v1; entirely Kotlin; Compose UI; no monster activities; clean
swappable APIs for rendering and ephemeris; coroutines/Flow; KMP-friendly core (pure Kotlin,
no Android imports); design accommodates the post-rewrite feature list (Vulkan/GL3 renderer,
camera mode, downloads, notifications, many more layer kinds).

## Module structure

Gradle multi-module project. Modules marked **pure** contain no Android dependencies (and no
Koin/Hilt — constructor injection only) so a later KMP conversion is a build-file change, not
a refactor.

```
:app                  Android. Compose UI, ViewModels, Hilt, sensors, location,
                      navigation, settings. Wires everything together.
:render:api           pure. Renderer contract: scene primitives + camera, no GL types.
:render:gles1         Android. OpenGL ES 1.0 backend implementing :render:api
                      (port of the v1 renderer). Future :render:gles3 / :render:vulkan
                      slot in beside it.
:data                 Android (KMP-able via Room KMP). Room DB, prepackaged catalog,
                      repositories implementing :core:catalog interfaces.
:core:catalog         pure. Domain model of celestial objects, catalog/search
                      repository interfaces, layer content definitions.
:core:astronomy       pure. Ephemeris API + Keplerian implementation, coordinate
                      transforms, sky-model (orientation+location+time → view), clocks
                      including time travel.
:core:math            pure. Vectors, matrices, RaDec, sidereal time. Ported from v1
                      math/ (already Android-free); replace 2009-era custom code with
                      stdlib/kotlinx where better alternatives exist.
```

Dependency rule: arrows point inward only — `:app → {:render:*, :data, :core:*}`,
`:render:gles1 → :render:api → :core:math`, `:data → :core:*`. Core modules depend on nothing
above them.

DI per D2: Hilt lives only in `:app` (and other Android modules' entry points); pure modules
use constructors. Koin modules are introduced only when shared code actually needs a container
(i.e., when KMP work starts), and are trivial to add over constructor-injected classes.

## The rendering abstraction (`:render:api`)

> The code blocks in this section are the *approved high-level sketch*. The exact, authoritative
> types live in [render-api.md](render-api.md) (e.g. `setRenderState` not `setMode`; `Double`
> not `Float`; the `Viewport`/`SkyProjection` of D21). Where they differ, the detailed doc wins.

The contract speaks domain/mathematical language so a shader pipeline can replace fixed-function
GL later without touching producers:

```kotlin
interface SkyRenderer {
    fun submit(layerId: LayerId, scene: LayerScene)   // replaces that layer's content
    fun setCamera(camera: SkyCamera)                   // look direction, up, FOV
    fun setMode(mode: RenderMode)                      // day / night-red
}

class LayerScene(
    val depth: Int,
    val points: List<PointPrimitive>,      // unit geocentric pos, color, size, magnitude
    val lines: List<LinePrimitive>,        // vertex list, color, width
    val images: List<ImagePrimitive>,      // center, angular size, orientation, ImageRef
    val labels: List<LabelPrimitive>,      // pos, text, style, priority
)
```

- Coordinates are geocentric unit vectors (`:core:math` types); colors are abstract RGBA;
  images are resource-agnostic `ImageRef`s resolved by the backend. No GL enums, no buffers,
  no fixed-function concepts in the API.
- v1's extras (search arrow, crosshair, sky box / gradient) become ordinary primitives or
  small dedicated overlay scenes — not special cases inside the renderer.
- The GLES1 backend is a port of v1's object managers (points/lines/labels/images, texture
  atlas for labels), modified to the appearance rules below.

### Retained scenes + per-frame camera (replaces v1's update queue)

The key observation: almost all content is *static in celestial coordinates*. Stars,
constellations, deep-sky objects, the grid, and the ecliptic never move; only solar-system
bodies, comets, and the horizon change, and only on second-to-minute timescales. What changes
every frame is the **camera**. So instead of v1's imperative update queue
(`Reset`/`UpdatePositions`/`UpdateImages` mutations), producers publish a complete immutable
`LayerScene` whenever their content actually changes — rarely — and the backend:

1. On `submit`: converts the scene to GPU-resident form (VBOs/textures) once, off the hot path.
2. Per frame: applies the camera transform and draws. No per-frame work by producers at all.

This is simpler to reason about (no partial-update states, snapshots are trivially testable),
maps directly onto Flow (`scenes(...)` collected onto the GL thread), and suits future
shader backends (static vertex buffers + per-frame uniforms is exactly the GL3/Vulkan model).

**"Rarely" is a producer decision, not an API assumption.** In time-travel mode (including
the animated transition into it) the clock advances fast and solar-system bodies, comets, and
the horizon visibly move — those layers will re-submit scenes at up to per-frame rates. That
is fine because submission cost scales with scene size, and the dynamic layers are tiny
(~10 solar-system objects vs ~10⁵ stars). The contract is therefore:

- Producers subscribe to the clock and re-submit when *their* content has moved by enough to
  matter (per-body angular-velocity thresholds; the stars layer ignores the clock entirely).
- The backend must absorb high-frequency re-submission of **small** scenes without hiccups
  (small scenes use cheaply-rewritten buffers; large static buffers are never churned).

### Camera and reference frames

The renderer always receives a fully resolved `SkyCamera` (look direction, up, FOV) per frame
and knows nothing about *why* the camera points where it does. Camera resolution lives in the
sky-model (`:core:astronomy`), driven by a reference frame:

```kotlin
sealed interface ReferenceFrame {
    object DeviceOrientation : ReferenceFrame                  // sensor mode
    data class Manual(val pointing: Pointing) : ReferenceFrame // drag/fling mode
    data class FixedSkyPoint(val raDec: RaDec) : ReferenceFrame    // future: time travel
    data class Tracking(val body: SolarSystemBody) : ReferenceFrame // future: follow a planet
}
```

The planned time-travel feature — hold a fixed sky point, or follow a planet while time runs —
is then a sky-model concern (evaluate the tracked body's ephemeris each frame and aim the
camera at it) and requires **no change to the renderer API**. Sensor and manual modes are just
two more frames, which also tidies v1's controller-switching logic.

### Culling: the backend's job, not the layers'

Producers submit everything potentially visible (the whole sky); they never cull, which keeps
layers trivial and lets each backend optimize for its hardware:

- **Points and lines** stay resident on the GPU and are submitted every frame; the vertex
  stage discards offscreen geometry. CPU-culling tens of thousands of points per frame would
  cost more than just drawing them — 100k points is a trivial vertex load even for GLES1-era
  hardware. **This is the design's one unmeasured perf assumption, so it is gated:** the
  step-2 test scene drives ~100k points on a Pixel 3a emulator and must hold the D19 first-frame
  and 30 fps pan/zoom budgets before the architecture commits to "no point culling." If it
  fails, the fix is a backend-internal region index (a port of v1's `SkyRegionMap`) — invisible
  to producers and API-compatible. Progressive magnitude-tiered loading (D19) further bounds the
  initial point count to a few thousand regardless.
- **Labels** are the expensive primitive (text layout, fill rate, legibility) and are culled
  on the CPU each frame: frustum test via a spatial index built at scene-submission time, then
  priority-based screen-space decluttering (drop overlapping lower-priority labels). This
  formalizes what v1's `LabelObjectManager` half-did.
- **Images** are few; frustum-tested on the CPU, drawn as textured quads.

A magnitude-limit filter (future feature) fits both ends: coarse filtering at the catalog
query (fewer primitives submitted), fine/dynamic filtering as a per-frame renderer parameter.

### Appearance rules (D12)

Not everything in the sky is an astronomical object: constellation lines, the grid, the
horizon, and labels have no magnitude in the way Sirius does. So primitives carry one of two
appearance kinds, and the "backend owns the mapping" rule applies to the first:

```kotlin
sealed interface PointAppearance {
    data class Stellar(val magnitude: Float, val colorIndex: Float?) : PointAppearance
    data class Fixed(val color: Rgba, val sizeDp: Float) : PointAppearance
}
// Lines and labels are always producer-styled: color + widthDp / text style.
// All colors are abstract Rgba; the backend applies the night-mode (red) transform globally.
```

- **`Stellar`** — stars, planets-as-points: the backend maps magnitude/color index to
  appearance. Brightness is conveyed by color/intensity, *not* size: point size is essentially
  constant in screen space (density-scaled, with a small floor so faint stars stay clearly
  visible; at most a modest boost for the brightest few). Zooming must never turn stars into
  blobs — FOV does not scale point size.
- **`Fixed` / producer-styled** — sky furniture (grid, horizon, ecliptic, constellation lines,
  meteor-shower radiants): the producing layer chooses color and screen-space (dp) dimensions,
  which is still renderer-agnostic. **Line width is constant in screen pixels** at every zoom
  level; lines must never expand into rectangles.

**Why the backend owns the stellar mapping:** magnitude is the lossless form; pixels are a
lossy projection of it. (1) The best magnitude→appearance mapping is backend-capability-
dependent — GLES1 can only bake brightness into vertex color, while a future shader backend
can draw anti-aliased PSFs or bloom; pre-baked RGBA would cap the better backend at GLES1
quality. (2) The mapping depends on render-time state producers can't see: density, night
mode, and the planned light-pollution fade and automatic magnitude filtering, which need
magnitude at render time. (3) Point sources come from many producers (stars, ephemeris,
comets, downloads); one mapping keeps them on a single brightness scale and makes rules like
D12 enforceable in one place. (4) Star appearance is tuned by eye; one backend policy makes
iteration a one-line change with no data regeneration. The general rule: never degrade domain
data into pixels before crossing the API boundary — furniture is producer-styled only because
it has no domain quantity to defer on.

## The ephemeris abstraction (`:core:astronomy`)

```kotlin
interface Ephemeris {
    fun geocentricPosition(body: SolarSystemBody, time: Instant): RaDec
    fun phase(body: SolarSystemBody, time: Instant): Float   // moon phase, planet illumination
    fun magnitude(body: SolarSystemBody, time: Instant): Float
}
```

Initial implementation: `KeplerianEphemeris`, a port of v1's orbital-elements code
(`ephemeris/` + `space/` — v1's `Universe` was already a sketch of this facade). Golden tests
against v1 outputs (D6). A higher-precision VSOP87/ELP implementation can replace it later
behind the same interface; rise/set calculation (planned feature) belongs here too.

## Layers as data producers (no inheritance tree)

v1's `Layer` class hierarchy becomes a small interface + registry, designed for "many more
kinds of layers" (D4) including future downloaded ones (the `LayerContext` sketch below is
superseded by per-layer constructor injection — see [layers-and-app.md](layers-and-app.md)):

```kotlin
interface SkyLayer {
    val id: LayerId
    val depth: Int
    val labelRes: StringRef
    fun scenes(context: LayerContext): Flow<LayerScene>   // context: time, location,
}                                                          // model state, catalog repo
```

- Catalog-backed layers (stars, constellations, deep-sky) are one generic implementation
  parameterized by a catalog query — adding a catalog layer is data, not code.
- Computed layers (solar system, meteor showers, comets, grid, horizon, ecliptic, gradient)
  are small classes emitting on the clock/location ticks they care about.
- `LayerRegistry` holds the set; visibility toggles persist via preferences and simply
  subscribe/unsubscribe a layer's flow. Search federates over layers exactly as in v1.

## UI layer (`:app`)

- **Single-activity Compose app.** The GL surface sits in the composition via
  `AndroidView`/`AndroidEmbeddedExternalSurface`; all chrome (buttons, sliders, dialogs,
  settings, gallery, diagnostics, onboarding) is Compose. This kills the 1,456-line
  `DynamicStarMapActivity`: its responsibilities split into ViewModels (map state, search,
  time travel, location status) and plain composables. Navigation via Navigation-Compose.
- **ViewModels + use cases.** Business logic lives in core; ViewModels orchestrate flows
  (orientation, clock, location, layer scenes) and expose UI state. Nothing in a ViewModel
  touches GL or sensors directly.
- **Sensors:** an `OrientationSource` (Android) exposes `Flow<RotationMatrix>` from the
  rotation-vector sensor with the legacy accelerometer+magnetometer fusion fallback and v1's
  damping/speed options; it feeds the pure sky-model in `:core:astronomy`.
- **UX anchoring:** same mental model as v1 (full-screen map, layer toggle row, search with
  arrow guidance, time-travel controls, night mode), restyled with Material 3 within brand.
  Per D11, dated patterns may be replaced with modern equivalents — e.g. the three-dots
  overflow menu gives way to Material 3 idioms (bottom sheets / icon actions) — as long as
  the experience remains recognizably Sky Map.

## Cross-cutting technology choices

| Choice | Decision | Note |
|---|---|---|
| Storage | Room, prepackaged DB + downloads | See [data-layer.md](data-layer.md) |
| Preferences | Jetpack DataStore — **confirmed (D10)** | SharedPreferences migration not required (D1) |
| Time | `kotlinx-datetime` in core — **confirmed (D10)** | `java.time` only at Android edges |
| Serialization | `kotlinx-serialization` — **confirmed (D10)** | object-info content, download manifests |
| Images | Coil (Compose-native) | already used in v1 |
| Async | Coroutines + Flow throughout | per brief |
| Networking | None in initial port | ISS dropped; downloads come later (Ktor or OkHttp then) |
| Analytics | Same gms/fdroid split as v1 | Firebase behind an interface, no-op for fdroid |
| minSdk | 29 — **confirmed (D9)** | ~1.6% of installs below Android 10; they keep v1 |

## Porting order (sketch, for after detailed design)

1. `:core:math` + `:core:astronomy` with golden tests against v1 — the correctness anchor.
2. `:render:api` + `:render:gles1` port, driven by a hardcoded test scene.
3. `:data` with generated prepackaged catalog; catalog layers render.
4. Sky-model + sensors: the map points at the sky.
5. Computed layers, search, time travel, settings, remaining screens.

Each step yields something buildable and verifiable (brief, step 5).
