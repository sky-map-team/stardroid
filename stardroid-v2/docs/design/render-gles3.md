# Detailed Design: `:render:gles3` — the GL ES 3.0 backend

**Status: PROPOSED** — design only, no code. Supersedes nothing yet; `:render:gles1`
([render-api.md](render-api.md)) remains the shipping backend until phase 1 lands and is
verified.

This document plans the port of the renderer from the GL ES 1.x fixed-function pipeline to a
programmable GL ES 3.0 pipeline. It is in two halves, deliberately:

- **Part A (§1–§6) — parity.** A new backend that draws exactly what ships today, behind the
  unchanged `SkyRenderer` contract. No new pixels, no new features, no API change. This is the
  part that must be boring.
- **Part B (§7–§9) — the unlocks.** What the programmable pipeline then makes possible: a true
  sky gradient, a real horizon and ground, Greek-myth constellation art, lunar eclipses, and a
  wider slate of astronomical features that GLES1 structurally cannot render.

Keeping these apart matters. Every GLES-3 rewrite that has ever gone wrong went wrong by doing
both at once and losing the ability to say whether a visual difference was a bug or a feature.

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
| `LineDrawer` | Width via `glLineWidth` | GL ES only guarantees a maximum line width of 1.0, and out-of-range values are **silently clamped** (no `GL_INVALID_VALUE`, no log). We never query the range, so if a device clamps we get no signal. Whether it currently bites is unmeasured — see §3.1 |
| `LabelDrawer` TODO | "one matrix push + draw call per label … once catalog layers submit hundreds, batch" | no instancing |
| `IconDrawer` | Same: a `glPushMatrix`/`glScalef`/`glDrawArrays` per sprite | no instancing |
| `PhaseCompositor` KDoc (D88) | The lunar terminator is composited **per pixel on the CPU** into a bitmap, quantised into phase buckets, and re-uploaded | no fragment shader |
| `TextureCache` | Every `ImageRef` is held **twice** — normal and a red-shifted night-mode variant | night mode is a per-primitive colour bake, not a post-process |
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

1. `MainActivity.kt:342` — `setEGLContextClientVersion(1)`.
2. `RendererTestActivity.kt:186` — same.
3. `RenderConnector` — typed to the concrete `GLSkyRenderer`, not to `SkyRenderer`.

Item 3 is a one-line generalisation that should land *first*, as its own tidy-up: `RenderConnector`
should hold a `SkyRenderer` plus a `GLSurfaceView.Renderer` (the two roles the backend plays),
so the choice of backend becomes a single construction-site decision. That is the entire
"pluggability" work. It is small because the architecture already paid for it.

The Konsist gate (D20) needs one new rule: `:render:gles3` may depend on `:render:api` and the
Android SDK, and nothing may depend on `:render:gles3` except `:app`.

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
| `TextureCache` | Two variants per `ImageRef` (normal + red-shifted) | One variant. Night mode becomes a **single colour transform uniform** applied in every fragment shader (or one post-process pass). Halves image texture memory and deletes `StellarStyler.applyNightMode`'s five call sites |
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
and this port does not worsen it. Two cheap follow-ups, independent of GLES3:

1. Log `GL_ALIASED_LINE_WIDTH_RANGE` on the diagnostics screen. One `glGetFloatv`.
2. Eyeball test, no code: the designed widths are a hierarchy — horizon 2.5 dp, ecliptic 1.8 dp,
   grid 1.5 dp. If a device clamps, all three render at 1 px and become indistinguishable. Put
   the horizon and the RA/Dec grid on screen together on real hardware and check the horizon is
   visibly heavier. Note the emulator and SwiftShader CI both translate to host desktop GL, which
   is generous about wide lines — so neither can answer this.

**If** measurement ever shows clamping, the fix is a **mitered triangle strip**: one strip per
polyline with adjacent segments *sharing* their joint vertices, so there is no overlap by
construction and therefore no beading. The 5° subdivision guarantees shallow bends, which is
exactly the regime where miter joins are well-behaved. Naive per-segment quads are the wrong
answer at every stage.

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

