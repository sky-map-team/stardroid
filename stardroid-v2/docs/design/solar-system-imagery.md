# Detailed Design: Solar-System Imagery — Quality, True Scale, and Phases

**Status: IMPLEMENTED** for D85–D88 (PRs #104–#107); **PROPOSED** for D89 (Saturn's ring tilt,
Galilean moons on the map). Where the as-built differs from the design as first written, the
deviation is called out inline; D91 (layer-parameter ownership) is implemented too.

Supersedes the deferred sketch in D55 ("Sun/Moon disc sizes stay oversized") and resolves both
of its open sub-decisions. Builds on D18 (distance-ordered solar-system images), D30 (the GLES1
`ImageDrawer` port, which deferred alpha blending), D37 (the `SolarSystemLayer` and its
`BodyImageMapper` seam), D54 (the topocentric Moon), D75 (the widget's procedurally-drawn moon
disc) and D84 (the Meeus ephemeris in J2000) — all in
decisions.md. The surfaces it touches are specified in
[render-api.md](render-api.md), [layers-and-app.md](layers-and-app.md) and
[core-math-astronomy.md](core-math-astronomy.md); the Layers sheet it extends is
[ux-polish.md](ux-polish.md) §1.

## Motivation

The Sun, Moon and planets are the most-looked-at things on the map and currently the
worst-drawn. Three independent defects compound.

**The textures are tiny.** `app/src/main/assets/planets/` holds 17 WebP files inherited
byte-for-byte from v1:

| Files | Dimensions | Bytes |
|---|---|---|
| `mercury`, `pluto` | 16×16 | ~0.7 KB each |
| `sun`, `venus`, `mars`, `jupiter`, `saturn`, `uranus`, `neptune` | 32×32 | 0.7–1.8 KB each |
| `moon0`–`moon7` (the eight phases) | 64×64 | 1.0–3.5 KB each |

68 KB in total. A 32-pixel bitmap is magnified onto a ~2.3° disc with `GL_LINEAR` and no
mipmaps: blurry mush when zoomed in, shimmering when zoomed out.

**The renderer degrades them further.** `ImageDrawer.draw` uses `glAlphaFunc(GL_GREATER, 0.5)`
with no blending, so every disc has a hard, stair-stepped limb — D30 recorded true alpha
blending as deferred. There are no mipmaps anywhere in the GLES1 backend. And there is no
`ImageRef`-keyed texture cache (the TODO at `ImageDrawer.kt:118` and `GLSkyRenderer.kt:289`),
so the solar-system layer re-uploads every planet texture on each ~1-minute resubmission.

**The geometry is fake.** `SolarSystemLayer.angularSizeDeg` returns v1's fixed half-chords —
Sun and Moon at 2.29° (≈4.6× true size), Saturn at 4.01°. Nothing depends on distance, so a
supermoon looks like any other moon and, as D55 documented against the 2026-08-12 eclipse, two
1.15°-radius discs cannot pull apart under a ~1° parallax shift: eclipses never render to
scale. Moon phase is quantised to eight baked-terminator bitmaps, and because `rotationDeg`
rotates the whole bitmap to face the Sun, the maria rotate with the phase — the Man in the Moon
spins through the month.

The intended outcome: bodies that stay sharp from a 90° field of view down to a telescopic one;
a continuously correct lunar terminator, with Mercury and Venus phases falling out for free; and
discs that reach their true angular size — so eclipses, occultations and a supermoon are
geometrically honest — without any body ever becoming invisible or untappable.

## Decisions this document records

- **Art**: re-derived NASA/public-domain discs bundled in the APK. No dependence on the
  data-pack machinery, which is itself only proposed (D79).
- **True-scale scope**: **all bodies**, not Sun/Moon only — resolving D55's first open
  sub-decision — subject to two hard constraints: a body must always stay findable (a visible
  speck is fine, disappearing is not), and its image must not visibly degrade when zoomed in.
