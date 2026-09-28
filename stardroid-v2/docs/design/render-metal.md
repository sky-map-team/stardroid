# Detailed Design: `:render:metal` — the Metal backend (iOS)

**Status: IN PROGRESS** — slices 2a–2b built (stars, lines, glows, sky dome, camera scrim,
offscreen render tests, and an iOS harness app); images, icons and labels to follow (§6). D117.

`:render:metal` is the iOS `SkyRenderer`: an iOS-only Kotlin Multiplatform module that calls
Metal directly through Kotlin/Native. It is a sibling of `:render:gles1` and `:render:gles3`
behind the same contract, with the same thread model and the same draw order, and it draws what
`:render:gles3` draws.

## 1. Why Kotlin/Native and not Swift (D117)

Most of a backend is not API calls. Layer ordering, per-layer caches, per-frame image sizing,
icon projection, and label declutter and fade are roughly 2,000 of `:render:gles3`'s 2,800
lines, and none of it depends on which GPU API runs underneath. A Swift backend would
reimplement all of that. It would also read every scene across the Kotlin/Swift framework
boundary, object by object, 100k points at a time. Kotlin/Native keeps one language and one set
of helpers, and its render tests run on the simulator through Gradle, in the CI job that already
exists.

What that gives up: Swift and MetalKit idiom (interop code is wordier, with pinning and `ULong`
counts), Xcode's build-time shader compilation, and contributors who know only Swift.

## 2. What is shared, and what is Metal

| Shared (`:render:api` and friends, every target) | Metal-only (`:render:metal`) |
|---|---|
| `SkyRenderer`, `LayerScene`, primitives, `RenderState` | `MetalSkyRenderer` — publication, per-layer GPU cache, draw order, uniforms |
| `SkyProjection` / `Matrix4` — the byte-identical view-projection matrix | `MetalPipelines` — the shader library and one pipeline state per program |
| `PointVertices`, `LineStrips`, `GlowMesh` — each primitive type's vertex data, pure and tested | `shaders/*.metal` — Metal Shading Language |
| `StellarRamps`, `GreatCircleSubdivision`, and (for later slices) `SizeFloor`, `LabelDeclutterer`, `LabelFader`, `LabelAtlasPacker` | `MetalInterop` — Kotlin arrays into Metal buffers |
| `TestScene` (`:render:testscene`) — the seeded scene every backend's harness draws | |

The three vertex builders are what "shared" means in practice. The point and glow builders use
the same interleaved layouts as `:render:gles3`'s drawers, but write plain `FloatArray`s rather
than `java.nio` buffers, which iOS lacks. **Follow-up:** GLES3 moves onto them, as its own
change with pixel-identical verification, since it touches Android.

## 3. Shaders

`render/metal/shaders/` holds one `.metal` file per program plus `common.metal`, each
transcribed from its GLSL counterpart in `:render:gles3`. The comments there carry the
reasoning, and the Kotlin in `:render:api` remains the golden reference (`StellarRamps` and the
rest). `:render:metal:embedMetalShaders` concatenates them, `common.metal` first, into one Kotlin
string. `MTLDevice.newLibraryWithSource` compiles it at runtime, as GLES3 compiles its GLSL from
assets. No offline Metal toolchain is needed, and Xcode 26 no longer installs one by default.

Compilation fails loudly and names the program. The `everyPipelineCompiles` test is the gate:
it caught `fragment` being a reserved word in MSL (the sky's dither took a parameter by that
name), which GLSL does not reserve.

Uniform structs contain only `float4x4` and `float4` members, so Kotlin writes them as flat float
arrays with no alignment rules to get wrong.

## 4. Where Metal differs from GLES3, deliberately

- **Lines are mitered triangle strips** (`LineStrips` + `line.metal`), because Metal has no line
  width. Each polyline vertex arrives twice, once per side, carrying its neighbours. The vertex
  shader projects all three and pushes the vertex out along the miter, so consecutive segments
  share their joints and a translucent line blends once everywhere. render-gles3.md §3.1 rules
  out independent per-segment quads for exactly this reason: the grid is 8% alpha. The edge is
  feathered over one pixel where GLES3's `glLineWidth` is aliased. Vertices behind the viewer
  are pulled to the eye plane along their segment, so a line crossing behind you keeps a finite
  on-screen direction. Miters are clamped at 4 half-widths.
- **Clip z is remapped.** `:render:api` builds GL-convention matrices (clip `-w ≤ z ≤ w`); Metal
  clips at `0 ≤ z ≤ w`. Every vertex shader applies `z' = (z + w) / 2`, which makes the two clip
  volumes identical rather than nearly so.
- **One reversed `smoothstep`** in the sky (the below-horizon dim) is rewritten with ordered
  edges. Reversed edges are undefined in both languages, and only GLSL drivers happen to accept
  them.

Deliberately the same: blend modes (alpha and additive, same factors on colour and alpha), the
non-sRGB `bgra8Unorm` framebuffer, the night-mode transform, painter's order with no depth
buffer, and the sky's skip rules (night mode, transparent background).

## 5. Testing

`MetalRenderTest` renders offscreen on the simulator's GPU and samples the frame. Each test pins
one thing the port could get wrong:
- the test scene draws its stars and grid;
- a 4 dp line covers 4 px;
- night mode leaves only red;
- the magnitude limit hides faint stars;
- the sky is blue overhead by day and warm toward a set sun;
- a glow fills exactly the band between its rings.

Every frame is also written to `render/metal/build/reports/metal-renders/*.png` for a person to
look at.

The simulator must be **booted**. Kotlin runs simulator tests with `simctl spawn --standalone`,
which skips the device's services, Metal among them, so `MTLCreateSystemDefaultDevice()`
returns nil. The module's simulator test tasks therefore run non-standalone, and boot their
device first.

## 6. Slices

- **2a (built):** pipelines, stars, lines, glows, sky dome, camera scrim, offscreen tests.
- **2b (built):** `:render:metal-harness` plus `ios/RendererHarness`, the counterpart of
  Android's `RendererTestActivity`. The `MTKView`, draw loop and gestures (drag, pinch, and tap to
  cycle night, day, twilight and night mode) are Kotlin, packaged as the static `SkyMapHarness`
  framework. The app is a SwiftUI shell whose Xcode project XcodeGen generates from
  `project.yml`; a build phase runs Gradle's `embedAndSignAppleFrameworkForXcode`. See
  `ios/README.md`.
- **2c:** images. This is `skyquad` with phase and eclipse shading: an `ImageRef` resolver
  (UIImage) and per-frame `SizeFloor` sizing.
- **2d:** icons and labels. Glyphs are rasterized by CoreText behind a `GlyphRasterizer` seam
  (render-gles3.md §6.5), packed by the shared `LabelAtlasPacker`, and decluttered and faded by
  the shared helpers.
- **2e:** the D19 perf gate (100k points at 30+ fps) on the oldest supported iPhone.

## 7. Noticed during the port

- **Overlapping stars punch holes.** Painter's order with alpha 1 means a faint star drawn after
  a bright one it overlaps covers the bright one's centre, leaving a bright ring around a grey
  core. The random 100k-star test scene shows dozens of these. GLES3 and GLES1 behave
  identically, since the draw order and blend are the same, so this is not a Metal bug. Real
  catalog stars rarely coincide, but when they do, the brighter should win. Candidates for every
  backend: a `max` blend for stellar points, or sorting each layer faint-to-bright at build time.