**Recommendation: retire it.** Carry both backends through exactly one release for rollback
safety, then delete `:render:gles1`.

The reasoning is that Part B requires *additive* changes to `:render:api` — new primitives
(spherical patches), new render-state fields (eclipse geometry, sky-model parameters, light
pollution). Every one of those would otherwise have to be either implemented twice or explicitly
no-op'd in a backend nobody uses, on a device population that (per §1) rounds to zero. That is a
permanent tax for a temporary comfort. Meanwhile `<uses-feature glEsVersion>` gives us a clean,
supported way to simply not ship to incapable hardware.

Suggested sequence:

- **Slice 1 — the seam.** Generalise `RenderConnector` to `SkyRenderer`; add the empty
  `:render:gles3` module, convention plugin, Konsist rule; `ShaderProgram`/`GlState`
  infrastructure; a black-screen backend that clears and nothing else, selectable by a debug
  setting. No user-visible change.
- **Slice 2 — the golden-image harness.** Extend `RendererTestActivity` into the scene corpus
  and dual-backend diff (§4.2). Built *before* the drawers, so parity is measurable from the
  first one.
- **Slice 3 — points and lines.** The star field (the big win: one draw call, continuous
  magnitude→size, a real PSF, and the `GL_SMOOTH_POINT_SIZE_RANGE` hazard gone) plus the
  minimal line shader. Lines are a near-verbatim carry-over by design (§3.1).
- **Slice 4 — sprites.** Icons and labels, instanced, `R8` atlas.
- **Slice 5 — images, glow, gradient, night mode.** Includes deleting `PhaseCompositor` and
  collapsing the dual-texture cache. Parity phase complete.
- **Slice 6 — flip the default**, ship, watch. Then delete `:render:gles1` (and lift its four
  GL-free files into `:render:api` where they belong).

Only then does Part B begin.

---
---

# Part B — what the programmable pipeline unlocks

Everything below is *enabled by* phase 1 and *sequenced after* it. Each item notes the API
extension it needs, because those are the changes that make retiring GLES1 (§6) worthwhile.

## 7. The four asked-for features

### 7.1 A truer sky gradient

Today: `SkyGradientDrawer` is a faithful port of v1's `SkyBox` — eight latitude bands, a linear
blue ramp of intensity 70→50 toward the sun and a grey 40→0 away from it, rotated so its pole
points at the Sun, and skipped entirely in night mode. It is a rough gesture at daylight, not a
sky.

The GLES3 version is a fragment shader evaluating an analytic sky-luminance model per pixel:

- **Daytime scattering.** The Preetham or Hošek–Wilkie analytic models give luminance and
  chromaticity as a function of view direction, solar zenith angle, and a turbidity parameter.
  Preetham is roughly thirty ALU ops — trivial on any GLES3 part — and it produces the correct
  characteristic behaviour for free: the bright circumsolar aureole, the darker band ~90° from
  the Sun, the brightening toward the horizon.
- **Twilight.** The interesting regime, and the one v1 never attempted. Below-horizon Sun
  positions should produce the real sequence: civil, nautical and astronomical twilight bands;
  the **Belt of Venus** (the pink anti-solar band) with **Earth's own shadow** rising beneath it
  in the east. This is a genuine, nameable, teachable phenomenon that almost no mobile
  planetarium renders, and it is essentially free once the sky is a shader — it falls out of
  evaluating scattering along the anti-solar direction.
- **Moonlight.** A second scattering term from the Moon's direction, scaled by its illuminated
  fraction. A full Moon genuinely produces a blue-grey sky and genuinely washes out faint stars.
  This is what makes the app's answer to "why can't I see anything tonight?" *visible* rather
  than merely stated.
- **Light pollution.** A Bortle-scale parameter, either user-set or derived from location, as a
  horizon-weighted skyglow term. Honest, useful, and the single best explanation of why the real
  sky does not match the app.
