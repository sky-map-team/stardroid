# Detailed Design: `:render:gles3` — the GL ES 3.0 backend

**Status: PROPOSED** — design only, no code. Supersedes nothing yet; `:render:gles1`
([render-api.md](render-api.md)) remains the shipping backend until phase 1 lands and is
verified.

**Revised 2026-08-31.** Four things that were open are now decided, and the sections below are
written as decisions rather than options: §0 states the product stance that governs every Part B
default; §6 commits to retiring `:render:gles1`; §6.5 (new) covers the eventual iOS port, which
the first draft did not mention at all; and §7.1–§7.2 are rewritten around a sky model that
actually knows where the horizon is, which is what makes twilight and a sun-dependent afterglow
possible.

This document plans the port of the renderer from the GL ES 1.x fixed-function pipeline to a
programmable GL ES 3.0 pipeline. It is in two halves, deliberately, with a stance and a
portability section bracketing them:

- **§0 — the stance.** Sky Map is a map. The rule that decides every Part B default when realism
  and legibility disagree.
- **Part A (§1–§6.5) — parity.** A new backend that draws exactly what ships today, behind the
  unchanged `SkyRenderer` contract. No new pixels, no new features, no API change. This is the
  part that must be boring. §6.5 covers what an eventual iOS port asks of the design — which is
  less than you would expect, and none of it at Android's expense.
- **Part B (§7–§9) — the unlocks.** What the programmable pipeline then makes possible: a real
  sky and twilight, a sun-dependent afterglow and a ground, Greek-myth constellation art,
  continuous eclipses, and a wider slate of features that GLES1 structurally cannot render.

Keeping these apart matters. Every GLES-3 rewrite that has ever gone wrong went wrong by doing
both at once and losing the ability to say whether a visual difference was a bug or a feature.

---

## 0. The stance — a map that knows what the sky looks like

Sky Map is a map. Everything in Part B makes the sky more realistic, and realism and legibility
pull in opposite directions, so the tie-breaker has to be written down before any of it is built:
**legibility first; realism opt-in and attenuated.** Four rules follow, and the later sections are
bound by them.

- **Star size stays near-constant in screen space.** D12 survives verbatim. A per-pixel PSF
  (§8.3) makes a star *better* — rounder, smoother, honestly sub-pixel — never bigger. Zooming in
  must never turn stars into blobs, which is the failure mode v1's world-space billboards had.
- **Anything that adds light to the frame ships with an opacity control and a modest default.**
  The Milky Way, bloom, zodiacal light, airglow, constellation art: each is capable of burying
  the labels, lines and grid that are the actual map. The default must leave those the most
  legible things on screen; a user who wants a photograph can turn it up.
- **Anything animated is off by default.** Twinkle, meteor particles, a live corona. This is
  §5's power rule restated as a product rule, because it is both: switching one on is what
  switches `RENDERMODE_WHEN_DIRTY` to continuous rendering, and that is a battery decision the
  user should be making deliberately.
- **Physical accuracy is welcome where it teaches.** Atmospheric extinction reddening and dimming
  objects near the horizon explains why nobody observes there. The twilight sequence has names
  — civil, nautical, astronomical — and the Belt of Venus is a thing you can go outside and
  look at. Accuracy that merely darkens the map, with nothing to learn from it, is not worth the
  fill rate.

---

## 1. Why — the debts GLES1 is charging us

The current backend is a clean, well-tested port, and most of it is fine. But a specific list of
compromises in the shipping code trace directly to the fixed-function pipeline, and each one is
already written down as a TODO, a caveat, or a decision we had to soften:

| Where | The compromise | Root cause |
|---|---|---|
| `StellarStyler` KDoc (D12) | "GLES1 can only bake brightness into vertex color and a couple of discrete sizes, not anti-aliased PSFs" | no fragment shader |
| `PointDrawer` | Points sorted into `SizeRun`s, **one draw call per distinct size** | `glPointSize` is per-draw-call state, not a vertex attribute |
| `PointDrawer` TODO (D31) | `GL_SMOOTH_POINT_SIZE_RANGE` may be `[1,1]`, collapsing every smoothed star to 1 px on such hardware | fixed-function point smoothing is optional |
| `LineDrawer` | Width via `glLineWidth` | GL ES only guarantees a maximum line width of 1.0, and out-of-range values are **silently clamped** (no `GL_INVALID_VALUE`, no log). We *do* now query the range — `RendererInfo.lineWidthRange`, shown on the diagnostics screen — but nobody has read it off real hardware yet, so whether it bites is still unmeasured. See §3.1 |
| `LabelDrawer` TODO | "one matrix push + draw call per label … once catalog layers submit hundreds, batch" | no instancing |
| `IconDrawer` | Same: a `glPushMatrix`/`glScalef`/`glDrawArrays` per sprite | no instancing |
| `PhaseCompositor` KDoc (D88) | The lunar terminator is composited **per pixel on the CPU** into a bitmap, quantised into phase buckets, and re-uploaded | no fragment shader |
| `TextureCache` | Every `ImageRef` is held **twice** — normal and a red-shifted night-mode variant, and the cache key has since grown two more dimensions (`TextureKey(ref, PhaseKey(illumination, limb angle, `EclipseKey`))`) | night mode is a per-primitive colour bake, not a post-process |
| `SkyGradientDrawer` | An 8-band × 10-step Gouraud dome with hardcoded blue/grey ramps | per-vertex colour is the only colour source |
| `HorizonLayer.glowMesh` KDoc | 8 rings exist **only** to piecewise-approximate an exponential falloff, because "Gouraud shading interpolates alpha linearly" | ditto |
| Everywhere | Colour is blended in sRGB space; gradients band | no sRGB framebuffer, no dithering |

Two of those (`glLineWidth`, `GL_SMOOTH_POINT_SIZE_RANGE`) are silent-clamping hazards we have
been able to note but not defend against — though the line one is **not** a reason to change the
line technique; see §3.1. Several more are the reason a feature was deferred rather
than built: D89 (Saturn's rings, Jupiter's moons) and the whole imagery-quality thread in
[solar-system-imagery.md](solar-system-imagery.md) keep running into the same wall.

### What GLES 3.0 specifically gives us

Everything below is core GL ES 3.0 — no extensions, no 3.1/3.2:

- Programmable vertex/fragment stages (`#version 300 es`), so appearance is computed per pixel.
- `gl_PointSize` as a **per-vertex** output → the entire star field is one draw call, and size
  becomes a continuous function of magnitude.
- Instanced drawing (`glDrawArraysInstanced`, `gl_InstanceID`) → labels, icons and images each
  become one draw call.
