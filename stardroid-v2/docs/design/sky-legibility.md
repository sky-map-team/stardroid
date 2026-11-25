# Design: Sky legibility — a map that matches your sky

**Status: PROPOSED — UX design only, no code.** Scheduled after the AR-mode launch. Companion
mockups: [`mockups/sky-legibility.html`](mockups/sky-legibility.html). Decisions are numbered
L1–L14 here and get real D-numbers when accepted.

**Ground rules for everything below:**

1. **It is a map, not a photograph.** Every visual choice borrows from printed star atlases
   (graded discs, dashed boundaries, isophote Milky Way, line-art figures), never from
   astrophotography (glare, bloom, diffraction spikes, photographic textures).
2. **Casual users get one control and good defaults.** Anything else sits one level down,
   under "Customize", for power users.
3. **Every distinction must survive night mode.** Night mode collapses colour to red luminance
   (`applyNightMode` in `common.glsl`), so hierarchy is carried by *size, luminance and dash
   pattern*, never by hue alone.
4. **GLES1 gets nothing new.** It is deprecated. New appearance work targets `:render:gles3` and
   `:render:metal`. Kotlin in `:render:api` stays the golden reference for both.

---

## 0. What the code tells us today

These facts shape the recommendations, so they come first.

| Fact | Where | Consequence |
|---|---|---|
| The star catalogue has **5,998 stars down to mag 6.16**: 1 brighter than mag 0, 15 at mag 0, 33 at mag 1, 123 at mag 2, 344 at mag 3, 1,087 at mag 4, **3,404 at mag 5** and 991 at mag 6. | `source-data/stars.csv` | **74% of the stars on screen are mag 5 or fainter**, so most city users can't see them. It also means a "binoculars" preset has nothing to add until the catalogue gets deeper (§1.6). |
| Stars come in two sizes, 3 dp and 5 dp, split at mag 1.0. Brightness falls only from 1.0 to 0.35. | `StellarRamps.sizeDp`, `magnitudeShade` | A mag-6 star is drawn 60% the size and 35% the brightness of Sirius, which is 1,600× brighter. **Much of the clutter is a drawing problem, not a visibility problem** (§2). |
| `RenderState.magnitudeLimit` exists, and both new backends fade stars past it (`magnitudeAlpha`, a 0.5-mag soft edge). **No producer ever sets it.** | `RenderState.kt`, `point.vert` | Most of the plumbing for a sky-brightness threshold is already built and tested. |
| `StellarRamps.colorForIndex` (B−V → tint) exists, but the catalogue has **no B−V column**. | `StellarRamps.kt`, `stars.csv` | Star colour is a data join, not a rendering project (§3). |
| Constellation figures are polylines of RA/Dec points with no star identities. One colour: `#B27CC9` at 50% alpha. | `constellations/iau.json`, `SkyColors` | To weight lines or tie them to star visibility, each vertex first needs matching to a catalogue star, done once at build time (§4). |
| Labels already fade in and out over time, and are filtered by an FOV-dependent magnitude threshold (mag 4 at 45°, 3 at 90°, 6 at 25°). Named DSOs and planets are exempt. | `LabelFader`, `LabelDeclutterer` | Label decluttering is a good base. The new limit plugs into the existing pre-filter (§8). |
| `dso.csv` carries `size_arcmin`, marked as not yet loaded. There is no Milky Way object. | `source-data/README.md` | Showing extended objects at their true size is a data-wiring job (§6). |
| `ImagePrimitive` is a flat quad. render-gles3 §7.3 already says constellation art needs a **spherical patch** primitive. | `render-gles3.md` | Large images reuse that plan (§7). |

---

## 1. Sky conditions — one threshold, many inputs

### 1.1 The model (L1)

Internally there is **one number: the limiting magnitude** — the faintest star you can see.
Every layer derives its visibility from it. Casual users never see the number.

```
limit(direction) = site            (how dark your location is)
                 − twilight(sun altitude)
                 − moonlight(moon phase, angle to the Moon)
                 − horizon(altitude of this direction)     ← extinction + skyglow
                 + aid                                     (eyes / binoculars)
```