- **Correctness plumbing.** Evaluate in linear space, tone-map, output sRGB, and **dither** —
  three lines that eliminate the banding an 8-bit gradient across a 1440p panel otherwise
  guarantees.

**Coupling worth building:** `RenderState.magnitudeLimit` already exists. Driving it from the
computed sky brightness makes stars fade in and out naturally through twilight — which turns
time travel across sunset from a lighting change into an *experience*.

**API extension:** `SkyGradient` grows from `sunDirection` alone to carry moon direction and
phase, turbidity, and a light-pollution parameter. Additive; producers already have all of it.

### 7.2 A nicer horizon and ground

Today: one great-circle `LinePrimitive` plus an 8-ring additive glow whose ring count exists
purely to fake an exponential curve.

- **A real ground.** An actual hemisphere below the horizon rather than a line. Note this is a
  **product decision before it is a rendering one**: v1 and v2 both let you look "through" the
  Earth at the sky below, which is genuinely useful ("where is the Sun right now?") and would be
  destroyed by an opaque ground. The answer is almost certainly a semi-transparent, shader-shaded
  ground with an opacity preference — not a binary.
- **A horizon profile.** A silhouette skyline — trees, hills, a city — stored as a 1-D height
  texture indexed by azimuth, sampled in the ground shader. Extremely cheap, and it does more for
  "this looks like standing outside" than any other single change. A small set of stock profiles;
  a user-supplied one is a natural later step.
- **Atmospheric extinction and reddening.** Objects near the horizon are genuinely dimmer and
  redder — airmass rises steeply below ~20° altitude. Applying this in the star and image shaders
  (a function of altitude, one dot product away) is physically correct, visually striking, and
  quietly teaches why nobody observes near the horizon. Pairs with **atmospheric refraction**
  (~34′ of lift at the horizon), which belongs in `:core:astronomy` and is pure, testable math of
  exactly the kind this codebase is good at.
- **The glow, properly.** Two rings, exponential falloff in the shader, and the `HorizonLayer`
  producer stops encoding a renderer limitation.

**API extension:** a `Ground` render-state block (opacity, profile id) — the ground is
observer-derived state like the sky dome, not scene content, so it belongs in `RenderState`
alongside `SkyGradient` rather than as a layer.

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

Today, `PhaseCompositor` paints the solar terminator into the Moon's bitmap on the CPU, cached
against a *quantised* phase. An eclipse cannot be done this way at all: it evolves over hours,
continuously, and would demand a full per-pixel recomposite and texture re-upload every frame.

In a fragment shader it is nearly free, and it is genuinely beautiful:

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

Offered as candidates, roughly in descending value-per-effort. These are suggestions, not a
commitment.

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

---

## 9. Open questions

1. What does the Play Console actually say about GLES 3.0 coverage for our installed base? (§1)
   This gates the §6 recommendation to retire GLES1.
2. Does real hardware clamp `glLineWidth`? (§3.1) Cheap to answer and worth answering
   independently of this port — it is a live question about the *shipping* app, not the new one.
3. Do the `google_atd` CI emulator images support GLES 3.0 under SwiftShader? If not, what does
   the CI renderer gate become? (§4.3)
4. Golden-image tolerance and storage: perceptual metric, threshold, and whether goldens are
   checked in or generated. (§4.2)
5. Is the ground opaque, translucent, or user-controlled — and does "look through the Earth"
   survive? (§7.2) A product question, needed before the ground shader is written.
6. Constellation art: derive from public-domain plates, or commission? Sets both the schedule and
   the licence. (§7.3)
7. Does Part B's API growth stay additive on `:render:api`, or does the spherical patch justify a
   contract revision? (§7.3)
8. Should the sky model be Preetham (cheaper, well-trodden) or Hošek–Wilkie (better, especially at
   twilight)? A spike, not a debate. (§7.1)