- **The size floor is automatic, not a setting.** It tracks zoom continuously and needs no UI.
  What is exposed is a **parameter on the Solar System layer**, for people who want strict true
  scale at every zoom — an inline expander in the Layers sheet, not a settings toggle and not a
  duplicate layer row (§3.4).
- **Saturn ships with rings immediately**, baked into the disc art at a representative opening
  angle. An unringed Saturn is unrecognisable. Only the opening angle is frozen; the orientation
  is already correct (§1).
- **Galilean moons render only when Jupiter is already at true scale**, which removes the
  hardest part of the problem entirely (§7.2).
- **Saturn's ring tilt and Jupiter's moons reach the info cards before the map** — the astronomy
  earns its keep even if the map rendering never lands.
- **GLES3 lands after this work** (confirmed 2026-08-13). So the GLES1 CPU compositor is what
  gets built — but every API addition is still chosen so the GLES3 version is a backend-local
  swap when it arrives (§4.3).

## Staging

| | Contents | Why here |
|---|---|---|
| **Release 1** | §1 assets · §2 renderer quality fixes · §3 true angular size, floor and layer parameter · §4 procedural lunar terminator · §5 Mercury/Venus phases | Phases must ship with the new art, or deleting the eight baked bitmaps is a regression. True size is small once §1 and §2 exist. |
| **Release 2** | §6.1 Saturn ring geometry and §7.1 Galilean moon positions in `:core:astronomy`, surfaced on the **info cards** | Pure astronomy plus Compose rows: cheap, self-contained, and it proves the maths before any rendering depends on it. |
| **Deferred (may never ship)** | §6.2 real ring *tilt* on the map · §7.2 Galilean moons on the map | Asset and rendering problems, both gated behind release 1's true-scale work, neither coupling back into it. |

The deferral is clean: releases 2 and 3 add only new functions to `:core:astronomy` and new
assets. No release-1 API is provisional.

---

## 1. Asset upgrade — one fully-lit disc per body

Replace `assets/planets/` with power-of-two WebP discs, **all rendered fully lit**. Phase becomes
procedural (§4), which collapses `moon0`–`moon7` into a single `moon.webp` and gives Mercury and
Venus phases for free.

| Asset | Size | Source |
|---|---|---|
| `moon.webp` | 1024² | The full-resolution LROC/LOLA public-domain mosaic. The bundled `celestial_images/planets/moon_lroc.webp` is an 800×800 crop of it and is the fallback if re-deriving proves awkward. |
| `sun.webp` | 512² | SDO/HMI continuum, public domain |
| `jupiter`, `mars`, `venus`, `mercury`, `uranus`, `neptune` | 256² | The bundled `celestial_images/planets/*.webp` where the photo is already a clean full disc (`hubble_jupiter`, `hubble_mars`, `nasa_venus`, `nasa_mercury`, `hubble_uranus`, `hubble_neptune`); otherwise the NASA/PDS originals |
| `saturn.webp` | 256² | Disc **with rings baked in** at a representative opening angle — see below |
| `saturn_disc.webp` | 256² | The bare disc, authored alongside it, so §6.2 is an asset swap rather than a re-derive |
| `pluto.webp` | 128² | `nh_pluto_in_false_color.webp` |

> **As built** (see `assets/planets/SOURCES.md`, which is authoritative): Jupiter and Saturn are
> **512²**, sized to what their sources hold and to the fact that they are the two largest
> planets drawn — Saturn's texture spans the ring system, so at 256² its globe had only 113
> texels. `saturn_disc.webp` was **not** produced: every public-domain Saturn photograph has
> rings, so a bare disc could only be made by painting them out and inventing the terrain
> underneath, and §6.2 wants a re-render from a cylindrical map anyway. Mercury comes from the
> PIA12397 global mosaic rather than `nasa_mercury.webp`, which has a baked terminator.

Rules the art must follow:

- **Power-of-two and square.** GLES1 mipmap generation requires it, and it costs nothing under
  GLES3.