- **Site** is the main input: roughly 3.5 in a city, 4.5 in suburbs, 5.5 in the countryside and
  6.5 at a dark site.
- **Twilight** comes from the Sun's altitude. There is no penalty below −18°. It ramps through
  nautical twilight. In daylight it drops to a floor (§1.4).
- **Moonlight** is a global penalty of up to about −1.5 mag at full Moon, plus up to about −1 mag
  more within ~20° of the Moon.
- **Horizon** lowers the limit by about 1–1.5 mag in the last 15° above the horizon. This is
  true everywhere and worst in cities, and it is what makes low stars thin out naturally.
- Everything except *site* is computed, not chosen.

The direction-dependent terms are cheap on the GPU. Magnitude is already a vertex attribute.
Add Sun, Moon and zenith directions as uniforms (`SkyGradient` already carries Sun and zenith)
and evaluate the limit per vertex. Labels and line segments evaluate the same Kotlin function
on the CPU.

### 1.2 The control (L2)

**One "Sky" row at the top of the Layers sheet**, because it is the most important filter the
app has. It opens a half-height sheet. The map stays visible above it and updates live as you
choose.

```
 How dark is your sky?
 ┌────────┐┌────────┐┌────────┐┌────────┐┌────────┐
 │  Auto  ││  City  ││ Suburb ││Country ││  Dark  │   ← each a tiny Orion thumbnail
 └────────┘└────────┘└────────┘└────────┘└────────┘
 About 240 stars above the horizon right now        ← the honest feedback line
 [ Check my sky ]                                    ← guided calibration, §1.5
 ▸ Customize                                         ← power users, §1.7
```

- **Auto is the default.** It uses the light-pollution lookup (§1.3) for *site*, plus the
  computed terms.
- Picking a preset **animates the limit over ~600 ms** instead of jumping, so the faint stars
  visibly drain away or fill in. That teaches what the control does without any words.
- When Auto changes the picture a lot (twilight, a bright Moon), a short chip appears once on the
  map, e.g. "Bright Moon tonight — faint stars hidden". It links to this sheet.
- The Tonight sheet gains a line: "Your sky tonight: suburban, Moon 82% — about 300 stars".

### 1.3 Auto site from a light-pollution map (L3)

An **offline grid** looked up from the location we already have. No network, so the privacy
policy doesn't change.