- VAOs, real VBOs, `glMapBufferRange` → no more client-side arrays re-walked every frame.
- Uniform buffer objects → camera/state uniforms bound once per frame, not per draw.
- sRGB textures and framebuffers → correct gamma; blending in linear space.
- Float and half-float textures, multiple render targets, complete FBO support → off-screen
  passes (bloom, tone mapping, accumulation).
- ETC2/EAC compressed textures **guaranteed** (unlike GLES2) → constellation art and DSO
  photography at a fraction of the memory.
- `R8` single-channel textures → the label atlas at ¼ its current footprint.
- Non-power-of-two textures with full wrap and mipmap support.
- `gl_VertexID` → quad expansion with no index buffer and no per-quad vertex duplication.
- Transform feedback → GPU-resident particle systems (meteors, comet tails).

### Availability

GL ES 3.0 shipped in Android 4.3 (API 18); v2's floor is already minSdk 29 (D9). Coverage is
effectively universal but is **not** implied by the API level — a device advertises its
capability separately. Before committing, read the actual number off the Play Console
distribution dashboard for our own installed base, exactly as D9 was decided. The mechanism:

```xml
<uses-feature android:glEsVersion="0x00030000" android:required="true" />
```

which makes Play filter the (vanishingly few) incapable devices rather than shipping them a
black screen. At runtime, `ActivityManager.deviceConfigurationInfo.reqGlEsVersion` is the check
if we choose to keep a fallback at all — see §6 for the recommendation not to.

---

## 2. The seam — what changes outside the new module

Very little, and that is the whole point of D21/D28. `:render:api` is pure Kotlin, backend
-agnostic, and **needs no change at all for parity**. The new backend is a sibling module:

```
:render:api      (pure Kotlin — unchanged)
:render:gles1    (existing, to be retired — see §6)
:render:gles3    (new; implements the same SkyRenderer)
```

Three call sites touch a concrete backend today:

1. `MainActivity.kt:405` — `setEGLContextClientVersion(1)`.
2. `RendererTestActivity.kt:187` — same.
3. `RenderConnector` — typed to the concrete `GLSkyRenderer`, not to `SkyRenderer`.

Item 3 is a one-line generalisation that should land *first*, as its own tidy-up: `RenderConnector`
should hold a `SkyRenderer` plus a `GLSurfaceView.Renderer` (the two roles the backend plays),
so the choice of backend becomes a single construction-site decision. That is the entire
"pluggability" work. It is small because the architecture already paid for it.

The Konsist gate (D20) needs one new rule: `:render:gles3` may depend on `:render:api` and the
Android SDK, and nothing may depend on `:render:gles3` except `:app`. §6.5 adds a second: a
`java.nio` denylist on `:render:api`, so the portable half stays portable.

### What must be preserved exactly

These are contract, not implementation, and the new backend inherits them unchanged:

- **Painter's algorithm, depth test off** (D18). Layer order by `LayerScene.depth`; within a
  layer, glows → lines → images → points → labels; within a type, list order. This is what makes
  solar-system occlusion correct (Moon over Sun at an eclipse). GLES3 makes a depth buffer
  cheap and *we still do not want one* — the sky is a sphere at unit distance and the ordering
  is semantic, not geometric.
- **`RENDERMODE_WHEN_DIRTY`** (D23). A still device draws nothing. Off-screen post-processing
  passes (§7) are a standing temptation to go continuous; they must not.
- **G9 resource lifecycle.** CPU-retained `LayerScene`s are the source of truth; GPU resources
  are a derived cache rebuilt after EGL context loss with no producer involvement.
- **The shared `SkyProjection`** (D21). The GL backend loads the same CPU-built matrix the
  Compose search arrow uses. Under GLES3 it goes into a UBO instead of `glLoadMatrixf` —
  same matrix, same bytes.
- **Thread model.** `submit`/`setCamera`/`setRenderState` from any thread, publishing immutable
  references; all GL on the GL thread.

---

## 3. Phase 1 — parity, drawer by drawer

The `build` (pure CPU, unit-tested) / `draw` (GL, untested) split in the current backend is its
best feature and must survive. Under GLES3 the *balance* shifts — much of what `build` does today
is exactly what a shader should do instead — so the rule becomes: **`build` computes what depends
only on the scene; the shader computes what depends on the camera or the frame.**

| Drawer | GLES1 today | GLES3 |
|---|---|---|
| `PointDrawer` | Per-vertex colour; size runs; one draw call per size; `GL_POINT_SMOOTH` | One VBO of `(pos, magnitude, colorIndex)`, **one draw call**. Vertex shader computes `gl_PointSize` continuously from magnitude and density; fragment shader draws an analytic anti-aliased PSF from `gl_PointCoord`. Magnitude→colour moves from `StellarStyler`'s CPU bake into the shader, so `magnitudeLimit` and night mode stop invalidating the buffer entirely |
| `LineDrawer` | `GL_LINE_STRIP` + `glLineWidth` | **Unchanged** — `glLineWidth` is core in GL ES 3.0 and the current behaviour is what we want. See §3.1 |
| `GreatCircleSubdivision` | CPU, at build time | **Unchanged** — stays CPU-side and unit-tested. (A tessellation shader would need GLES 3.2; not worth the floor) |
| `ImageDrawer` | Per-frame CPU quad-corner rebuild, one `glDrawArrays` per image | Corners move into the vertex shader (`SizeFloor` becomes shader arithmetic on a `fovDeg` uniform); one instanced draw for all images sharing a texture. Note: for large angular sizes the planar quad is wrong — see §7.3 |
| `IconDrawer` | Matrix push/scale/draw **per sprite** | One instanced draw over a sprite attribute buffer; `gl_VertexID` expands the unit quad |
| `LabelDrawer` | Canvas→ARGB8888 atlas; per-label matrix push and draw | Canvas rasterisation **stays** (it is the right tool and it is Android-side, which is allowed). Atlas becomes `R8` (¼ the memory — it is a coverage mask, not colour). Draw becomes one instanced call per atlas page. `LabelDeclutterer` stays CPU-side and unit-tested |
| `GlowDrawer` | Multi-ring mesh approximating an exponential falloff via linear Gouraud interpolation | Two rings and an **exponential falloff in the fragment shader**. Deletes the "why 8 rings" paragraph in `HorizonLayer` — the producer stops encoding a rendering workaround |
| `SkyGradientDrawer` | 8×10 vertex-coloured dome | Full-screen (or coarse dome) fragment shader. Parity first — port the existing ramp exactly — then §7.1 replaces the ramp |
| `PhaseCompositor` | **Per-pixel CPU composite** into a bitmap, cached by quantised phase | Deleted. The terminator becomes six lines of fragment shader with the `Terminator` as uniforms — continuous, exact, allocation-free. `PhaseGeometry` (pure, already unit-tested) is ported verbatim to GLSL |
| `TextureCache` | Two variants per `ImageRef` (normal + red-shifted) | One variant. Night mode becomes a **single colour transform uniform** applied in every fragment shader (or one post-process pass). Halves image texture memory and deletes `StellarStyler.applyNightMode`'s seven call sites |
| `CameraScrimDrawer` | Full-screen quad | Same, trivially |

