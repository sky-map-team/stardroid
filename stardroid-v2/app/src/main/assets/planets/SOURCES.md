# Solar-system disc textures — provenance

Every file in this directory is derived from NASA / ESA / JHUAPL imagery released into the
**public domain**. It is scientific data, not branding: it is classified `[third-party]` in
`ASSET-LICENSES.txt`, so the reserved-rights notice in `LICENSE.md` does **not** apply to it.

Regenerate with:

```
python3 tools/planets/generate_planet_discs.py
```

The tool is the authority on how each file is made; this page records where the pixels came from
and what is known to be imperfect about them. See `docs/design/solar-system-imagery.md` §1 (D85)
for the rules the art follows: power-of-two and square, fully lit, centred, IAU north pole up,
straight (non-premultiplied) alpha with an inward-feathered limb, and no baked terminator — phase
is procedural (D88).

## Files

| File | Size | Source | Mission / instrument |
|---|---|---|---|
| `moon.webp` | 1024² | [LROC color mosaic, 4k](https://svs.gsfc.nasa.gov/4720) (`lroc_color_poles_4k.tif`) | LRO / LROC + LOLA, NASA GSFC SVS |
| `sun.webp` | 512² | [SDO browse archive, 2026-08-14 00:00 UT](https://sdo.gsfc.nasa.gov/assets/img/browse/2026/08/14/20260814_000000_1024_HMIIF.jpg) | SDO / HMI continuum (intensitygram, flattened) |
| `mercury.webp` | 256² | [PIA12397](https://images-assets.nasa.gov/image/PIA12397/PIA12397~orig.jpg), full global mosaic | MESSENGER / MDIS, NASA/JHUAPL/Carnegie |
| `venus.webp` | 256² | bundled `celestial_images/planets/nasa_venus.webp` | Magellan radar mosaic, NASA/JPL |
| `mars.webp` | 256² | bundled `celestial_images/planets/hubble_mars.webp` | HST / WFPC2, NASA/ESA |
| `jupiter.webp` | 512² | bundled `celestial_images/planets/hubble_jupiter.webp` | HST / WFC3 (OPAL), NASA/ESA |
| `saturn.webp` | 512² | bundled `celestial_images/planets/hubble_saturn.webp` | HST / WFC3 (OPAL), NASA/ESA |
| `uranus.webp` | 256² | bundled `celestial_images/planets/hubble_uranus.webp` | HST, NASA/ESA |
| `neptune.webp` | 256² | bundled `celestial_images/planets/hubble_neptune.webp` | HST, NASA/ESA |
| `pluto.webp` | 128² | bundled `celestial_images/planets/nh_pluto_in_false_color.webp` | New Horizons / Ralph, NASA/JHUAPL/SwRI |

`moon.webp` replaced v1's eight 64² baked phase bitmaps (`moon0`–`moon7`) when the procedural
terminator landed (D88). One fully-lit disc now serves every phase, and the renderer paints the
terminator over it — which is also why nothing here may carry a baked shadow.

## How the discs are made

Two paths, chosen per body:

- **Projected from a global map** (Moon, Mercury). An equirectangular mosaic is projected
  orthographically onto the disc, viewed from 0°N 0°E. A map carries no illumination of its own,
  so the result is fully lit by construction. For the Moon, 0°E is the centre of the near side,
  so this is the Moon as it is actually seen.
- **Cropped from a photograph** (everything else). The lit extent is measured, cropped square
  about its centre, and resized.

Where a source has a sharp edge, the limb is then refined to its half-maximum contour, which sets
the centre as well as the radius. This matters most for the Sun: a plain brightness threshold
catches the scattered light around the disc *and* the image credit burnt into the frame, which
between them put the disc 11 px off centre in a 1024 px image and 2% oversized. The margin was a
ring of opaque near-black pixels just inside the alpha edge — invisible against the night sky, but
a dark arc around the Sun against the daytime gradient. Bodies whose brightness declines steadily
to the limb instead are left alone: that decline is real limb darkening, and cutting at
half-maximum would slice off real planet.

Either way the disc's diameter is exactly the texture width, so draw-time sizing (D86) can treat
`angularSizeDeg` as the body's true apparent diameter with no correction. **Saturn is the
exception**: its texture spans the *ring system*, so its angular size must be scaled by the A
ring's outer edge at 2.269 equatorial radii. Saturn's disc is 226 px of the 512.

RGB is WebP quality 88; alpha is stored losslessly, because the limb feather is what the
renderer's blending reads.

## How big each one is

Two ceilings, and the lower one decides:

- **What the source holds.** Past the lit span measured out of the source image there is nothing
  left to resolve, and a larger texture is a larger upscale rather than more planet. The bundled
  Hubble frames run 360–470 px, which is why nothing here exceeds 512².
- **How large the body is ever drawn.** A texture is only magnified past its own texels once the
  disc covers more than `size` pixels of screen; below that the extra detail is mipmapped straight
  back out. In glyph mode (D87's default) a body drawn *D* degrees across passes `size` pixels at
  a field of view of `shortSide × D / size` — so on a 1280 px screen Jupiter's 2.05° glyph
  outgrows 256 texels at a 10° FOV, well inside the range people zoom through, whereas Mercury's
  0.74° glyph does not until 3.7°.

Jupiter and Saturn clear both bars and are 512²: they are the largest planets drawn, and the only
two whose sources hold real structure — bands, the Great Red Spot, the Cassini division. Saturn is
the strongest case of the set, because its texture spans the ring system, so at 256² its *globe*
had only 113 texels. Uranus and Neptune are drawn nearly as large but their sources are soft,
near-featureless discs, so more resolution would buy nothing. Mercury and Venus have the detail
and are never drawn big enough to show it. Pluto stays 128² despite being five times below its
New Horizons source: it is the smallest glyph in the ladder, and the detail is not worth carrying.

This is not free — the textures live in a byte-budgeted GL cache, and raising Jupiter and Saturn
took its working set from 18.2 MB to 23.1 MB against what was then a 24 MB budget. See
`TextureCache.DEFAULT_BYTE_BUDGET`, and `SolarSystemAssetBudgetTest`, which adds up the files in
this directory so the next resolution change has to face the same arithmetic.

## Known imperfections

Recorded rather than hidden — each is judged invisible at the sizes these are drawn, but a future
re-derive should fix them.

- **Venus is a radar map, not a photograph.** The Magellan mosaic shows the *surface*, which no
  observer can see; the real telescopic Venus is a featureless bright cloud deck. The design named
  this file, and it is far more informative than a blank disc, but it is not what you would see
  through an eyepiece. `celestial_images/planets/hubble_venus_clouds_tops.webp` is the honest
  alternative if we ever want it.
- **Mercury has 10.5% of its map interpolated.** MESSENGER's mosaic has unimaged gaps; projected
  raw they read as black bites out of the disc. They are filled by interpolating along each line
  of latitude — plausible neighbouring terrain rather than invented craters, and much better than
  the alternative source, whose baked terminator left 21% of the disc black.
- **Uranus's north/south sense is unverified.** The source shows Uranus's bands running vertically
  (its axis lies almost in the ecliptic), and the disc is rotated 93° to stand the pole up — but
  nothing in the image distinguishes the north pole from the south, so the disc may be inverted.
  Uranus is a handful of pixels at any realistic zoom, and its banding is barely visible even
  here.
- **Saturn's rings are frozen at their opening in the source image**, measured at **26.5°** from
  the ring ellipse's axis ratio — near maximum opening, and more open than the ~20° the design
  suggested. The ring *orientation* still tracks the sky correctly (D85 §1); only the openness is
  fixed until D89's tilt variants land. Note this rotates Saturn 90° from v1's sprite, which drew
  the rings vertically.
- **Jupiter's limb is genuinely dark.** Hubble's disc fades to about a fifth of its central
  brightness at the edge, so roughly 3% of the texture is opaque but nearly black. That is the
  planet, not a margin — clipping it at half-maximum like the Sun would remove real disc — and
  Jupiter is only ever drawn against a night sky, where a dark limb reads correctly.
- **The Sun is a single day's disc**, 2026-08-14, sunspots and all. That is a permanent dated URL,
  not a "latest" endpoint, so regeneration is reproducible.
- **`saturn_disc.webp` (the bare, ringless disc) is not here.** §1 asked for it alongside
  `saturn.webp` so that D89's ring-tilt work becomes an asset swap. It was skipped deliberately:
  every public-domain Saturn photograph has rings, so a bare disc could only be made by painting
  them out and inventing the terrain underneath — and D89 calls for re-rendering the tilt variants
  offline in a 3D renderer, which needs a proper cylindrical Saturn map rather than an inpainted
  disc. Better to source that map when the work actually starts than to ship a fake now.
