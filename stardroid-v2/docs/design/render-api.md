# Detailed Design: `:render:api` (and `:render:gles1` port notes)

**Status: IMPLEMENTED** (porting-order step 2, through Slice 3b-iii / D31).

- `:render:api` — contract, primitives, `Matrix4`, `SkyProjection` (Slice 3a, D28).
- `:render:gles1` — `GLSkyRenderer`, `StellarStyler`, `GreatCircleSubdivision`, `PointDrawer`,
  `LineDrawer` (Slice 3b-i, D29); `ImageDrawer`, `LabelDeclutterer`, `LabelDrawer` (Slice 3b-ii,
  D30); `SkyGradientDrawer` (Slice 8, D41).
- `:app` — `RenderConnector` (D23 RENDERMODE_WHEN_DIRTY bridge) + `RendererTestActivity` (dev
  test-scene harness, D19 perf-gate, screenshot comparison; Slice 3b-iii, D31 —
  debug-source-set only, audit-2026-08 H1).

The D19 30 fps gate is CI-verified as a smoke test only (≥ 5 frames on SwiftShader); real 30 fps
validation is a release-time check on Pixel 3a hardware (see decisions.md D31). Detailed design
increment 1 (with [core-math-astronomy.md](core-math-astronomy.md)).

`:render:api` is pure Kotlin (depends only on `:core:math`). It encodes the decisions in
[high-level-architecture.md](high-level-architecture.md): retained scenes + per-frame camera
(D13), backend-owned culling, the appearance rules (D12), a resolved camera (D14), the shared
CPU-built projection (D21), and the on-demand render loop (D23).

## The contract

```kotlin
interface SkyRenderer {
    /** Replaces the named layer's content. Thread-safe; cheap (publishes an immutable ref). */
    fun submit(layerId: LayerId, scene: LayerScene?)        // null removes the layer
    /** Per-frame state. Thread-safe; latest value wins. */
    fun setCamera(camera: SkyCamera)
    fun setRenderState(state: RenderState)
}

data class SkyCamera(
    val lineOfSight: Vector3,      // unit, celestial coords
    val up: Vector3,               // unit, perpendicular to lineOfSight
    val fovDeg: Double,
)

data class RenderState(
    val nightMode: Boolean = false,        // backend applies red transform to ALL colors
    val magnitudeLimit: Double? = null,    // dynamic fine filter (coarse filtering is the
                                           // catalog query's job)
    val labelScaleFactor: Double = 1.0,    // global accessibility/font-size multiplier,
                                           // applied by the backend — no resubmission needed
    val skyGradient: SkyGradient? = null,  // sun-position sky dome behind all layers; null
)                                          // draws none (D41; layers-and-app.md)

data class SkyGradient(
    val sunDirection: Vector3,             // unit, celestial coords — same frame as SkyCamera
)

class LayerScene(
    val depth: Int,                        // back-to-front ordering, v1 depth table carried over
    val points: List<PointPrimitive>,
    val lines: List<LinePrimitive>,
    val images: List<ImagePrimitive>,
    val labels: List<LabelPrimitive>,
    val glows: List<GlowPrimitive>,        // additive gradient meshes (horizon glow, D38)
)
```