### 3.1 Lines stay as lines — the rejected alternative

The obvious GLES3 move is to replace `glLineWidth` with quad geometry expanded in the vertex
shader. **We are not doing this.** Recording why, so it is not "helpfully" reintroduced later:

- **v1 did it and it looked wrong.** v1 extruded lines as *world-space* quads, so width was an
  angular size on the sphere and grew on screen as you zoomed in — the exact behaviour D12 was
  written to outlaw. Screen-space extrusion would not repeat that specific bug (width would stay
  constant in pixels), but the technique carries the association for good reason.
- **Translucent overlap would compound.** Independently-extruded segments overlap on the inside
  of every bend. Our lines are translucent (`GRID_LINE` is **8%** alpha, `HORIZON_LINE` 47%,
  `CONSTELLATION_LINE` 50%) and `GreatCircleSubdivision` splits every great circle into ~72
  segments, so a graticule would double-blend at ~72 joints and read as beaded rather than
  drawn. This is not hypothetical: `SkyColors.ECLIPTIC_LINE` is *already* forced opaque with the
  comment "so ticks merge into the line instead of compounding into bright patches where
  translucent quads overlap."
- **`glLineWidth` is core in GL ES 3.0.** It was never deprecated in ES (the removal is a
  desktop-core-profile thing), so keeping it costs nothing and keeps the parity phase honest:
  lines render identically before and after the port.

**The silent-clamping hazard is therefore left as status quo, not fixed.** It predates this port
and this port does not worsen it. The instrumentation half is **done**: `GLSkyRenderer` reads
`GL_ALIASED_LINE_WIDTH_RANGE` and `GL_SMOOTH_POINT_SIZE_RANGE` into `RendererInfo` and the
diagnostics screen displays both. What remains is one eyeball test, no code, and it belongs in
slice 0:

> The designed widths are a hierarchy — horizon 2.5 dp, ecliptic 1.8 dp, grid 1.5 dp. If a device
> clamps, all three render at 1 px and become indistinguishable. Put the horizon and the RA/Dec
> grid on screen together on real hardware, read the range off the diagnostics screen, and check
> the horizon is visibly heavier. Note the emulator and SwiftShader CI both translate to host
> desktop GL, which is generous about wide lines — so neither can answer this.

**If** measurement shows clamping, the fix is a **mitered triangle strip**: one strip per
polyline with adjacent segments *sharing* their joint vertices, so there is no overlap by
construction and therefore no beading. The 5° subdivision guarantees shallow bends, which is
exactly the regime where miter joins are well-behaved. Naive per-segment quads are the wrong
answer at every stage.

That same mitered strip is, separately, the **known iOS answer** — Metal has no line width at
all and draws every line at exactly 1 px (§6.5). That does not change anything on Android:
`glLineWidth` is chosen here on its Android merits and stays. It does mean the strip is worth
designing rather than discovering, so slice 3 carries one requirement: keep the line builder's
signature polyline-in / vertex-array-out, so a strip builder is a sibling rather than a rewrite.
Do not *build* it speculatively — dead code for a platform that does not exist yet is a worse
trade than writing the design down.

### Shader inventory (phase 1)

Small, and deliberately so:

1. `point.vert/frag` — stars and fixed points, with the PSF.
2. `line.vert/frag` — plain `GL_LINE_STRIP` with a colour uniform; the trivial pass-through
   pair that replaces fixed-function transform and `glColor4f`. Width stays `glLineWidth` (§3.1).
3. `sprite.vert/frag` — instanced screen-space quads; serves icons **and** labels (labels bind an
   `R8` atlas and modulate by colour; icons bind RGBA and modulate by tint). One shader, two
   configurations.
4. `skyquad.vert/frag` — world-space textured quads: images, and later the constellation patches.
5. `glow.vert/frag` — additive gradient bands.
6. `sky.vert/frag` — the gradient dome.

A tiny `ShaderProgram` helper (compile, link, cache uniform locations, report errors loudly in
debug) and a `GlState` object that tracks bound program/VAO/texture to avoid redundant calls —
the same caching discipline the current drawers apply by hand to `glColor4f` and `glLineWidth`.

Shaders live in `assets/shaders/` in the gles3 module. They are **code, not branding**: GPLv3,
not the All-Rights-Reserved asset directories (AGENTS.md's licensing table needs one row added
to say so explicitly, since "anything in assets/" is currently read the other way).

---

## 4. Verification — the part that needs the most thought

This is the single biggest risk in the whole project, and it is not performance. Today's test
discipline works because the interesting logic is CPU-side and pure: `StellarStyler`,
`GreatCircleSubdivision`, `LabelDeclutterer`, `ImageDrawer.quadCorners`, `LabelAtlasPacker`,
`PhaseGeometry`, `SizeFloor` all have plain-JUnit tests with no GL context. **Moving that logic
into shaders moves it out of test coverage**, and `./gradlew check` would get quieter while the
renderer got more complex. That trade must be paid for deliberately:

1. **Keep the pure functions pure, and share them.** `PhaseGeometry.litOffset`, `SizeFloor`,
   `StellarStyler`'s magnitude ramps and B−V curve stay as Kotlin in `:render:api` and are
   *transcribed* into GLSL. Add **conformance tests**: the Kotlin implementation is the golden
   reference, and an instrumented test renders a 1-D LUT through the shader and asserts it
   matches the Kotlin function within tolerance. This catches transcription drift, which is the
   realistic failure mode.
2. **Golden-image instrumented tests.** The `RendererTestActivity` already exists and already
   does screenshot comparison (D31). Extend it into a proper scene corpus — a handful of fixed
   camera/scene/state combinations, captured with `PixelCopy`, compared against checked-in goldens
   with a perceptual tolerance. This is also exactly the harness that proves parity in the first
   place: **run every golden through both backends and diff.** Differences are then a
   deliberate, reviewed list rather than a vibe.
3. **A shader compilation test.** Every shader compiles and links on a real GL context; a
   trivial test, but it turns a class of black-screen field failures into a CI failure. Note that
   `google_atd` CI images are software-rendered (SwiftShader), which does support GLES 3.0 —
   verify this early, since it gates whether any of this runs in CI at all.
4. **The D19 perf gate carries over unchanged** — 100k points, 30 fps floor on Pixel 3a
   hardware, `MIN_FRAMES_FOR_CI` smoke gate in CI. Phase 1 should *beat* GLES1 comfortably (one
   draw call instead of dozens); if it does not, something is wrong and we want to know before
   Part B adds load.