- **Straight (non-premultiplied) alpha with a feathered limb**, 2–3 px. Today's art has a
  hard-cut edge because the renderer alpha-tests it; once §2 lands, the feather is what produces
  a clean disc at any drawn size.
- **Centred, with the body's IAU north pole up** in texture space. §4 redefines `rotationDeg` in
  terms of that pole, so the art has to honour it.
- **No baked terminator.**

### Saturn keeps its rings from day one

Nobody identifies Saturn without rings, so `saturn.webp` ships with them baked in at a
representative opening angle — about 20° open, northern face: the iconic view, and close to
Saturn's average tilt.

What is frozen is only the *opening angle*. The ring **orientation stays correct**, because the
ring plane is Saturn's equator and §4 already rotates the texture by the body's north-pole
position angle. So release 1 shows rings that tilt correctly with the sky, and only their
openness is approximate — an error nobody but a planetary observer will notice, and §6.2 removes
it. Author `saturn_disc.webp` at the same time, and size Saturn's `minScreenFraction` (§3.3)
against the **ring system** rather than the disc from the start, so nothing visibly changes when
§6.2 lands.

### Budget and licensing

Roughly **400 KB**, replacing 68 KB — a net APK increase of ~330 KB against an assets tree that
already carries 10 MB of `celestial_images`. That is the ceiling; if measurement exceeds it,
drop the Moon to 512².

These are NASA/ESA public-domain scientific imagery, so they stay in
`app/src/main/assets/planets/` — **not** the All-Rights-Reserved `assets/branding/` tree. This
is the same rule D62 applied to the v1-derived catalog icons. Record per-file provenance in a new
`assets/planets/SOURCES.md`.

## 2. Renderer quality fixes

Four changes in `render/gles1/`, all independent of the rest of this design, all worth doing
first because they improve today's art immediately, and all carrying over to a GLES3 backend
unchanged in intent.

- **Alpha blending replaces the alpha test.** `ImageDrawer.draw` swaps `GL_ALPHA_TEST` for
  `glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)`. Correct back-to-front ordering is already
  guaranteed inside the layer by D18's descending-Earth-distance sort in
  `SolarSystemLayer.buildScene`. This closes D30's deferred item and is the biggest visual gain
  per line changed.
- **Mipmaps.** Set `GL_GENERATE_MIPMAP` before `GLUtils.texImage2D`, and use
  `GL_LINEAR_MIPMAP_LINEAR` for the min filter in `Textures.setLinearClampParams`. Without it a
  1024² Moon drawn at 12 px aliases badly. Keep `GL_LINEAR` magnification and `GL_CLAMP_TO_EDGE`.
- **An `ImageRef`-keyed texture cache**, implementing the existing TODOs. Mandatory once textures
  are ~100× larger: the layer resubmits about once a minute and would otherwise re-upload several
  MB per minute. An LRU keyed on `ImageRef` with a stated byte budget, cleared on GL context loss
  alongside the existing cache reset.
- **Lazy night-mode textures.** `Textures.uploadRedTexture` runs eagerly for every image today,
  doubling texture memory for users who never enter night mode. Build it on first night-mode use,
  keyed in the same cache.

## 3. True angular size with a minimum-size floor

### 3.1 New astronomy API

`:core:astronomy` has no notion of physical size today.

```kotlin
/** True apparent equatorial diameter of [body] as seen from Earth, in degrees. */
fun angularDiameterDeg(body: SolarSystemBody, time: Instant): Double
```

Default implementation: `2 * atan(equatorialRadiusKm / distanceKm)` from a table of IAU mean
equatorial radii and the existing `earthDistanceAu`. `MeeusEphemeris` already returns a true
geocentric lunar distance, which is what makes a supermoon read correctly. `earthDistanceAu`
currently promises only correct *ordering* (D18), so its contract tightens to "good to ~1%";
verify the Keplerian series meets that and golden-test against JPL Horizons.

### 3.2 Render API change