- **Source:** VIIRS nighttime-lights annual composites (Earth Observation Group; to my knowledge
  CC BY 4.0 — *verify before use*). Upward radiance is **not** sky brightness, so the build tool
  convolves it with a distance-falloff kernel (Walker's law, ~d⁻²·⁵ out to ~200 km) to get
  zenith skyglow, then quantises to ~8 Bortle-like steps. Falchi et al.'s 2016 World Atlas
  already has this physics, but its licence needs checking (it may be non-commercial, which
  would rule it out).
- **Size:** at 0.05° (~5 km) and 3–4 bits per cell, the grid is 7200×3600. It is mostly ocean
  and dark land, so it compresses to a few MB. At 0.1° it is under 1 MB. The tool lives in
  `tools/`, and only its output ships. If it's too big for the APK it becomes a data pack
  (data-packs.md, D79).
- **It's a default, not a verdict.** If the user picks a preset while on Auto, we remember it
  *for that place* (within ~10 km). Home and the holiday cabin then each keep their own setting,
  and Auto stays useful.

### 1.4 The daytime floor (L4)

Taking twilight literally would leave the map empty at noon. That is honest, but as a map it is
useless ("where is Orion right now?"). **The limit never drops below ~mag 2 for stars**, which
leaves about 50 stars plus the constellation figures. The analytic sky gradient already reads
clearly as day, so the user isn't misled. An opaque or misleading "you can't see these" overlay
is not needed.

### 1.5 "Check my sky" — the calibration that works (L5)

This is the classic naked-eye limiting magnitude test, made tappable. The app picks a well-placed
constellation that is high in the sky now: the Little Dipper in the north, Crux or Orion in the
south. It then shows **four small star patterns of increasing faintness** (around mag 2, 3, 4
and 5) and asks *"Tap the faintest group you can see."* One tap sets the site value, with no
numbers shown. It takes about 15 seconds, and it is far more accurate than any sensor.

### 1.6 Binoculars (L6)

A "Looking with: Eyes / Binoculars" switch adds about +3 mag. **It's only worth showing once the
catalogue goes past mag 6.2.** The candidates:

- a Hipparcos-derived subset to about mag 8.5 (~40k stars, ~0.5 MB, also gives B−V for every
  star);
- Tycho-2 as a data pack.

The D19 perf gate already runs 100k points. Until then, hide the switch: a toggle that changes
nothing reads as broken (the same rule `LayerRegistry` applies to satellites).

### 1.7 Customize (power users)

- A **"Faintest star" slider** (mag 1–6.5, or deeper with a deep pack) with a graded-disc legend
  under it. This is the only place the magnitude number appears.
- **Dim toward the horizon** (default on) and **Moon washes out stars** (default on).
- **Reveal fainter stars when zoomed in** (default **off**, see L7).
- **Show hidden objects as faint outlines** (default off): stars below the limit drawn as hollow
  rings at 30% alpha, like a chart's "fainter than" key.

### 1.8 Zoom and the threshold (L7)

**My recommendation: zoom does not change the star limit by default.** The promise of the
preset is "the map shows what you can see", and zooming into Orion doesn't let your eyes see
fainter stars. Zoom keeps doing what it already does well: revealing **labels**, **DSO
markers**, and (new) the **extended constellation lines** (§4). Clutter at a wide field of view
is mostly fixed by the size ramp (§2), not by hiding more. Power users who want an atlas-style
"zoom reveals more" can turn it on: up to +1.5 mag at the narrowest field of view, ramping in
below 30°.

### 1.9 What hidden means (L8)

- Objects past the limit **fade** over a 0.5-mag soft edge (the existing `FADE_RANGE_MAGNITUDES`).
  The limit moves continuously through twilight, so stars come out one by one, the way they do
  in the real sky.
- **Search always wins.** Searching for a hidden object draws it, with its label and the search
  arrow. Its info card adds one line: "Too faint to see from here without binoculars".
- **Tap-to-identify ignores hidden objects.** Tapping empty sky never selects something you were
  told isn't there.
- **Planets and the Moon are never hidden by the site limit** (only Mercury/Venus by the daytime
  floor). Uranus and Neptune draw as faint outlines when below the limit; they are famous enough
  that people look for them.
- **DSOs use surface brightness, not total magnitude.** Effective magnitude = `mag +
  2.5·log10(area)`-style penalty from `size_arcmin`, capped. In a city, M31's total magnitude of
  3.4 is misleading, because its light is spread over 3°, while M45 and M42 survive.

### 1.10 The phone's light sensor — honest opinion (L9): don't build it

- **It points the wrong way.** The ambient light sensor sits beside the front camera, facing the
  user. In map mode it sees your face and the ground, and in AR mode it sees your face.
- **It doesn't have the range we need.** A moonless sky is ~0.0002–0.002 lux, suburban skyglow
  ~0.01–0.05 lux, full Moon ~0.1–0.3 lux, and a streetlit pavement 5–30 lux. Typical sensor
  floors are 0.01–1 lux, and the phone's own screen contaminates the reading. It could tell "under
  a streetlight" from "not", but **not suburb from countryside, which is the distinction the
  presets need**.
- **iOS has no public ambient-light API**, so it would be Android-only, breaking parity for a
  weak signal.
- **The camera might work.** In AR mode we already have the rear camera. Pointed at the zenith,
  with exposure metadata (ISO, shutter, aperture), it can estimate sky brightness in
  mag/arcsec², the way dedicated sky-quality-meter apps do. Per-device calibration is the risk.
  *Worth a one-day prototype later as a power-user "Measure my sky", not a dependency.* For
  casual users, "Check my sky" (§1.5) is better.

---

## 2. How stars look (L10)

### 2.1 A continuous, absolute ramp

Use a **continuous** size from magnitude, plus a gentler brightness ramp. The shape follows
printed atlases (graded discs), compressed for a phone:

| mag | −1.5 | 0 | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|---|---|
| diameter (dp) | 7.5 | 6.4 | 5.6 | 4.8 | 4.0 | 3.2 | 2.4 | 1.7 |
| brightness | 1.0 | 1.0 | 0.95 | 0.88 | 0.78 | 0.66 | 0.54 | 0.45 |

Roughly `d = 6.4 − 0.78·m` dp, floored at 1 physical px. The ramp is **absolute**, not relative
to the limit. A mag-2 star is always the same size, so changing the sky preset only adds or
removes the faint end, and the stars you already know never change. (A ramp relative to the
limit would make every city star look "big", which is tempting but makes the map shift under the
user.)

GLES3 and Metal already take `gl_PointSize` per vertex, so this costs one function in
`StellarRamps` and the existing conformance/parity tests. Zoom invariance (D12) stays: sizes are
in dp, not angles.

### 2.2 The chart halo

Each star draws a **1–1.5 dp ring in the background colour** at ~70% alpha, *after* the
constellation lines. Lines then visibly stop short of their stars, as on a printed chart. This is
the single biggest legibility trick in cartography: overlapping things separate, and it makes
dense regions (Pleiades, Orion's belt) readable. It costs a few lines in `point.frag`. Over the
camera feed the halo is the same dark ring, which also lifts stars off a noisy background.

### 2.3 First-magnitude accent (optional)

Only the ~20 stars brighter than mag 1.0 (plus planets) get a faint soft glow at 2.5× diameter
and ~12% alpha. This is the only "brightness" effect: no spikes, no bloom. It's optional. The
mockup shows it on and off, and I lean **on**, because it gives the eye entry points in a sparse
city sky.

### 2.4 Other objects by brightness

- **Planets** use the same size ramp from their ephemeris magnitude, so Venus visibly outshines
  Sirius. They keep their identity colour, and a thin ring distinguishes them from stars on the
  map. The existing disc imagery takes over when zoomed in.
- **DSOs:** marker opacity follows effective magnitude (§1.9). **When a DSO's true size exceeds
  its icon, draw its true outline** (an ellipse; needs axis ratio and position angle, which
  OpenNGC has — CC BY-SA 4.0, fine for data, credit needed). At 20° field of view, M31 should
  look 3° long, not like a 12 dp icon.
- **Satellites** keep their own styling. ISS brightness varies too much along a pass to grade
  usefully.

---

## 3. Star colour (L11)

- **Data:** join B−V from the Yale Bright Star Catalogue (public domain). `augment_names.py`
  already uses it for Bayer designations, so the HR-number join exists. Add a `bv` column to
  `stars.csv`. (The deep-star pack in §1.6 brings its own B−V.)
- **Saturation follows brightness.** Faint stars are near-white, and only bright stars carry
  visible colour (Betelgeuse orange, Rigel blue-white, Antares red). That matches the eye: rods
  don't see colour, so faint stars genuinely look white. It also declutters, because the
  colourful dots are the ones worth noticing.
- **Equal-luminance tints.** Normalise each tint to the same luminance as white, so colour never
  changes how bright a star *looks*. Without this, night mode's luminance conversion would
  quietly dim every red star.
- **Default on** with the restrained saturation above. Power users get a "Star colours" toggle.
  Colour is never the only carrier of meaning.

---

## 4. Constellation lines (L12)

### 4.1 Core shapes vs extended figure

Tag each figure's strokes as **core** (the shape people recognise) or **extended**:

- Big Dipper in Ursa Major
- Orion's belt, shoulders and knees
- Cassiopeia's W
- the Teapot in Sagittarius
- Scorpius' hook
- Leo's sickle and triangle
- Crux
- the Great Square of Pegasus
- …

| | width | alpha | shows |
|---|---|---|---|
| core | 2.0 dp | 65% | always (when the layer is on) |
| extended | 1.0 dp | 35% | when zoomed in past ~70° field of view, *and* both end stars are visible |

Curating 88 constellations is roughly a day's work, done in a new `core` flag on each stroke in
`iau.json`.

### 4.2 Lines follow their stars

At build time, snap each figure vertex to its catalogue star (nearest within 0.2°) so every
segment knows its two endpoint magnitudes. A segment fades when **either endpoint is below the
current limit**. In a city, Ursa Major then reduces to the Big Dipper on its own, because the
bear's legs are mag 3–4 stars. **Visibility and declutter become the same mechanism**, and no
user is ever shown lines joining stars they can't see.

### 4.3 Focus

The constellation nearest the centre of the view is drawn at full strength, and the rest at about
75%. The change takes 400 ms with hysteresis, so panning doesn't flicker. It's subtle on purpose:
the effect is "this one is in front", not a spotlight. Selecting a constellation (search, or a
tap on its name) makes the contrast stronger and also draws its boundary (§5).

### 4.4 Asterisms

The catalogue already has an `asterism` type, with one entry (`asterism/summer_triangle`, a
searchable point with no lines). Extend it with line data for the shapes casual users actually
know, many of which cross constellations: Summer Triangle, Winter Hexagon, Great Square, Northern Cross, Teapot, Keystone,
Big Dipper, Sickle. **Searchable by name.** When searched, the shape is drawn as a highlighted
overlay. A power toggle draws all of them as thin dotted lines. This is cheap and very casual-
friendly: more people search "Big Dipper" than "Ursa Major".

---

## 5. Constellation boundaries (L13)

- **Storage:** the IAU boundaries (Delporte 1930) in the standard J2000 digitisation, CDS
  catalogue VI/49 (Davenhall & Leggett). Store them as **unique edges**, each shared edge once
  with both neighbouring constellation ids, in `source-data/constellations/boundaries.json`.
  About 13k points, ~150 KB of source. Unique edges mean no double-drawn translucent overlaps,
  and one constellation's outline is just a filter by id.
- **Densify at the source.** The boundaries are arcs of constant RA and **constant
  declination**. A parallel of declination is a small circle, so `GreatCircleSubdivision` would
  draw it wrong. Pre-densify to ≤1° spacing so straight chords between points are accurate.
- **Look:** thin (1 dp), **dashed**, neutral warm grey at ~25% alpha, visibly different from
  figures without relying on colour. The dash period is set in screen space (an arc-length
  attribute scaled by a field-of-view uniform), so dashes don't stretch with zoom. Metal's
  line-width handling needs the same check as its other lines.
- **Enable:** **off by default.** It becomes a parameter on the Constellations layer
  (`LayerParameter`), next to Lines / Names / Art. **In context, regardless of the toggle:**
  selecting a constellation draws *its* boundary solid at ~40% with a very faint region fill,
  for as long as it's selected.
- **The bigger win is that the HUD and tap can say where you are.** Point-in-boundary on B1875
  coordinates (the classic Roman 1987 lookup, CDS VI/42) gives "You're looking at Taurus" for
  the centre of view, and tapping empty sky opens the constellation's info card instead of
  nothing.

---

## 6. Extended objects — the Milky Way and friends (L14)

### 6.1 Representing the Milky Way

- **A stylised isophote band, not a photograph.** Three or four smooth brightness levels in a
  cool, desaturated grey-blue at 6–18% alpha, the way Sky Atlas 2000 draws it. The Great Rift
  and the Cygnus/Sagittarius star clouds should read clearly.
- **License-clean source we generate ourselves:** sum Tycho-2 star flux (ESA; free with credit)
  into ~0.25° cells in galactic coordinates, smooth it, and quantise it to levels. Dust lanes
  show up as deficits, so the rift comes for free. The tool lives in `tools/`, and the
  reproducible output keeps F-Droid happy.
- **Texture:** a galactic-coordinate band (longitude 0–360°, latitude ±35°), ~2048×400, one
  channel. A few hundred KB. It is sampled per pixel by direction in the sky shader, so it needs
  no patch geometry. Single-channel means it's tinted at draw time, and night mode is free.
- **Visibility follows the limit:** opacity ramps from 0 at limit 4.0 to full at 6.0. A city
  user doesn't see it by default, which is honest. Power users can force "show the Milky Way
  even when I can't see it" as a faint outline.

### 6.2 Search and tap for extended objects

Add an optional **outline** to catalogue objects (polygon or ellipse) and a **search anchor
rule**:

- **Search "Milky Way"** points the arrow at the **highest visible point on the galactic
  equator right now**, not the galactic centre (which may be below your horizon), and pulses the
  band's opacity. The info card is ordinary.
- **Same pattern** for the LMC/SMC (already DSOs with size), the Coalsack, the Hyades, M31
  (ellipse), constellations (boundary) and asterisms (lines). The search highlight draws the
  **outline** instead of a reticle whenever an object has one.
- **Tap-to-identify** falls back in order: point object near the tap → extended object
  containing the tap → the constellation containing it. Every tap gets an answer.

---

## 7. Large images — constellation art (and the Milky Way)

### 7.1 Style

- **Monochrome line art, tinted at draw time.** Public-domain engravings already are line art,
  so cleaned up they become a single-channel mask (R8 / EAC R11): small, tintable (a pale warm
  ivory, ~25% default opacity), and night mode is free.
- **One atlas for all figures,** for consistency. Bode's *Uranographia* (1801) covers nearly all
  modern constellations including Lacaille's southern ones. Hevelius (1690) lacks them and is
  **mirror-reversed** (a globe view). Either way the cleanup (removing engraved stars, grids and
  lettering) is the long pole: budget it as an art project. Classify the cleaned derivatives in
  `ASSET-LICENSES.txt` as `[third-party]` (public-domain source). render-gles3 §7.3's suggestion
  of `[arr]` predates the current licensing rule and conflicts with it.
- **Registration** follows render-gles3 §7.3: three anchor stars per figure on the spherical
  patch primitive.

### 7.2 What keeps it uncluttered

1. **Default: only the focused constellation's art,** and only after the view settles (~500 ms
   dwell). It fades in over 600 ms and fades out on pan. Neighbours stay off.
2. **"All art" is a power option.** In that mode the focused figure shows at full opacity and
   the others at about a third.
3. **Art hands over to lines as you zoom.** It's full below ~90° field of view, fades out by
   ~25°, and lines and stars take over.
4. **The info card entry point:** a constellation's card gets "Show the figure", which selects
   it and shows the art even if the layer is off. That's how casual users discover it.
5. **Night mode caps it at half opacity.** Large bright areas cost dark adaptation.
6. **Delivery:** 88 figures at ~1024² single-channel is ~10–15 MB as WebP. Ship the ~12 zodiac
   figures plus a few famous ones (Orion, the Bears, Cassiopeia, Cygnus, Crux) in the APK, and
   the rest as a data pack (D79).

---

## 8. Labels and transitions (cross-cutting)

### 8.1 Labels

- **Labels obey the visibility limit.** The pre-filter becomes `min(FOV threshold, local
  limit)` for stars. Named DSOs and planets keep their exemptions but respect §1.9.
- **Three type styles, not three colours:**
  - constellations in spaced small caps at lower contrast (region names);
  - stars in regular weight;
  - planets in medium weight with their identity colour.
  Different typography reduces perceived clutter more than removing labels does.
- **Priority flips with zoom.** At wide fields constellation names outrank star names (they
  label regions); as you zoom in, star names outrank them.
- **A density budget** on top of overlap rejection: at most about one label per 1.5 cm² of
  screen. This prevents the "label carpet" that can occur with no overlaps at all.
- **Try four positions** (right, left, above, below) before rejecting a label. This is standard
  cartographic placement, and it keeps more labels without adding overlap.

### 8.2 How things appear and disappear

| What drives the change | How it transitions | Why |
|---|---|---|
| A continuous quantity (limit through twilight, Moon) | **Per-object fade by value** — the 0.5-mag soft edge | Stars come out one by one, as in the real sky. No timers, no lag |
| Zoom (extended lines, art hand-over, labels' FOV threshold) | **Fade by field of view, tied to the gesture** | Reversible mid-pinch. Nothing "catches up" after you stop |
| Pan / focus (focused constellation, art dwell) | **Time-based, 400–600 ms, with hysteresis** | Prevents flicker when sweeping across a boundary |
| User toggles a layer or preset | **Fast fade (~200 ms) or animated limit (~600 ms)** | Instant changes read as glitches; slow ones read as lag |
| Off-screen | **Hard cull** | Nothing to see, nothing to fade (as `LabelDeclutterer` already says) |

Time-based fades need frames, so `RENDERMODE_WHEN_DIRTY` gets brief continuous bursts while a
fade is running, as `LabelFader` already does. A still device still draws nothing.

### 8.3 Night mode checklist

- Hierarchy survives the luminance-only red: size ramp, halo and brightness ramp; dashed
  boundaries vs solid figures; core vs extended by width.
- Star tints are luminance-normalised (§3), so red stars don't dim.
- The Milky Way and art are capped at 50% of their normal opacity, and the glow accent is off.
- Turning on night mode is a strong hint the user is outdoors in the dark. If Sky is on a manual
  preset, offer "Match my sky?" once.

---

## 9. Bang for the buck — recommended order

| # | Item | Cost | Impact | Notes |
|---|---|---|---|---|
| 1 | Continuous star ramp + chart halo (§2.1–2.2) | **S** | **Very high** | Pure renderer + `StellarRamps`. Fixes most of the clutter on its own |
| 2 | Sky presets wired to `magnitudeLimit`, labels obey it (§1.2, §8.1) | **S–M** | **Very high** | Uniform already plumbed. Manual presets first, Auto later |
| 3 | Star colour from BSC B−V (§3) | **S** | Medium | Data join + existing `colorForIndex` |
| 4 | "Check my sky" (§1.5) | **S–M** | High | Makes presets accurate with no sensor or data |
| 5 | Twilight, Moon and horizon terms (§1.1) | **M** | High | Shader + one Kotlin function. Stars emerge at dusk, time travel gets better |
| 6 | Core/extended lines + lines follow stars (§4.1–4.2) | **M** | High | Mostly curation and a build-time snap |
| 7 | Asterisms, searchable (§4.4) | **S–M** | Medium-high | Casual users search for these |
| 8 | Boundaries + "you're looking at Taurus" (§5) | **M** | Medium-high | The HUD and tap fallback are the real value |
| 9 | Auto site from light-pollution grid (§1.3) | **M–L** | High | Tooling + licence check. Makes Auto truly auto |
| 10 | Milky Way band + extended-object search (§6) | **M–L** | Medium-high | Generated texture, galactic-coordinate shader |
| 11 | DSO true outlines + surface-brightness visibility (§1.9, §2.4) | **M** | Medium | OpenNGC join |
| 12 | Deep-star pack + Binoculars (§1.6) | **L** | Medium (power) | Also brings B−V for all |
| 13 | Constellation art (§7) | **L** (art) | Medium-high (delight) | Patch primitive + an art project + a data pack |
| — | Light sensor (§1.10) | — | — | Don't build. Camera "Measure my sky" maybe, as a later experiment |

**Suggested phases:**

1. **"A calmer map"**: items 1–3. Small, lands quickly, and visibly fixes the complaint.
2. **"Matches my sky"**: items 4, 5 and 9.
3. **"Shapes you know"**: items 6, 7 and 8.
4. **"Pictures"**: items 10, 11 and 13.
5. **Binoculars**: item 12, whenever the deep catalogue is worth its size.

## 10. Open questions for the maintainer

1. Daytime floor (L4): is ~mag 2 right, or should the day map show everything at reduced
   strength?
2. Zoom (L7): off by default, as recommended?
3. Art (L14 / §7): Bode, or another single public-domain source? Is ~2 MB of art in the APK
   acceptable, with the rest as a pack?
4. Asterism list: which ~10–15 to ship first?