---

## 5. Performance and power

Expected direction is favourable — fewer draw calls, GPU-resident buffers, no per-frame CPU quad
rebuilds — but three specific things need watching:

- **Fill rate, not vertex rate, becomes the limit.** A full-screen sky shader and any
  post-processing chain are per-pixel costs on a 1440p panel. Everything in Part B that runs
  full-screen needs a resolution scale and an off-switch.
- **Shader compilation stall on first frame.** Compiling six programs at `onSurfaceCreated`
  costs tens to hundreds of milliseconds on a cold GPU driver cache and will show up as a splash
  hitch. Mitigation: compile during the existing splash/startup-state read (D48), and consider
  the standard warm-up draw of one pixel per program.
- **`RENDERMODE_WHEN_DIRTY` must survive Part B.** Any animated effect (twinkle, meteor
  particles, a live corona) implicitly demands continuous rendering and therefore continuous
  battery drain. The rule: animated effects are opt-in, and switching one on is what switches
  render mode — never a global change.

---

## 6. Migration and the fate of `:render:gles1`

**Decided: retire it.** Carry both backends through exactly one release for rollback safety,
then delete `:render:gles1`.

The reasoning is that Part B requires *additive* changes to `:render:api` — new primitives
(spherical patches), new render-state fields (eclipse geometry, sky-model parameters, light
pollution). Every one of those would otherwise have to be either implemented twice or explicitly
no-op'd in a backend nobody uses, on a device population that (per §1) rounds to zero. That is a
permanent tax for a temporary comfort. Meanwhile `<uses-feature glEsVersion>` gives us a clean,
supported way to simply not ship to incapable hardware.

### The sequence

Slices 0–6 are Part A. Nothing user-visible changes until slice 6.

| Slice | Content | Done when |
|---|---|---|
| **0 — measure** | No code. Read GLES 3.0 coverage for our installed base off the Play Console (gates the retirement above). Read `RendererInfo`'s line-width range off the diagnostics screen on real hardware and run §3.1's eyeball test. Verify `google_atd` SwiftShader speaks GLES 3.0, since that gates whether any of this runs in CI at all. | The three questions that gate the rest are answered with numbers. |
| **1 — the seam** | Generalise `RenderConnector` from the concrete `GLSkyRenderer` to `SkyRenderer` + `GLSurfaceView.Renderer`. Add `:render:gles3`, its convention plugin and its Konsist rule, plus the `java.nio` denylist on `:render:api` (§6.5). Move `StellarStyler`, `GreatCircleSubdivision`, `LabelDeclutterer`, `LabelAtlasPacker` into `:render:api` now rather than after retirement (§6.5). `ShaderProgram` + `GlState`. A backend that clears and nothing else, behind a debug setting. | `./gradlew check` green; the app runs on either backend; gles3 draws black. |
| **2 — the harness** | The golden-image corpus and the dual-backend diff (§4.2), plus the shader-compile test (§4.3) and the Kotlin↔GLSL conformance LUT harness (§4.1). Built **before** the drawers, so parity is measurable from the first one. | A scene corpus renders through both backends and diffs. This is the project's biggest risk (§4) and it does not exist today. |
| **3 — points and lines** | The star field: one VBO, one draw call, `gl_PointSize` continuous in magnitude, an analytic anti-aliased PSF, and the `GL_SMOOTH_POINT_SIZE_RANGE` hazard gone. Plus the minimal line shader — a near-verbatim carry-over by design (§3.1). | The golden diff against gles1 is a short, reviewed list. |
| **4 — sprites** | Icons and labels, instanced, `R8` atlas. Introduce the `GlyphRasterizer` seam (§6.5). Decide here whether the atlas goes SDF (§8.15) or stays bitmap. | Labels and icons at parity, one draw call per atlas page. |
| **5 — images, glow, gradient, night mode** | Instanced world-space quads with `SizeFloor` moved into the vertex shader; the two-ring exponential glow; the sky dome as a fragment shader porting today's ramp **exactly**; night mode as one colour-transform uniform, collapsing the dual texture cache; `PhaseCompositor` deleted in favour of `PhaseGeometry`/`EclipseGeometry` transcribed to GLSL. | Part A complete. Golden diff clean modulo a reviewed difference list. |
| **6 — flip and retire** | Default to gles3; add `<uses-feature android:glEsVersion="0x00030000" android:required="true" />`; ship one release with both; watch; then delete `:render:gles1`. | One backend. |

Part B then runs in four tiers, ordered so the things that change how the app *reads* land before
the things that change how it *looks*:

| Tier | Content |
|---|---|
| **B1 — the sky** | §7.1/§7.2 in full: the local frame in `RenderState`, the analytic sky model, the twilight sequence and the sun-dependent afterglow, the Belt of Venus, moonlight, light pollution, the translucent ground, and the `magnitudeLimit` coupling. The feature this whole project is most visibly for. |
| **B2 — the map** | The legibility tier: Milky Way (opacity-gated), MSAA, SDF label halos if deferred from slice 4, constellation regions and boundaries, object trails. §8.15–§8.22. |
| **B3 — the art** | Constellation figures (§7.3). Budget it as an art project with a rendering component, not the reverse; the licensing question is the long pole and needs deciding before any plate is traced. |
| **B4 — the bodies** | Continuous eclipses (§7.4), Saturn's rings, lit planet spheres, DSO imagery at true angular size, bloom, extinction and refraction. |

---

## 6.5 Portability — what an eventual iOS port costs, and what it costs us today

