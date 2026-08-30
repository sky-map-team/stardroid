# Solar-system disc textures

`generate_planet_discs.py` builds every file in `app/src/main/assets/planets/` from
public-domain NASA/ESA/JHUAPL imagery. It implements §1 of
[`docs/design/solar-system-imagery.md`](../../docs/design/solar-system-imagery.md) (D85).

```
python3 tools/planets/generate_planet_discs.py            # all bodies
python3 tools/planets/generate_planet_discs.py --only moon saturn
python3 tools/planets/generate_planet_discs.py --out /tmp/preview
```

Requires Pillow and numpy. Sources that are not already in the repo are fetched once into
`.cache/` (git-ignored) from permanent, dated URLs — nothing reads a "latest" endpoint, so
re-running produces the same bytes. Provenance and known imperfections are recorded in
[`app/src/main/assets/planets/SOURCES.md`](../../app/src/main/assets/planets/SOURCES.md); this
page is about the mechanics.

## What the output has to satisfy

Two invariants, checked on every run — the script prints a `WARNING` line rather than failing, so
a deliberate exception stays possible:

1. **The disc fills the texture.** D86 sizes bodies by their true apparent diameter, so any margin
   between the limb and the texture edge would draw every body too large by exactly that margin.
   The limb feather is therefore applied *inward* from the edge, and `refine_limb` pulls the
   extent in to the half-maximum contour on sources with a hard edge — a brightness threshold
   alone lands outside the body whenever there is a halo around it, and the margin shows as a dark
   rim against a lit sky. It measures the contour by centroid and area rather than a bounding box,
   which would take the single furthest pixel of a noisy edge and land outside the limb again.
2. **The disc is lit to the limb.** A baked terminator would fight the procedural one D88 puts on
   top of it, and the phase would be applied twice. The check is the fraction of the disc darker
   than 6% luminance, warning above 10%: real limb darkening tops out at 5% (Jupiter, the
   strongest here), while a terminator-baked photograph reads about 21%.

Testing instead for a *one-sided* shadow — which is what a terminator is — was tried and does not
work: measured over limb quadrants, the baked Mercury photo scores 1.6 while Pluto's genuine
albedo variation scores 2.2.

## Adding a body

Append a `Body` to `BODIES`. The two paths are:

- `equirect=True` — the source is a global map, projected orthographically. **Prefer this.** A map
  has no illumination baked in, so invariant 2 holds by construction. Add `gapfill=True` if the
  mosaic has unimaged patches.
- the default — the source is a photograph of a disc, which is cropped to its lit extent. Check
  the result: photographs of the inner planets very often carry a phase.

`rotate` brings the body's IAU north pole up, and should be *measured*, not guessed. For an oblate
body the disc's own major axis is its equator (a principal-component fit of the lit mask recovers
Jupiter's flattening as 7.3% against a true 6.5%); for Saturn the ring ellipse gives both the
orientation and, through its axis ratio, the ring opening angle. Neither resolves north from
south — that needs a recognisable feature, like Jupiter's Great Red Spot sitting south of the
equator, or Mars's polar caps.