```kotlin
data class ImagePrimitive(
    val center: Vector3,
    val angularSizeDeg: Double,             // now means TRUE apparent diameter
    val rotationDeg: Double,                // §4: position angle of the body's north pole
    val image: ImageRef,
    val minScreenFraction: Double = 0.0,    // floor, as a fraction of the short viewport side
    val minSizeDp: Double = 0.0,            // absolute floor — never let it vanish
    val terminator: Terminator? = null,     // §4
    val visibleBelowFovDeg: Double? = null, // §7.2 — drawn only when zoomed in past this
)
```

### 3.3 Draw-time sizing

The floor is applied in `ImageDrawer.quadCorners`, which is where pixels and field of view are
known — deliberately *not* in the layer, whose `angularSizeDeg` is cadence-throttled to about
1 Hz and cannot track a continuous pinch:

```
floorDeg = max(minScreenFraction * fovDeg, minSizeDp * density * fovDeg / shortSidePx)
drawnDeg = max(angularSizeDeg, floorDeg)
scale    = sin(drawnDeg / 2)
```

`quadCorners` therefore needs `fovDeg`, `shortSidePx` and `density`, all already available to the
backend via `SkyCamera.fovDeg` (which spans the short side, D21/D82) and `Viewport`.

Why this meets both constraints:

- **Never disappears.** Both floors are proportional to `fovDeg`, so the floor shrinks exactly as
  fast as the user zooms in and can never push a body below `minSizeDp`. Set `minSizeDp = 3.0` —
  the "at least a pixel" guarantee, but at 3 dp it is genuinely visible and tappable rather than
  one physical pixel on a 3× screen.
- **Continuous.** At the crossover the floor equals the true size, so there is no pop; the disc
  hands off to true scale while already large on screen. This is D55's stated reason for
  rejecting a hard zoom threshold, which would make the Moon *shrink* as you zoomed in.
- **No regression at default zoom.** Calibrate each body's `minScreenFraction` as
  `glyphSizeDeg / CALIBRATION_FOV_DEG`. By construction the map is pixel-identical to the glyph
  ladder at the field of view the map opens at; everything below it is a reward for zooming in.

Crossover is therefore `CALIBRATION_FOV_DEG × trueDeg / glyphDeg`, and as built
`CALIBRATION_FOV_DEG` is **21.1°** — the FOV the map opens at (`MapViewModel.INITIAL_FOV_DEG`,
which `SolarSystemLayerTest` pins). Saturn's ring factor cancels, since both its glyph and its
`angularSizeDeg` span the rings.

> **This table was wrong in the design as first written.** It assumed both a ~70° calibration FOV
> and v1's fixed glyph sizes. The map opens at 21.1°, and D87's glyphs are now sized by physical
> diameter (§3.4), which is a different and much larger ladder — Saturn spans 4.42° where v1 drew
> it at a fraction of that. Both changes push every crossover down. Corrected values, at the
> body's largest and smallest apparent diameter:

| Body | Glyph | True diameter | Floor wins until FOV ≈ |
|---|---|---|---|
| Moon | 4.06° | 29.3′–33.5′ | 2.5°–2.9° |
| Sun | 4.06° | 31.5′–32.5′ | 2.7°–2.8° |
| Jupiter | 2.05° | 29.8″–50.1″ | 0.09°–0.14° |
| Venus | 0.98° | 9.7″–66.0″ | 0.06°–0.40° |
| Mars | 0.82° | 3.5″–25.1″ | 0.03°–0.18° |
| Mercury | 0.74° | 4.5″–13.0″ | 0.04°–0.10° |
| Saturn (globe) | 1.95° | 14.5″–20.1″ | 0.04°–0.06° |
| Uranus | 1.51° | 3.3″–4.1″ | 0.013°–0.016° |
| Neptune | 1.49° | 2.2″–2.4″ | 0.009° |
| Pluto | 0.60° | 0.06″–0.11″ | 0.001° |