v2 has stated KMP-friendliness as a goal since
[high-level-architecture.md](high-level-architecture.md) ("KMP-friendly core… so a later KMP
conversion is a build-file change, not a refactor"), and the invariant has held: the pure modules
import no Android and almost no JVM-only API. The renderer is the one place that goal has never
been examined, and a GLES3 rewrite is exactly the moment to examine it — because three of the
techniques this document recommends have no equivalent on Apple's GPUs at all.

**The governing rule, first, because everything below depends on it: portability is a design
input, not a constraint, and explicitly not a lowest-common-denominator rule.** Where Android can
do something well that Metal cannot, Android does it well and the eventual iOS backend is allowed
to be worse, or to reach a similar result by a different route. Only two things are genuinely
shared: the `SkyRenderer` contract, and the pure `build` half. The `draw` half, the shader
techniques, and the achieved visual quality may all legitimately diverge — which also means the
golden-image corpus (§4.2) is per-backend, not cross-platform. What portability buys is that the
iOS port becomes a rewrite of `draw` against constraints that are already written down, rather
than a research project that discovers them. It does not buy Android being held to Metal's floor.
Where a GLES 3.1/3.2 or extension-gated feature makes Android better, take it behind a capability
check and note what iOS will have to do instead.

**No `GraphicsDevice` abstraction.** The obvious move — a device-agnostic wrapper over buffers,
textures, programs and draws — is over-engineering for one hypothetical second platform, and it
would put a layer of indirection between us and the GL calls precisely where we most want to see
them. The seam we already have is better: the `build` (pure CPU, unit-tested) / `draw` (platform
GL) split that §3 says is the current backend's best feature. Two rules make `draw` the only
thing an iOS port rewrites.

### The portable half lives in `:render:api`

Not in the backend module. `:render:api` is already where `SizeFloor`, `PhaseGeometry`,
`EclipseGeometry`, `SkyProjection` and `Matrix4` live; it is already pure Kotlin; it is already
Konsist-gated; and §6 already planned to lift gles1's four GL-free files there *after*
retirement. Do it up front instead, in slice 1: `StellarStyler`, `GreatCircleSubdivision`,
`LabelDeclutterer` and `LabelAtlasPacker` move into a `…render.api.engine` package, and every new
gles3 geometry builder is written there from the start rather than migrated later.

### Builders emit `FloatArray`, never `java.nio.Buffer`

Today `Buffers.kt`'s direct `FloatBuffer` is threaded through every gles1 drawer. `java.nio` is
JVM-only, and it is exactly the import that [build-and-tooling.md](build-and-tooling.md) §"Layer 3"
earmarked for a KMP-readiness denylist "deferred until KMP work begins". This is when it begins,
for the render path only: add it as a fourth Konsist rule scoped to `:render:api`. The Android
`draw` half wraps the arrays in direct buffers at the GL edge.

**This costs Android nothing**, and that is worth stating because under gles1 it would have. The
current backend re-walks client-side arrays every frame, where an extra copy would sit on the hot
path. Under GLES3 geometry goes into a VBO once per scene rebuild — roughly once a minute for the
busiest layer, and once ever for the star field — so a single array→direct-buffer copy at upload
time is off the hot path entirely.

### Four constraints Metal imposes

In each case the Android technique is chosen on its Android merits, and the constraint is recorded
so the port is not a surprise.

1. **`glLineWidth` has no Metal equivalent** — Metal draws lines at exactly 1 px, always. §3.1's
   reasoning for keeping `GL_LINE_STRIP` on Android is unaffected and still holds, so **Android
   keeps `glLineWidth`**. The mitered triangle strip §3.1 describes as a clamping contingency is
   also the iOS answer; §3.1 now says so, and asks slice 3 for one cheap thing: a line-builder
   signature that a strip builder can slot into.
2. **ETC2 is Android-only** — Apple GPUs do ASTC. §7.3 sizes the constellation-art budget on ETC2
   (~2.7 MB vs ~16 MB for a 2048² plate, the difference between shipping 48 figures and not), and
   that number is right for Android. The fix is to make the art pipeline **compress from source
   PNG at build time to a per-platform format** rather than checking in ETC2 blobs. No Android
   cost — it is strictly better asset hygiene, and it keeps the pipeline honest about what its
   source of truth is. `ImageRef` already hides format selection inside the texture loader, so
   `AssetImageLoader` is the only thing that changes.
3. **Transform feedback does not exist in Metal** (the equivalent is a compute pass). §8.8's
   meteor particles and §8.9's comet tails should still use **whatever is fastest on Android**,
   transform feedback included. Only the substitution needs recording. At our particle counts a
   CPU-updated instanced buffer is very likely competitive anyway, which would make the question
   moot — measure when we get there rather than deciding it on paper now.
4. **Label rasterization has no shared implementation.** `LabelDrawer`'s `android.graphics.Canvas`
   text path is the right tool on Android; iOS would use CoreText. The packer and the declutterer
   are already pure. Add a `GlyphRasterizer` interface in `:render:api` — measure a string,
   rasterize it into a caller-supplied coverage buffer — so the Canvas implementation is the only
   Android-specific part. This is a pure win regardless of iOS: it is an interface around code
   that already exists, and it is the seam an SDF atlas (§8.15) plugs into.

**What needs no thought at all:** `gl_PointSize` (Metal has `[[point_size]]` and `[[point_coord]]`),
instancing, UBOs, sRGB textures and framebuffers, FBOs, MSAA, `R8` textures, `gl_VertexID`.

### Shader authoring rules

So that `glslang` + SPIRV-Cross translate cleanly to MSL: `#version 300 es`; explicit
`layout(location = …)` on every vertex attribute and fragment output; named `std140` uniform
blocks with explicit bindings rather than loose uniforms; no `gl_FragColor`/`gl_FragData`; no
`gl_DrawID` or `gl_BaseVertex`.

**None of these cost Android anything** — they are ordinary modern-GLSL hygiene a GLES3 backend
would want regardless, which is why they are worth adopting as house style rather than as a
concession. And the rule is not "restrict Android to the intersection": a shader that needs
something Metal cannot express goes behind a capability check, with a note on what iOS does
instead.

Shaders are **code, not branding** — GPLv3, classified `[gpl]` in `ASSET-LICENSES.txt`, which
`tools/check_asset_licenses.py` will demand before they can merge. AGENTS.md's licensing table
needs one row saying so explicitly, since a file under `assets/` is currently read the other way.

### Rejected: ANGLE-over-Metal

ANGLE's Metal backend would let this exact GLES3 code run on iOS unchanged, and that is genuinely
tempting. It is rejected because it adds a large native dependency to a project whose F-Droid
flavor exists to have none, cedes control of the GL layer at the exact moment we are taking
ownership of it, and would make every performance question a question about somebody else's
translation layer. The `build`/`draw` split gets most of the portability benefit for none of that.

### What is deliberately not decided

Whether the pure half ever becomes a real Kotlin Multiplatform module, or stays JVM + Android with
an iOS implementation written separately against the same design. Nothing above depends on the
answer, and the answer depends on how Room-KMP and Compose-Multiplatform look when an iOS port is
actually scheduled.

---
---

# Part B — what the programmable pipeline unlocks

Everything below is *enabled by* phase 1 and *sequenced after* it. Each item notes the API
extension it needs, because those are the changes that make retiring GLES1 (§6) worthwhile.

## 7. The four asked-for features

### 7.1 A truer sky, and the thing that currently makes it impossible

Today: `SkyGradientDrawer` is a faithful port of v1's `SkyBox` — eight latitude bands of ten
steps, a linear blue ramp of intensity 70→50 toward the sun and a grey 40→0 away from it,
rotated so its pole points at the Sun, and skipped entirely in night mode. Eighty vertices, built
once, coloured by band latitude alone.

**The blocker is not the fixed-function pipeline. It is that the renderer does not know where the
horizon is.** `SkyGradient` carries `sunDirection` and nothing else. There is no zenith, no
observer, no notion of "above" or "below" — so the dome cannot express the one thing that matters
most about the sky, which is that its appearance is governed by the Sun's *altitude*, not merely
its direction. A shader would not fix that on its own. The API extension is the load-bearing part.

`RenderState.skyGradient` therefore grows from a direction into a lighting block:

```kotlin
data class SkyLighting(
    val zenith: Vector3,                  // unit, celestial — the observer's local frame,
    val north: Vector3,                   //   which is what gives us altitude and azimuth
    val sunDirection: Vector3,            // already present
    val moonDirection: Vector3,
    val moonIlluminatedFraction: Double,
    val turbidity: Double = 2.5,          // daytime haze
    val lightPollution: Double = 0.0,     // Bortle-ish; user-set or location-derived
    val groundOpacity: Double = 0.0,      // §7.2; 0 preserves today's behaviour exactly
)
```

Every field is already sitting in the producer. `MapViewModel` holds a `LocalFrame` from
`SkyModel.localFrame` (zenith, true north, refreshed on the shared clock bus) and already computes
the Sun's position for today's gradient; `MeeusEphemeris` supplies the Moon's position and
`illuminatedFraction`. This is plumbing, not new astronomy — and it is *additive*, which is exactly
the kind of `:render:api` growth §6's retirement recommendation exists to avoid dual-implementing.

With altitude in hand, the fragment shader can evaluate an analytic sky model per pixel:

- **Daytime scattering.** Preetham or Hošek–Wilkie give luminance and chromaticity as a function
  of view direction, solar zenith angle and turbidity. Preetham is roughly thirty ALU ops —
  trivial on any GLES3 part — and it produces the correct characteristic behaviour for free: the
  bright circumsolar aureole, the darker band ~90° from the Sun, the brightening toward the
  horizon.
- **Twilight**, the interesting regime and the one v1 never attempted. Below-horizon Sun positions
  should produce the real sequence, and the thresholds already exist in `:core:astronomy` as
  `RiseSet.CIVIL_TWILIGHT_SUN_ALT_DEG` (−6°) and `NAUTICAL_TWILIGHT_SUN_ALT_DEG` (−12°), with
  astronomical twilight at −18°. Because these are *named, teachable* stages, §0's last rule
  applies: this is accuracy worth having.
- **The Belt of Venus and Earth's shadow.** The pink anti-solar band with the planet's own shadow
  rising blue-grey beneath it in the east. A genuine, nameable phenomenon that almost no mobile
  planetarium renders, and essentially free once scattering is evaluated along the anti-solar
  direction.
- **Moonlight.** A second scattering term from the Moon's direction, scaled by its illuminated
  fraction. A full Moon genuinely produces a blue-grey sky and genuinely washes out faint stars.
- **Light pollution.** A horizon-weighted skyglow term. Honest, useful, and the single best
  explanation of why the real sky does not match the app.
- **Correctness plumbing.** Evaluate in linear space, tone-map, output sRGB, and **dither** —
  three lines that eliminate the banding an 8-bit gradient across a 1440p panel otherwise
  guarantees.

**Coupling worth building:** `RenderState.magnitudeLimit` already exists. Driving it from the
computed sky brightness makes stars fade in and out naturally through twilight, and makes a full
Moon visibly wash out the faint end. That turns time travel across sunset from a lighting change
into the app's actual answer to "why can't I see anything tonight?".

**Parity first.** Slice 5 ports the existing 70/50/40/0 ramp to a shader *exactly*, verified by
the golden diff; B1 replaces the ramp. Those are separate changes and must not be one change.

### 7.2 The two glows, and the ground

There are two different things in this codebase called "the horizon glow", they want opposite
treatments, and conflating them is how the current design got stuck.

**The twilight afterglow is atmospheric, and belongs in the sky shader.** This is the light you
actually see at the horizon after the Sun has set, and today nothing in the app models it at all.
It is driven by the Sun's altitude *and azimuth*: a band low in the sky, centred on the sunset
azimuth, orange near the horizon grading up through pink into deep blue, fading with azimuthal
distance from the Sun and with the Sun's depth below the horizon through the civil/nautical/
astronomical sequence — until at astronomical twilight it is gone and the sky is genuinely dark.
It falls out of the §7.1 evaluation for free once the shader has the local frame: the same
scattering integral, evaluated for a below-horizon Sun. This is the sun-dependent glow, and it is
the visible payoff of the whole B1 tier.

**The green horizon marker is a map affordance, and stays a `GlowPrimitive`.** `HorizonLayer`
paints an azimuthally uniform glow in `SkyColors.HORIZON_LINE` below the horizon circle. It is not
a physical phenomenon and must not become one: it marks where the ground is on a map, and it has
to stay legible at midnight when there is no afterglow at all, and in night mode when the sky
shader is off entirely. What §7.2 buys it is the fix it was always owed. Today
`HorizonLayer.glowMesh` builds **eight rings at 1° spacing** whose count exists purely to
piecewise-approximate an exponential falloff, because Gouraud shading interpolates alpha linearly —
its own KDoc says so at length. Two rings and an exponential falloff in the fragment shader delete
both the rings and the paragraph explaining them, and the producer stops encoding a renderer
limitation.

Getting these two right also means they must **compose**: the marker is additive over whatever the
sky shader produced, so at sunset the green sits on top of the orange rather than replacing it.

**The ground.** An actual shaded hemisphere below the horizon rather than a bare line — but this
is a **product decision before it is a rendering one**. v1 and v2 both let you look "through" the
Earth at the sky below, which is genuinely useful ("where is the Sun right now?", "is Jupiter up
yet?") and would be destroyed by an opaque ground. Decided: a **translucent, shader-shaded ground
with an opacity preference**, and `groundOpacity = 0` — today's exact behaviour — must remain
reachable, because some users rely on it. Default low.

**A horizon profile** — a silhouette skyline of trees, hills or a city, stored as a 1-D height
texture indexed by azimuth and sampled in the ground shader — is extremely cheap and does more for
"this looks like standing outside" than any other single change. Sequence it *after* the ground
ships; a small set of stock profiles first, a user-supplied one later.

**Atmospheric extinction and reddening** belong here too: objects near the horizon are genuinely
dimmer and redder, because airmass rises steeply below ~20° altitude. Applying it in the star and
image shaders is one dot product away once the shader knows the zenith, is physically correct, and
quietly teaches why nobody observes near the horizon. It pairs with **atmospheric refraction**
(~34′ of lift at the horizon), which belongs in `:core:astronomy` as pure, testable math of exactly
the kind this codebase is already good at.

**API extension:** `SkyGradient` becomes `SkyLighting` as above, carrying `groundOpacity` and a
profile id. The ground is observer-derived state like the sky dome, not scene content, so it
belongs in `RenderState` rather than as a layer.

### 7.3 Greek-myth constellation overlays

The most valuable and the most expensive, and the expense is **not** the rendering.

**The rendering problem.** `ImagePrimitive` is a planar quad — correct for a 0.5° Moon, wrong for
a 20–30° constellation figure, which would visibly fail to follow the sphere and distort badly
toward its edges. This needs a new primitive: a **spherical patch** — a subdivided mesh on the
unit sphere carrying a texture and a UV parameterisation, anchored the way Stellarium does it, by
registering three named stars in the artwork to their three sky positions. That determines
placement, scale and rotation from the data rather than from hand-tuned constants, which matters
because it is the only approach that stays correct as the star catalog changes.

GLES3 handles this comfortably: one indexed mesh per figure, ETC2-compressed mipmapped textures
(a 2048² artwork is ~2.7 MB compressed against ~16 MB uncompressed — the difference between "48
figures ship" and "they do not"), alpha-blended with a global opacity uniform.

**The presentation problem, which is the real one.** Constellation art that is always on is
noise. The rules matter more than the pictures:
- an opacity slider, defaulting low;
- fade out as you zoom in (past roughly 30° FOV the figure is off-screen anyway and its lines are
  what you want);
- fade the figure you are pointing at *up* and its neighbours *down*, so the sky does not become
  a collage;
- heavily attenuated in night mode.

**The asset problem, which is the expensive one.** Roughly 48 classical figures, each needing
artwork plus three-star registration. Licensing is a live concern given v2's split-license
structure. The sound path is derivation from public-domain sources — Bayer's *Uranometria*
(1603), *Urania's Mirror* (1824), Hevelius' *Firmamentum Sobiescianum* (1690) — cleaned,
recoloured and cut out, with the derived assets living under
`app/src/main/assets/branding/` and therefore All Rights Reserved per AGENTS.md. This wants its
own decision entry before any art is commissioned or traced, and it is the long pole: budget it
as an art project with a rendering component, not the reverse.

**API extension:** a new `SkyPatchPrimitive` (or `ImagePrimitive` gains a `sphericalPatch` mode)
plus per-figure anchor data in the catalog. This is the single clearest example of why §6
recommends retiring GLES1 rather than dual-implementing.

### 7.4 Lunar eclipses

> **Update (D106, 2026-08-24): shipped on GLES1, not deferred to this backend.** The claim below —
> that an eclipse "cannot be done this way at all" — turned out to be about *per-frame* continuity,
> which isn't what an eclipse needs: it evolves over hours, and `PhaseCompositor`'s existing
> quantise-and-cache cadence (~once a minute, on layer resubmission) is comfortably fast against
> that. The CPU version described in [lunar-eclipse.md](lunar-eclipse.md) ships the umbra/penumbra
> tinting this section wanted, built additively onto `Terminator`/`PhaseGeometry` exactly as
> sketched below, so the GLES3 shader version remains available as a strict improvement (exactly
> continuous rather than cadence-quantised) rather than the only way to get eclipses at all.

Today, `PhaseCompositor` paints the solar terminator *and* Earth's shadow into the Moon's bitmap
on the CPU, cached against a quantised phase and a quantised shadow geometry
(`TextureKey(ref, PhaseKey(…, EclipseKey))`). It works, and it ships. What it is not is
continuous: an eclipse evolves over hours, and the cache re-composites in discrete steps rather
than smoothly.

So the shader version is a strict improvement on something that exists, not the only way to get
eclipses — and the transcription target already exists too, as the pure `PhaseGeometry` and
`EclipseGeometry` in `:render:api`, which is precisely why they were put there. In a fragment
shader it is nearly free, and it is genuinely beautiful:

- Uniforms carry both shadows — the solar terminator (already modelled by `PhaseGeometry`) **and**
  the Earth-shadow geometry: the direction of the anti-solar point and the angular radii of the
  umbra and penumbra at the Moon's distance.
- The shader produces the real appearance: gradual penumbral dimming, a soft-edged umbral
  boundary (soft because Earth has an atmosphere — a hard edge is the tell of a fake), and the
  deep copper-red interior, reddest near the umbra's centre, from sunlight refracted through
  Earth's atmosphere. Exposing a Danjon-scale parameter for the depth of the red is a nice touch
  and is real observational vocabulary.
- The astronomy — shadow-cone geometry, umbra/penumbra radii, contact times — belongs in
  `:core:astronomy` as pure functions, testable against published eclipse circumstances. That is
  precisely the shape of work this codebase already does well (cf. the USNO-golden rise/set solver,
  D50).

The same shader machinery then also gives, at very little marginal cost:

- **Solar eclipses** — the Moon's disc occluding the Sun, with limb darkening and a procedural
  corona at totality. (The occlusion *ordering* is already correct thanks to D18's
  distance-sorted solar-system layer.)
- **Transits** — Mercury and Venus crossing the solar disc.
- **Jovian satellite phenomena** — Io's shadow crossing Jupiter's cloud tops.

**API extension:** `ImagePrimitive.terminator` grows a sibling `eclipse` field (or `Terminator`
becomes a small sealed hierarchy of shadow sources). Additive.

---

## 8. Further astronomical features GLES3 enables

Offered as candidates, roughly in descending value-per-effort. Mostly suggestions rather than
commitments — but §6's B2 tier does name several of "The map tier" below, because per §0 those
are the ones that make Sky Map a better *map* and so earn their place ahead of the rest.

1. **The Milky Way, for real.** An all-sky panorama (the ESO Gigagalaxy Zoom or Mellinger
   mosaics, both freely licensed) as a cube map, brightness-scaled and gated by the computed sky
   brightness and light pollution from §7.1. Probably the **single largest visual upgrade per
   unit of effort in this entire document** — one cubemap sample in the sky shader — and it is
   what makes the app look like the sky rather than a diagram.
2. **HDR and bloom.** Render to a float FBO, threshold the bright end, blur, composite. Sirius,
   Venus and Jupiter get real glare, so brightness becomes *legible* instead of being a slightly
   larger dot. This is what finally retires D12's "GLES1 can only bake brightness into vertex
   colour" compromise rather than merely mitigating it.
3. **True stellar PSF and scintillation.** Continuous magnitude→size in the vertex shader, an
   analytic Airy/Gaussian profile in the fragment shader, and an optional subtle twinkle keyed to
   altitude (low stars twinkle more — because they do). Turns the star field from a scatter plot
   into a photograph.
4. **Proper motion and precession, animated.** With proper motion as a vertex attribute and
   "years from epoch" as a uniform, running the sky forward 50,000 years and watching the Big
   Dipper deform costs *one uniform update*. Today it would mean re-uploading the entire star
   buffer every frame. No mainstream mobile app does this well, and it is a spectacular teaching
   tool that falls directly out of the port.
5. **Saturn's rings and Jupiter's moons on the map** — D89, deferred to info cards precisely
   because the renderer could not do it. Rings need a tilted annulus with the planet's shadow
   falling across them and the rings' shadow falling on the planet: a shader job, unblocked here.
6. **Planets as lit spheres at high zoom.** Instead of a textured disc, an actual sphere with the
   correct sub-observer longitude, so Mars shows the face it is really showing and Jupiter's Great
   Red Spot appears only when it is actually turned toward us. A quiet "the app really knows"
   moment.
7. **DSOs as real imagery at true angular size**, cross-fading from icon to photograph as you
   zoom in. `ImagePrimitive.visibleBelowFovDeg` already anticipates exactly this; compressed
   mipmapped textures make it affordable.
8. **Meteor showers as GPU particles** — instanced quads or transform feedback, with trails,
   radiating correctly from the radiant. The D58 layer currently draws the radiant, not the
   meteors.
9. **Comet tails** — the deferred comet layer. A tail is an anti-solar-oriented textured plume;
   only reasonable with shaders.
10. **Satellite trails and orbital shadow** (D92) — a pass fades out when the satellite enters
    Earth's shadow, which is exactly *why* passes end, and showing it explains it.
11. **Star trails / long-exposure mode** — FBO ping-pong accumulation during time travel.
    Cheap once off-screen targets exist, and immediately shareable.
12. **Better colour throughout** — B−V mapped through an actual blackbody curve rather than the
    current three-stop lerp, blended in linear space.
13. **Zodiacal light and airglow** — faint, real, and nearly free inside the sky shader.
14. **Night mode as a post-process** — noted in §3 as a parity simplification, but worth calling
    out as a *quality* win too: it finally reddens photographs and DSO imagery correctly, which
    the current per-primitive colour bake handles only by shipping a second copy of every texture.

### The map tier

The list above is mostly about making the sky more convincing. This one is about making the *map*
better, which per §0 is the higher priority — and these are the items the first draft of this
document missed.

15. **An SDF label atlas.** Rasterize each glyph once as a signed distance field instead of once
    per size. Crisp at any scale, so `RenderState.labelScaleFactor` stops forcing an atlas
    rebuild; free outlines and halos, which is what will keep labels readable once a Milky Way or
    a bright twilight sky sits behind them; and it unblocks label rotation (the sky-up angle
    deferred since D30). It is also the most portable text path — an iOS backend needs only a
    different implementation behind §6.5's `GlyphRasterizer`. Strong enough that slice 4 should
    decide it rather than defaulting to a bitmap atlas and revisiting later.
16. **MSAA.** GLES3 has multisampled renderbuffers, and the grid, constellation figures and
    horizon line are visibly jagged today. One FBO, pure map-quality win, no product decision
    attached.
17. **Constellation region shading and boundaries.** Draw the IAU boundaries, and fill the
    constellation you are currently pointing at. Per-pixel region fill is a shader job. This is a
    *map* feature in a way the Greek-myth artwork of §7.3 is not — it answers "which constellation
    am I looking at?" directly, and unlike §7.3 it needs no art assets and no licensing decision.
18. **Object trails and trajectories.** One instanced polyline with fade-by-age, where today it
    would mean rebuilding CPU geometry every frame. This is not speculative: object trails are
    already a designed feature in [time-travel.md](time-travel.md) (D112–D114), so this is a
    concrete case where the renderer is the thing blocking a planned feature.
19. **An anti-aliased, zoom-adaptive grid.** Screen-space derivatives let the RA/Dec graticule
    subdivide smoothly as you zoom rather than stepping between densities, with lines that stay
    crisp at any width. `GridLayer` currently picks a fixed spacing.
20. **Eyepiece, binocular and telescope FOV overlays** — a true-scale circle for the user's actual
    optics, with the sky outside it dimmed rather than occluded. Cheap, and squarely a map feature
    for the users who are standing at a telescope.
21. **Colour-vision-deficiency and high-contrast modes**, as a post-process colour transform. This
    is the same uniform-driven machinery that replaces the night-mode texture bake in §3, so it is
    nearly free once that lands — and it is an accessibility capability the current pipeline
    cannot offer at all, since it bakes colour per primitive.
22. **Milky Way, with §0 attached.** It stays item 1 above as the biggest visual win per unit of
    effort, but it is also the single item most capable of burying the map. Ships with an opacity
    control, a modest default, and gating by the computed sky brightness and light pollution from
    §7.1.

---

## 9. Questions — settled and open

### Settled by this revision

| Was | Now |
|---|---|
| Retire GLES1, or keep it as a fallback? (§6) | **Retire it.** Both backends for exactly one release, `<uses-feature glEsVersion>` to filter incapable hardware, then delete. Part B's API growth is additive and would otherwise have to be dual-implemented or no-op'd forever. |
| Is the ground opaque, translucent, or user-controlled — and does "look through the Earth" survive? (§7.2) | **Translucent, with an opacity preference, defaulting low.** `groundOpacity = 0` reproduces today's behaviour exactly and stays reachable. |
| How does the horizon glow improve? (§7.2) | Two things, separated. The **twilight afterglow** becomes sun-dependent and moves into the sky shader; the **green horizon marker** stays a `GlowPrimitive` and gets its exponential falloff. They compose additively. |
| What does iOS do to any of this? (not previously asked) | §6.5. Design for portability, build Android-only, and never let portability make the Android result worse. |

### Still open

1. **What does the Play Console say about GLES 3.0 coverage for our installed base?** (§1) Gates
   the retirement above. Slice 0.
2. **Does real hardware clamp `glLineWidth`?** (§3.1) The instrumentation is built and shipping —
   `RendererInfo.lineWidthRange` on the diagnostics screen — so this is now one device and five
   minutes, not a project. Slice 0. If it clamps, the mitered strip becomes an Android fix rather
   than only an iOS one.
3. **Do the `google_atd` CI emulator images support GLES 3.0 under SwiftShader?** (§4.3) If not,
   what does the CI renderer gate become? Slice 0, because it determines whether slice 2's harness
   can run in CI at all.
4. **Golden-image tolerance and storage** (§4.2): perceptual metric, threshold, and whether
   goldens are checked in or generated.
5. **Preetham or Hošek–Wilkie** for the sky model? (§7.1) Cheaper and well-trodden versus better,
   especially at twilight — which is the regime we care most about. A spike, not a debate.
6. **Constellation art: derive from public-domain plates, or commission?** (§7.3) Sets both the
   schedule and the licence, and it is the long pole of the B3 tier.
7. **SDF or bitmap label atlas?** (§8.15) Decide in slice 4 rather than deferring — the answer
   changes what slice 4 builds.
8. **Does the pure half ever become a real KMP module?** (§6.5) Deliberately not decided; nothing
   in this design depends on the answer.
