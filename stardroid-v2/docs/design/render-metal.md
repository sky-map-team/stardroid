# Detailed Design: `:render:metal` — the Metal backend (iOS)

**Status: IN PROGRESS** — slices 2a–2d built: everything `:render:gles3` draws (stars, lines,
the sky dome and the ground below the horizon, camera scrim, images with phase and eclipse, icons, labels), offscreen render
tests, and an iOS harness app. The on-device perf gate remains (§6). D128.

`:render:metal` is the iOS `SkyRenderer`: an iOS-only Kotlin Multiplatform module that calls
Metal directly through Kotlin/Native. It is a sibling of `:render:gles1` and `:render:gles3`
behind the same contract, with the same thread model and the same draw order, and it draws what
`:render:gles3` draws.

## 1. Why Kotlin/Native and not Swift (D128)

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
| `PointVertices`, `LineStrips` — each primitive type's vertex data, pure and tested; `GroundRamp`, `MoonShading` — the golden references the sky, ground and image shaders transcribe | `shaders/*.metal` — Metal Shading Language |
| `ImageQuad` — an image's per-frame drawn size (FOV cull + `SizeFloor`), quad half-axes, and texture-space lit-limb and shadow vectors | `MetalTextures` — images decoded into staging buffers, copied into textures ahead of the frame's render pass |
| `ImageCache` — reference-counted, byte-budgeted texture bookkeeping with the GPU calls passed in | `MetalInterop` — Kotlin arrays into Metal buffers |
| `LabelAtlas` — measure, pack and rasterize a layer's labels into coverage pages; `LabelFrame` — per frame: cull, project, offset, declutter, fade, emit glyph and underline quads | `UIKitGlyphRasterizer` — the one label step that is platform code: UIKit string drawing into an alpha-only bitmap |
| `IconSprites` — icon quads per frame, one run per image; `SpriteInstances` — the instance buffer, in runs; `ScreenSpace` — frustum and pixels-per-degree | `sprite.metal` — icons, glyphs with their halo, and underlines in one instanced shader |
| `GlyphRasterizer` — the seam every backend implements; `StellarRamps`, `GreatCircleSubdivision`, `SizeFloor`, `LabelDeclutterer`, `LabelFader`, `LabelAtlasPacker` | |
| `TestScene` (`:render:testscene`) — the seeded scene every backend's harness draws | |

The vertex builders, `ImageQuad`, `ImageCache` and the label and icon pipeline are what
"shared" means in practice: of labels, only turning text into pixels is platform code. The point builder uses
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

Deliberately the same: images as premultiplied RGBA with linear filtering, clamped, and no
mipmaps (as GLES3 uploads Android bitmaps, so disc edges blend identically), blend modes (alpha and additive, same factors on colour and alpha), the
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
- the sky ends at the horizon, and the ground matches `GroundRamp` pixel for pixel, washing over
  shallow layers while the horizon layer at `LayerScene.GROUND_DEPTH` draws on top of it;
- every tuning constant in the Metal source matches its Kotlin original
  (`MetalShaderConstantParityTest`, the counterpart of GLES3's `ShaderConstantParityTest`).

Every frame is also written to `render/metal/build/reports/metal-renders/*.png` for a person to
look at.

The simulator must be **booted**. Kotlin runs simulator tests with `simctl spawn --standalone`,
which skips the device's services, Metal among them, so `MTLCreateSystemDefaultDevice()`
returns nil. The module's simulator test tasks therefore run non-standalone, and boot their
device first.

## 6. Slices

- **2a (built):** pipelines, stars, lines, sky dome, camera scrim, offscreen tests. (It also drew
  the horizon glow, until the GLES3 branch replaced that with the ground hemisphere; the Metal
  backend followed, with a `ground.metal` transcribed from `ground.frag`.)
- **2b (built):** `:render:metal-harness` plus `ios/RendererHarness`, the counterpart of
  Android's `RendererTestActivity`. The `MTKView`, draw loop and gestures (drag, pinch, and tap to
  cycle night, day, twilight and night mode) are Kotlin, packaged as the static `SkyMapHarness`
  framework. The app is a SwiftUI shell whose Xcode project XcodeGen generates from
  `project.yml`; a build phase runs Gradle's `embedAndSignAppleFrameworkForXcode`. See
  `ios/README.md`.
- **2c (built):** images, from GLES3's `skyquad` with phase and eclipse shading per pixel. The
  renderer takes an `ImageRef → UIImage` loader. Textures upload through a staging buffer whose
  copy is encoded on the frame's own command buffer, before the render pass. The harness now
  draws the test scene's planet. Tests check drawn size, the size floor, the FOV cull, a missing
  image, the half phase, and the reddened umbra.
- **2d (built):** icons and labels. `GlyphRasterizer` (render-gles3.md §6.5) is the only
  platform step; UIKit's string drawing implements it in the system sans-serif face. So iOS
  labels are San Francisco where Android's are its own sans-serif, which is intended: each
  platform's native face. Atlas building, per-frame layout, decluttering, fading, the halo and
  the info-card underline are shared or transcribed. One instanced draw per atlas page or icon
  image. The renderer takes `labelFadeMillis` (0 in tests, so one frame shows the declutter
  decision) and an `onAnimating` callback for hosts that render on demand. Tests check the
  rasterizer's cell clipping, the label's hang below its anchor, the underline, font scaling,
  night mode, the halo against a bright sky, and icon size, tint and missing images.
- **2e:** the D19 perf gate (100k points at 30+ fps) on the oldest supported iPhone.

## 7. Noticed during the port

- **Removing from a map while holding its entries is JVM-only.** `ImageCache`'s eviction first
  kept the map's own entry objects, then removed from the map while reading them. Kotlin/Native
  throws `ConcurrentModificationException` for that; the JVM tolerates it. The iOS test run
  caught it, and the shared version snapshots key/value pairs. `:render:gles3`'s `TextureCache`
  has the same pattern, harmless only because it runs on the JVM, and it goes away when GLES3
  adopts `ImageCache`.

- **Overlapping stars punch holes.** Painter's order with alpha 1 means a faint star drawn after
  a bright one it overlaps covers the bright one's centre, leaving a bright ring around a grey
  core. The random 100k-star test scene shows dozens of these. GLES3 and GLES1 behave
  identically, since the draw order and blend are the same, so this is not a Metal bug. Real
  catalog stars rarely coincide, but when they do, the brighter should win. Candidates for every
  backend: a `max` blend for stellar points, or sorting each layer faint-to-bright at build time.