**Three bodies never reach true scale in "Auto".** Uranus, Neptune and Pluto cross below the
0.03° zoom stop, so the floor governs them everywhere in the zoom range. That is a consequence of
ranking glyphs by physical size: Uranus gets a 1.51° glyph for being large, while being far
enough away to appear 4″ across. It is acceptable — those three are featureless at any zoom this
app reaches, and "True scale" mode shows them honestly for anyone who wants it — but it is a real
limit of "Auto", not an approximation.

**`MapViewModel.MIN_FOV_DEG` drops from 0.5° to 0.03°** (the design first proposed 0.1°). At 0.5°
most planets sit above their crossover and stall oversized, so "true size for occultations" would
never quite arrive. At 0.03° Jupiter's disc reaches ~46% of the screen, Saturn's rings resolve,
and the Galilean moons sit at plausible separations — this is what makes the 512-pixel art worth
its bytes. Two follow-ups: confirm `IdentifyGeometry.MIN_TAP_THRESHOLD_DEGREES` still yields a
usable tap target at 0.03°, and that the pinch ramp reaches the new limit in a sane number of
gestures.

**Label clearance must follow the drawn size.** `SolarSystemLayer` sets
`clearanceDeg = angularSizeDeg * 0.6` so a name clears the disc. Once the drawn size is known
only at draw time, this moves to the backend: `LabelStyle.clearanceDeg` becomes a fraction of the
associated image's drawn diameter, or the label drawer reads the floored size from the same
computation. Otherwise names sit inside a true-scale Moon.

**Sun corona.** A true-scale Sun with a hard limb reads as a flat sticker. Give it an additive
corona/glare halo through the existing `GlowPrimitive` mesh (added for the horizon glow, D40) at
about 2.5× the drawn disc. It also keeps the Sun findable when it is a floored speck.

### 3.4 The layer parameter

The floor is automatic and has no UI. The **Solar System layer gains a parameter** for users who
want strict true scale at every zoom, shown as an **inline expander in the Layers sheet**. This
establishes a general "layers can carry parameters" mechanism — grid density, magnitude limit and
shower selection are the obvious later customers.

```
Layers                                  ✕
── OBJECTS ─────────────────────────────
  Stars                          [●──]
  Constellations                 [●──]
  Deep sky                       [●──]
  Solar system            ⌄      [●──]
  │
  │  Disc size
  │  ╭────────┬────────────┬──────────╮
  │  │ ✓ Auto │ True scale │ Glyphs   │
  │  ╰────────┴────────────┴──────────╯
  │  Legible when zoomed out, true
  │  size when you zoom in.
  │
  Meteor showers                 [──○]
── REFERENCE ───────────────────────────
  Grid                           [──○]
```

- **Auto** (default) — the §3.3 floor: today's appearance at default zoom, true scale when
  zoomed in.
- **True scale** — floors reduced to `minSizeDp` alone, so discs are true size at every zoom.
  Planets become specks at ordinary zoom but stay visible and tappable at 3 dp.
- **Glyphs** — today's fixed inflated sizes, no true-scale handoff. An escape valve for anyone
  who dislikes the change, and it subsumes v1's `show_planetary_images` intent.

Mechanics: the expander is collapsed by default and rendered only for layers that declare
parameters. `SkyLayer` gains an optional `parameters: List<LayerParameter>` — an enum choice is
the only kind needed now. `LayersViewModel` persists the selection through the existing DataStore
path alongside the visibility toggles, and it reaches the renderer as part of `RenderState`, so
it re-applies without layer resubmission exactly as `magnitudeLimit` does. The rail icon is
unchanged: the rail stays a shortcut surface and the sheet stays canonical (D56).

Rejected alternatives: a Settings entry (this is layer configuration, and the Layers sheet is
already the canonical place for it, D56); and a second "Solar System (true scale)" layer row
(it doubles the rail budget and models a display option as a layer).

## 4. Accurate Moon phase — a procedural terminator

The eight baked bitmaps quantise illumination to eight buckets *and* couple the terminator to the
surface features. Split them.

### 4.1 The API