**Within-scene ordering is part of the contract:** primitives of each type draw in list order
(painter's algorithm), and the types draw in the order glows → lines → images → points →
labels (glows first since D38, so the crisp horizon line draws over its glow's top edge).
This is what makes solar-system occlusion correct: the solar-system layer sorts its images by
descending Earth-distance at every submission (it already has the heliocentric coordinates,
and it re-submits as time advances), so Mercury renders behind the Sun at superior conjunction
and in front of it at inferior conjunction, and the Moon — nearest, last in the list —
correctly occludes the Sun during an eclipse. This fixes v1's static enum-declaration-order
drawing, whose own TODO admitted conjunctions rendered wrongly.

### Projection and screen model (D21)

`SkyCamera` does not fully determine where a point lands on screen — it lacks the screen. The
backend supplies the missing half from its `Surface`:

```kotlin
data class SkyCamera(
    val lineOfSight: Vector3,      // unit, celestial coords
    val up: Vector3,               // unit, perpendicular to lineOfSight
    val fovDeg: Double,            // FOV across the shorter viewport side
)

data class Viewport(val widthPx: Int, val heightPx: Int, val density: Float) {
    val aspect: Float get() = widthPx.toFloat() / heightPx
}
```

- `fovDeg` is the FOV across the **shorter** viewport side (the vertical FOV in landscape);
  the longer side's FOV derives from the aspect. Anchoring the FOV to the short side keeps the
  sky's apparent scale constant across portrait/landscape rotation. The backend
  obtains the `Viewport` from `onSurfaceChanged` (px) and the display metrics (density); the API
  has no `setViewport` call — the surface owns its own size.
- The projection is an ordinary perspective. Near/far merely bracket the unit sphere; depth
  ordering is the painter's algorithm (D18) with the depth buffer off, so near/far are not
  load-bearing.

The projection math is **pure and CPU-side**, in `:render:api`:

```kotlin
class SkyProjection(camera: SkyCamera, viewport: Viewport) {
    val viewProjection: Matrix4                       // perspective × look-at(origin, los, up)
    fun worldToScreen(p: Vector3): ScreenPoint?       // null if behind the viewer
}
data class ScreenPoint(val xPx: Float, val yPx: Float, val depth: Float)
```

Building this matrix is CPU work in *every* backend (even GLES1's `glFrustumf`/`glLoadMatrixf`
compute on the CPU and merely hand GL the result); only the per-vertex multiply for drawing
happens on the GPU. So `:render:api` owns the one implementation and:

- the **GLES1 backend** builds `SkyProjection.viewProjection` and loads it into GL — it does not
  keep a private projection;
- the **Compose search arrow** (screen-space UI, no GL) calls `worldToScreen` on the CPU for the
  single target point.

Both therefore use the byte-identical matrix, so the GL-drawn sky and the CPU-projected arrow
agree pixel-for-pixel, and projection is unit-testable with no GL context. `dp → px` is resolved
at each Android edge from `Viewport.density` (the backend for primitive sizes / label offsets,
Compose for the arrow); the pure projection works in angular/NDC space and never sees dp.

### Primitives

All positions are unit geocentric vectors (`:core:math.Vector3`); all sizes/widths are
screen-space dp; all colors are abstract `Rgba` (night-mode transform is the backend's).

```kotlin
data class PointPrimitive(val pos: Vector3, val appearance: PointAppearance)
sealed interface PointAppearance {
    data class Stellar(val magnitude: Double, val colorIndex: Double?) : PointAppearance
    data class Fixed(val color: Rgba, val sizeDp: Double) : PointAppearance
    // Screen-space textured marker (DSO type icons, radiants) — the catalog-and-schema.md
    // addendum, added in slice 4d. Does not scale with zoom, unlike ImagePrimitive.
    // The tint modulates the texture at draw time (D38): glyphs are authored white, so
    // marker color is layer policy like every other primitive's, not baked into assets.
    data class Icon(val image: ImageRef, val sizeDp: Double, val tint: Rgba) : PointAppearance
}

data class LinePrimitive(val vertices: List<Vector3>, val color: Rgba, val widthDp: Double)
// Polyline on the celestial sphere; backend subdivides long arcs into great-circle segments
// (v1 did this at line construction — it moves into the backend, where projection lives).

data class GlowPrimitive(val rings: List<GlowRing>)   // concentric loops, outermost first;
data class GlowRing(val vertices: List<Vector3>, val color: Rgba)   // all the same length
// Additively-blended gradient mesh (D38, ports v1's upstream HorizonGlowPrimitive): the
// backend fills the bands between consecutive rings, Gouraud-interpolating each ring's color
// (alpha included) across the band, and draws with GL_SRC_ALPHA/GL_ONE so the glow adds light
// to whatever is behind it. Ring vertices are used as given — no great-circle subdivision.

data class ImagePrimitive(
    val center: Vector3,
    val angularSizeDeg: Double,            // world-anchored, DOES scale with zoom (a nebula
    val rotationDeg: Double,               // photo covers sky); the up-orientation on the
    val image: ImageRef,                   // sphere, and an opaque image key resolved by the
)                                          // backend (resource, asset, or DB blob)

data class LabelPrimitive(
    val pos: Vector3,
    val text: String,                      // already localized by the producer (data layer)
    val style: LabelStyle,                 // size bucket (title/standard/minor), color, offsetDp
                                           //   (screen-space gap below the anchor, constant at
                                           //   every zoom — D74) and clearanceDeg (angular floor
                                           //   so labels clear angularly-sized images)
    val priority: Int,                     // decluttering rank (higher wins)
    val magnitudeForThresholding: Double? = null,
)                                          // enables FOV-dependent label depth (see decluttering
                                           //   below); producers may bias it (DSO bonus) or drop
                                           //   it (planets) — `priority` keeps true brightness
```

Design notes:

- **`ImageRef` is an opaque key** (`value class ImageRef(val key: String)`). Producers name
  images ("planet/jupiter", "dso/m31"); each backend owns resolution and texture lifecycle.
  Keeps drawables, assets, and future downloaded imagery out of the API. **Future imagery path
  (G7):** for the initial port the backend resolves keys to bundled drawables/assets. Downloaded
  imagery later flows the same way — a shared image store (the download/Coil disk cache, keyed by
  `ImageRef`) is read by the backend's texture loader; this is a backend responsibility behind
  the unchanged `ImageRef` contract, not an API change. The Coil path (gallery/info-card) and the
  GL texture path stay separate consumers of that one store.
- **Labels carry resolved strings.** Localization is the data layer's concern; the renderer
  draws text. Text rasterization (v1: Canvas → texture atlas) is a backend implementation
  detail — fine, since backends are Android modules; the *API* stays pure.
- **No partial updates.** A layer that changes resubmits its whole `LayerScene` (D13). Scenes
  are plain data: producers are unit-testable by asserting on scene contents, no GL anywhere.
- **Points stay `List<PointPrimitive>` for the port (D22).** At the bundled catalog's scale (a
  few thousand stars, submitted once) the boxed list is allocation-cheap. A columnar
  `StellarPointBatch(positions: FloatArray, magnitudes: FloatArray, colorIndices: FloatArray)`
  — carrying domain magnitude, so D12 is unaffected — is an *additive, non-breaking* future
  field on `LayerScene` for when the downloadable bulk catalog (tens of thousands) lands. Not
  built now; noted so the boxed list isn't mistaken for a permanent ceiling.

### Threading model

- `submit`/`setCamera`/`setRenderState` may be called from any thread; they publish immutable
  references (atomic swap, no locks held during draw).
- The backend draws on its own render thread (GLSurfaceView's GL thread for GLES1). On each
  frame it reads the latest published camera/state and scene set; on first sight of a new
  scene it uploads GPU resources (VBOs, textures). Large static layers therefore upload once;
  small dynamic layers (solar system during time travel) re-upload cheaply per submission.
- Producers connect via a small adapter in `:app` collecting each layer's
  `Flow<LayerScene>` into `submit()` — the renderer API itself stays Flow-free so backends
  and tests need no coroutine machinery.

### Render loop and power (D23)

The GLES1 backend runs `GLSurfaceView` in **`RENDERMODE_WHEN_DIRTY`**, not continuous. The
`:app` `RenderConnector` calls `requestRender()` whenever a camera, render-state, or scene
change is published; the backend coalesces these to the display refresh. Consequences:

- A still device in real-time mode draws **nothing** — no busy-loop, battery-friendly (the
  North Star's implicit "and battery-friendly"). This matches v1's on-demand rendering intent.
- In sensor mode, sensor samples drive `requestRender`; during time travel, clock ticks do.
- The render thread reads the latest published camera/state/scenes each time it wakes.

### GL resource lifecycle (G9)

The CPU-side `LayerScene`s are the retained source of truth (D13); GPU resources (VBOs,
textures, the label atlas) are a derived cache. On EGL context loss (backgrounding) and surface
re-creation the backend re-uploads from the retained scenes — no producer involvement, no
re-`submit`. This is an explicit backend responsibility.

## `:render:gles1` port notes

A port of v1's renderer behind the new API, not a rewrite — split further than the table below
implies; see D29 for the 3b-i (points/lines, **built**) vs. 3b-ii (images/labels, proposed) split:

| v1 | v2 |
|---|---|
| `RendererController` + update queue + `UpdateType.{Reset,UpdatePositions,UpdateImages}` | Gone — replaced by whole-scene swap on submit |
| `PointObjectManager` / `PolyLineObjectManager` / `ImageObjectManager` / `LabelObjectManager` | Become internal per-primitive drawers, fed from `LayerScene` instead of `Renderable` objects |
| `HorizonGlowObjectManager` (upstream #924) | `GlowDrawer` (D38): same band-quad indexing and additive blend, with day+night per-vertex color buffers baked at build time |
| `SkyRenderer` (GLSurfaceView.Renderer) | `GLSkyRenderer`, same role; consumes published camera instead of being poked |
| Label texture atlas (Canvas rasterization) | Kept as-is internally (3b-ii) |
| `SearchArrow`, `CrosshairOverlay`, `SkyBox` special cases | Deleted from the backend; produced as ordinary scenes by their features |
| Point size scaled with zoom; line width in world-ish units (world-space quads, `SkyRegionMap` CPU culling) | Replaced per D12/D29: `GL_POINTS`/`glPointSize` and `GL_LINE_STRIP`/`glLineWidth` — both natively pixel-space, so constant-dp sizing and zoom-invariance fall out of the GL call instead of geometry; no CPU culling (points/lines are GPU-resident and submitted whole) |

`StellarStyler` (backend-internal, one per backend; **built** in 3b-i) is where D12 lives:
magnitude → color/alpha on a density-scaled, near-constant point size; clamped boost for the
brightest few; optional `colorIndex` tint. It reads `RenderState.magnitudeLimit` for fine
filtering (fade-out near the limit rather than a hard pop, where the backend can afford it).
`GreatCircleSubdivision` (also 3b-i) replaces v1's construction-time chord subdivision, moved to
the backend since "is this chord visibly short of the sphere" is a projection question.

Label decluttering (backend-internal, **built in 3b-ii**): per frame, frustum-test label
positions (dot-product threshold from FOV × aspect), apply an **FOV-dependent magnitude threshold**
(`4.0 + (45° − fovDeg).coerceAtLeast(0) / 10`, zooming in deepens it), project survivors via
`SkyProjection.worldToScreen`, then greedy screen-space rejection by `priority`. Label sizes are
the style bucket (TITLE 15 sp / STANDARD 10 sp / MINOR 8 sp) × `RenderState.labelScaleFactor`,
so the accessibility/font-size preference rescales every label without any producer resubmitting —
but does require an atlas rebuild (infrequent). This replaces v1's `LabelObjectManager` ad-hoc
behavior with a defined rule. Label rotation (sky-up angle) is deferred to step 4 (sky model);
labels are currently always drawn upright. See decisions.md D30.

## What verifies it

- `DummyRenderer`-style fake in `:render:api` test fixtures: records submissions, lets layer
  and ViewModel tests assert "stars layer submitted N points with magnitude data" with no GL.
- **Built in 3b-i:** `StellarStyler`, `GreatCircleSubdivision`, `PointDrawer.build`, and
  `LineDrawer.build` are plain-JUnit tested with no GL context — they touch no `android.*`/GL
  types, only `GLSkyRenderer`/`*Drawer.draw` issue real GL calls. `SkyProjection` unit tests
  (pure, no GL): `worldToScreen` round-trips, off-screen/behind-viewer rejection, aspect/FOV
  correctness — the same projection the arrow relies on (D21).
- **Built in 3b-ii:** `ImageDrawer.quadCorners` (pure geometry — corner scale, frame rotation,
  pole singularity fallback) and `LabelDeclutterer` (frustum test, FOV magnitude threshold,
  greedy screen-space declutter) are plain-JUnit tested with no GL or Android context.
  `ImageDrawer.build/draw/release` and `LabelDrawer.build/draw/release` issue real GL/Android
  calls and have no automated unit coverage until the 3b-iii test scene.
- **Proposed, 3b-iii:** a standalone test activity (development-only) driving `:render:gles1`
  with a synthetic scene — the porting-order step 2 milestone ("hardcoded test scene") from the
  high-level doc; the on-device GL draw path (vertex/color arrays, `glPointSize`/`glLineWidth`
  state, the GL resource lifecycle on context loss) has **no automated coverage until then**.
- **Proposed, 3b-iii:** screenshot comparison of v1 vs v2 rendering the same sky for the visual
  port, acknowledging intentional D12 differences (star sizes, line widths).
- **Proposed, 3b-iii:** the test scene is also the **D19 perf gate**: it drives ~100k points on a
  Pixel 3a emulator to validate first-frame and the 30 fps pan/zoom floor *before* the
  no-point-culling stance (D13) is locked in; missing the gate triggers a backend region index
  (or, per D29, promoting 3b-i's client-side arrays to real VBOs first).