```kotlin
/** Pure geometry: how much of a lit sphere faces us, and which way the lit limb points. */
data class Terminator(
    val illuminatedFraction: Double,   // 0..1
    val brightLimbAngleDeg: Double,    // position angle of the lit limb, in the image frame
)
```

- **`rotationDeg` is redefined** as the position angle of the body's north pole, so the maria stay
  fixed against the celestial sphere as the phase changes. This fixes the spinning-Man-in-the-Moon
  bug: `SolarSystemLayer.moonRotationDeg` rotates the whole bitmap anti-solar today, which was
  only ever correct because the terminator was baked in.
- **`brightLimbAngleDeg`** is Meeus ch. 48's position angle χ of the bright limb, derivable from
  Sun and Moon RA/Dec — essentially the angle `moonRotationDeg` already computes from the
  anti-solar tangential direction. Move it into `:core:astronomy` as a pure, testable
  `brightLimbAngleDeg(body, time, observer)`.
- `PlanetImages.MOON_IMAGES` collapses to a single `ImageRef("planet/moon")`. `lunarPhaseBucket`
  stays — the widget and any phase-naming UI still want a named bucket.

### 4.2 The New Moon must never be a black hole

A fully-shadowed disc painted black is invisible against a black sky, and users read that as the
Moon having vanished. So the compositor:

- **Floors the dark-side luminance.** The shadowed hemisphere renders as a dark grey sphere with
  a shallow limb-darkening falloff, never below a stated minimum alpha, so it always reads as an
  object.
- **Adds earthshine**, which is physically real and peaks exactly where it is needed — the ashen
  light on the dark limb is brightest at thin crescent and New. Scale it with
  `(1 − illuminatedFraction)` so it is invisible at Full and strongest at New.
- **Keeps a faint limb ring**: a thin, slightly brighter rim around the full circumference at
  every phase, so the disc's extent is always locatable.

The label and the existing tap target are unaffected, so a New Moon stays findable and tappable
regardless. (Physically a true New Moon is within ~5° of the Sun and unobservable — but the app
draws it, and users go looking, so it has to read as *something*.)

### 4.3 Rendering — CPU now, shader after the GLES3 upgrade

`render/gles1` is fixed-function GL10; there are no shaders. So under GLES1 the terminator is
composited **CPU-side into the cached bitmap** at texture-build time: darken the un-lit region,
feather the terminator by about 1% of the radius (the real one is soft), and apply §4.2's
dark-side floor, earthshine and limb ring. The cache key is `ImageRef` plus the illuminated
fraction quantised to 0.5% steps — roughly 200 buckets, imperceptible, and the fraction changes
slowly enough that a recomposite happens at most once per layer resubmission (~1/min). One
bitmap composite and upload per minute is negligible.

Reuse the geometry that already exists: `widget/MoonDiscRenderer` draws the correct terminator
for the moon widget — a half-circle closed by an ellipse of horizontal semi-axis `r·|2f−1|`
(D75). Lift that path construction into a shared `PhaseCompositor` and drive it from the texture
rather than flat fills. The widget's `mirrored`/`waxing` booleans are subsumed by
`brightLimbAngleDeg`, which handles every orientation continuously.

Night mode applies the red-luminance transform to composited bitmaps through the same §2 cache,
so it falls out for free.

**The GLES3 seam.** `Terminator` is backend-agnostic data. A GLES3 backend evaluates it per-pixel
in a fragment shader instead, dropping the compositor, the quantisation, the bitmap cache and the
re-upload path entirely, and gaining an exactly continuous terminator. Nothing outside
`render/gles3` changes. This is the reason for a typed field rather than encoding phase into the
`ImageRef` string. **Confirmed 2026-08-13: GLES3 lands after this work, so build the CPU
compositor** — but keep it strictly inside the backend, so retiring it later is a deletion rather
than a refactor.

**Libration** — the Moon's ±8° wobble, which really does change which features face us — is not
specified here. It is the natural next accuracy step, and needs an optical-libration term plus a
slightly oversized albedo texture.

## 5. Mercury and Venus phases

Free once §4 exists: the same `Terminator`, with `illuminatedFraction` from the existing
`Ephemeris.illuminatedFraction` and the same `brightLimbAngleDeg`. Venus is the payoff — it
swings from a 10″ full disc to a 66″ crescent, unmistakable at a 0.1° field of view. Apply the
terminator to every body: for the outer planets it is a no-op above ~99% illumination, and Mars
picks up its gibbous phase (minimum ~84%) for nothing.

## 6. Saturn's rings

### 6.1 Astronomy and info card — release 2

Implement Meeus ch. 45 in `:core:astronomy`; the low-accuracy form is ample.

```kotlin
fun saturnRingGeometry(time: Instant): SaturnRings  // openingAngleDeg (B), positionAngleDeg
```

Surface it as a row on Saturn's info card — "Rings: 14° open (northern face)", or "Rings:
edge-on" near a plane crossing — alongside the existing rise/set rows (D51). Cheap, and it
delivers the interesting fact with no rendering risk.

### 6.2 Correct ring tilt on the map — deferred

Release 1 already draws rings at a fixed opening angle with correct orientation (§1). What is
deferred is making the *opening angle* track reality, so the rings visibly close toward edge-on
and reopen across the 15-year cycle.

**Programmatic compositing is rejected.** The vertical squash by `sin|B|` is geometrically exact
— the rings really are a flat plane — but the parts that sell the image are the planet's shadow
on the rings, the rings' shadow on the planet, C-ring translucency where the disc shows through,
and the front/back split at the limb. Composited from a flat annulus those look synthetic.

Instead, **author the tilt variants offline** in a real 3D renderer over `saturn_disc.webp` and
Cassini ring photometry, sampled every ~3° of B from −27° to +27°: 19 textures at 256², about
230 KB. B moves so slowly that a user sees one or two variants in a year, so the texture cache
holds exactly one. The runtime does nothing but pick the nearest variant — a pure asset problem
with no quality risk, and `saturn.webp` simply becomes the B ≈ 20° member of the set.

## 7. Jupiter's Galilean moons

### 7.1 Astronomy and info card — release 2

Meeus ch. 44's low-accuracy theory gives each moon's rectangular offset (X east, Y north) in
Jupiter radii. Pure, and golden-testable against JPL Horizons.

```kotlin
fun galileanMoons(time: Instant): List<SatelliteOffset>  // name, xRadii, yRadii, behindPlanet
```

Surface it on Jupiter's info card as a "Moons tonight" strip: a wide, short diagram with
Jupiter's disc centred and the four moons at their current offsets, labelled. It is the classic
small-telescope view and needs no renderer work at all.

### 7.2 Map rendering — deferred

**Gated on Jupiter being drawn at true scale.** This is what removes the hard part. Because the
moons appear only once the floor no longer inflates Jupiter, their offsets are simply true
angular offsets — no satellite primitive, no offsets expressed in drawn-body radii, no coupling
to the floor at all. Each moon is an ordinary small primitive with `visibleBelowFovDeg` set to
Jupiter's crossover field of view (~0.14° at opposition, §3.3), which is the one general field
§3.2 already
introduces.

Consequences worth stating: the moons appear together as you pass the crossover, which reads as a
deliberate reveal rather than a glitch; they are unavailable in "Glyphs" mode and always
available in "True scale" mode, both correct by construction. Labels default off — four labels
around Jupiter would swamp the view — surfacing only well below the crossover.

## 8. Adjacent opportunities

Not part of the staged work, but recorded because this design makes each of them cheap:

- **Lunar eclipse colouring.** With true-scale discs and D54's topocentric Moon, the Moon's
  position relative to Earth's umbra is computable. Tinting it copper inside the umbra is one
  more `PhaseCompositor` parameter (or shader uniform), and it is exactly what people open the
  app for.
- **Solar eclipses start working.** True-scale Sun and Moon plus D18's distance ordering means
  the Moon correctly occludes the Sun, and D54's parallax correctly pulls the discs apart off the
  path of totality — the concrete failure D55 was written about. This becomes an acceptance test,
  not a feature request.
- **Tap targets must not follow the drawn size.** `IdentifyGeometry` uses an FOV-scaled threshold
  independent of image size, which is what we want; verify a true-scale Neptune at 2″ is still
  tappable, and consider floor-aware hit-testing so tapping a floored glyph hits its visible
  extent.
- **`BodyImageMapper`'s dot fallback** is largely redundant once discs are floored and
  true-scale; the "Glyphs" parameter supersedes its intent. Decide whether it stays.
- **`AssetImageLoader` needs a generated-bitmap path.** It resolves `planet/*` to a fixed `.webp`
  filename today. The compositor sits in front of it: the loader fetches the base disc, the
  compositor applies the terminator, the §2 cache stores the result. Under GLES3 the compositor
  drops out and the loader is untouched.

## Files this design touches

| Area | Files |
|---|---|
| Astronomy | `core/astronomy/…/Ephemeris.kt` (`angularDiameterDeg`, `brightLimbAngleDeg`), `MeeusEphemeris.kt`, `KeplerianEphemeris.kt`; new `SaturnRings.kt`, `GalileanMoons.kt` (release 2) |
| Render API | `render/api/…/Primitives.kt` — `ImagePrimitive` floors, `Terminator`, `visibleBelowFovDeg`; `LabelStyle.clearanceDeg` semantics (the disc-size parameter does *not* ride on `RenderState` — see D91) |
| GLES1 backend | `ImageDrawer.kt` (blending, floored `quadCorners`), `Textures.kt` (mipmaps), `GLSkyRenderer.kt` (texture cache) |
| App | `layers/SolarSystemLayer.kt`, `layers/PlanetImages.kt`, `layers/SkyLayer.kt` + `LayerRegistry` (layer parameters), the Layers sheet UI, `render/AssetImageLoader.kt`, new `render/PhaseCompositor.kt` (generalising `widget/MoonDiscRenderer.kt`), `ui/map/MapViewModel.kt` (`MIN_FOV_DEG`), `ui/objectinfo/` (release-2 card rows) |
| Assets | `app/src/main/assets/planets/` replaced; new `SOURCES.md` |

## Acceptance criteria

1. `./gradlew check` from `stardroid-v2/` — ktlint, the Konsist architecture gate (new astronomy
   code must stay Android-free), JUnit 5 + Truth.
2. **Pure unit tests**: `angularDiameterDeg` and `brightLimbAngleDeg` golden-tested against JPL
   Horizons; the size-floor formula tested for continuity at the crossover and for the
   never-below-`minSizeDp` guarantee across the full 0.1°–90° sweep; terminator lune area against
   `illuminatedFraction`; the New Moon dark-side floor asserted non-zero; Saturn's B and the
   Galilean offsets against Meeus's worked examples (release 2).
3. **Renderer tests**: extend `ImageDrawerTest` for floored `quadCorners` at representative fields
   of view and for `visibleBelowFovDeg` gating.
4. **Instrumented**: `./gradlew connectedDebugAndroidTest`, with the D19 perf gate extended by a
   solar-system pinch from 90° to 0.1°.
5. **On-device visual check** (emulator, v1 uninstalled first, `pm clear` to avoid stale assets):
   - Time-travel to 2026-08-12 on the eclipse path (Lerma, Spain) — totality; then Miami — a
     partial eclipse with the discs genuinely offset. This is D55's original failure case.
   - Zoom the Moon from 90° to 0.1°: it sharpens rather than blurring, with no pop at the
     crossover.
   - Step the clock through a full lunation: the terminator sweeps continuously, the maria stay
     put, and the New Moon is still a visible dark sphere with earthshine — not a hole.
   - Saturn is instantly recognisable at default zoom, and its rings roll with the sky rather
     than staying screen-aligned.
   - Toggle the layer parameter through Auto / True scale / Glyphs at several zoom levels.
