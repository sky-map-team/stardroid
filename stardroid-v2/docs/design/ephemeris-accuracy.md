# Ephemeris Accuracy — Frame Audit and the Case for an Off-the-Shelf Library

**Status: REFERENCE + PROPOSED.** The frame audit below is a record of what was measured while
reviewing the `MeeusEphemeris` change (PR #101); the precession fix it motivated is implemented
(D84). The library evaluation in Part 3 is **proposed and not decided** — it is written up so it
can be picked up later without redoing the research.

Companion to [core-math-astronomy.md](core-math-astronomy.md), which describes the module as
built. This doc is about how *accurate* it is and where the remaining error lives.

---

## Part 1 — The frame audit

### The finding

`MeeusEphemeris` was introduced to fix solar-eclipse timing, on the stated grounds that the
baseline `KeplerianEphemeris` Sun was "~0.4° off" for want of aberration and nutation. **That
diagnosis was wrong, and the real cause is more interesting.**

Measuring the angular separation between the two implementations' Sun, at the vernal equinox of
several years:

| Year | Baseline vs. Meeus Sun |
|---|---|
| 2000 | 0.004° |
| 2010 | 0.144° |
| 2020 | 0.275° |
| 2026 | 0.365° |
| 2040 | 0.555° |

Zero at J2000, growing linearly at 0.0139°/year. General precession is 50.3″/year = 0.01397°/year.
Aberration and nutation together are ~20″ and ~17″ — two orders of magnitude too small to explain
this. The discrepancy was **precession**: the baseline Sun was essentially correct *in the J2000
frame*, and its apparent "error" was the J2000 → equinox-of-date rotation, measured against USNO
apparent positions that are quoted in the of-date frame.

### Why it mattered

Sky Map v2 draws a **J2000 sky**:

| Source | Frame | Where |
|---|---|---|
| Star / DSO catalog | J2000 | `CatalogModel.kt` |
| Planet orbital elements | J2000 | `OrbitalElements.kt` (Standish) |
| Ecliptic → equatorial rotation | J2000 obliquity | `EphemerisGeometry.kt` |
| Baseline Sun, planets | J2000 | `KeplerianEphemeris` |
| Baseline Moon (Almanac D22) | **of date** | `KeplerianEphemeris` |
| Meeus Sun and Moon | **of date** | `MeeusEphemeris`, as written |
| Sidereal time | **of date** | `Time.kt` `meanSiderealTimeDeg` |

`MeeusEphemeris` became the app-wide ephemeris in the same PR, so its of-date Sun and Moon were
being drawn against a J2000 star field: a systematic ~0.365° displacement, larger than the Sun's
own disc and growing every year.

The eclipse fix was still real, but for a different reason than claimed. The baseline Moon was
*already* of-date (the Almanac D22 series is), so the old Sun–Moon separation carried the full
precession offset. Moving the Sun into the Moon's frame is what removed the ~30-minute timing
error. **The gain was frame consistency, not Sun accuracy.**

### The fix (D84)

`Precession.kt` supplies the IAU 1976 (Lieske) rotation between J2000 and the mean equinox of a
date. `MeeusEphemeris` now rotates its results back to J2000 before returning them, so its output
matches the frame everything else is drawn in.

Two properties make this safe:

- Precession is a **rigid rotation**, so angular separations are invariant under it. The Sun and
  Moon receive the same rotation, so the eclipse timing is untouched — `PrecessionTest` pins this
  explicitly.
- The Moon's diurnal parallax is computed against an observer vector built from *of-date* sidereal
  time, so the subtraction must happen in the of-date frame; only the result is rotated. Order
  matters here and the code comments say so.

Verification: `KeplerianEphemeris` derives the Sun from J2000 elements by a path that never touches
precession, so agreement between the two implementations is an independent check on the frame. The
residual is now **0.001–0.007° across 1800–2050, with no secular trend** (was 0.365° in 2026).
`MeeusEphemerisTest.sunIsReturnedInTheSameJ2000FrameAsTheBaselineAcrossTheValidRange` guards it.

### What is still inconsistent — the known remaining gap

**Sidereal time is of-date, and it is used against J2000 right ascensions.** `meanSiderealTimeDeg`
returns an of-date angle, and it feeds:

- `SkyModel.zenithRaDec` → `localFrame` — the entire rendering orientation;
- `RiseSet.altitudeDeg`, `azimuthDeg`, and the `riseSetUtHours` solver.

So every hour angle in the app carries the same ~0.365° offset. Consequences:

- **Rendering: harmless in practice.** The offset is a rigid rotation applied to the whole sky at
  once, so it shifts where the sky sits relative to the horizon and compass, not where objects sit
  relative to each other. At 0.365° it is far below the sensor pointing error
  ([sensor-correction-model.md](sensor-correction-model.md)).
- **Rise/set and azimuth: a real ~1.5-minute error**, uniform across all objects. Published
  sunrise/sunset times are checkable by users, so this is the part worth fixing.

This is **pre-existing** — it predates PR #101 and affects stars exactly as much as planets. It was
deliberately left out of D84 because fixing it touches `SkyModel` (the render orientation) and
`RiseSet`, and warrants its own PR with device verification.

Two ways to close it, when someone picks this up:

1. **Precess positions to date inside the hour-angle functions.** Local, per-object, three call
   sites in `RiseSet.kt`. Cheap and correct for rise/set. Does not address `SkyModel`.
2. **De-precess the zenith in `SkyModel.zenithRaDec`.** One rotation puts the local frame into
   J2000, making the whole render path consistent, without touching a single catalog site.

Doing both makes the app fully consistent. **Do not** instead precess the catalog to date: the
J2000 → render path has 8+ `toGeocentricVector()` call sites across `CatalogLayers`, `GridLayer`,
`EclipticLayer`, `MeteorShowerLayer`, search and object-info, with no single choke point. Rotating
the two of-date quantities inward is far cheaper than rotating thousands of catalog rows outward.

### Residual errors after D84

Ranked by size, for anyone deciding what to do next:

| Error | Size | Notes |
|---|---|---|
| Hour-angle frame mismatch | 0.365°, growing | Above. Rise/set only, in practice. |
| Saturn position | ~10′ | Standish linear elements cannot carry the Jupiter–Saturn great inequality. |
| Jupiter position | ~6′ | Same cause. |
| Mars, Venus, Mercury, outer planets | ≲1′ | Fine. |
| Nutation, on Sun/Moon | ≤17″ | Not removed before de-precessing. Cancels between Sun and Moon. |
| Light-time, planets | ≤40″ (Mars) | Not applied. Under one screen pixel at typical FOV. |
| Annual aberration, planets | ~20″ | Not applied. |

Note the ordering: **the frame gap is 2–4× larger than the worst planet-ephemeris error.** Any
effort spent on better planetary theory before closing it is spent on the wrong problem.

### Appearance quantities — deliberately not fixed

- `SolarSystemLayer.kt` uses `magnitude()` only for `labelPriority`, never for brightness or size.
  So Saturn's hard-coded `-8.75` (ring geometry ignored, a real ±1 mag swing) and the Moon's fixed
  `-10` are **not user-visible**. Low priority despite looking wrong.
- Lunar `phaseAngleDeg` still runs the baseline's elongation approximation against the baseline's
  Keplerian Sun, so the drawn terminator is computed on a different model from the drawn Moon.
  ~1% in illuminated fraction. Now that a good Sun and Moon exist, Meeus 48.2 would be strictly
  better and is a two-line change; it was deferred to preserve the lunar-phase golden tests.
- `earthDistanceAu` now returns a true Moon distance, but `angularSizeDeg(body)` is still fixed, so
  the Moon's 29.4′–33.5′ perigee/apogee variation is available and unused. Cheap win if a
  "supermoon" surface is ever wanted.

---

## Part 2 — Where this leaves the hand-rolled ephemeris

After D84 the ephemeris is *internally* consistent and good to roughly an arcminute, except for
Jupiter and Saturn. That is adequate for a phone planetarium. The open question is whether we want
to keep owning it.

Currently hand-maintained, all pinned by golden tests:

| File | Lines | What it is |
|---|---|---|
| `KeplerianEphemeris.kt` | 274 | v1 port: Kepler solver, Almanac lunar series, magnitudes |
| `MeeusEphemeris.kt` | ~380 | Meeus ch. 25 Sun, ch. 47 ELP tables (two transcribed tables) |
| `OrbitalElements.kt` | 163 | Standish J2000 elements |
| `RiseSet.kt` | 220 | Almanac hour-angle iteration |
| `LunarPhase.kt` | 94 | Phase naming |
| `Precession.kt` | ~85 | D84 |
| `EphemerisGeometry.kt` | 52 | Ecliptic/equatorial |

Roughly 1,250 lines, most of it transcribed from books. The `MeeusEphemeris` tables in particular
are 120 rows of integers that nobody will ever meaningfully review — a transcription error in a
low-order coefficient would pass code review and quietly survive the tests.

---

## Part 3 — Off-the-shelf library evaluation (PROPOSED, not decided)

### Candidates

| Option | License | Data files | Accuracy | Verdict |
|---|---|---|---|---|
| **Astronomy Engine** (cosinekitty) | MIT | none | ≤1′ vs NOVAS | **Leading candidate** |
| Swiss Ephemeris (Java port, th-mack) | AGPL-3.0 or commercial | none in Moshier mode | 0.1″ planets, 3″ Moon | License-blocked |
| Orekit | Apache-2.0 | needs `orekit-data` | metre-class | Overkill, heavy for Android |
| JPL SPICE + DE440s | public domain | ~32 MB minimum | metre-class | Too big to bundle |
| libnova | LGPL (C) | none | arcsec | JNI across 4 ABIs |
| commons-suncalc | Apache-2.0 | none | Sun/Moon only | Doesn't cover planets |

**Swiss Ephemeris is technically the strongest and is what most projects reach for, but AGPL would
pull the whole app to AGPL.** With v2 at GPLv3 plus All-Rights-Reserved branding assets and a Play
Store presence, that is not a trade worth making for precision invisible on a phone.

### Astronomy Engine specifics (verified 2026-08)

- <https://github.com/cosinekitty/astronomy>, MIT, ~960 stars, last push 2025-01-27.
- Kotlin binding is a **single 433 KB `astronomy.kt` with no dependencies** — pure JVM Kotlin, so
  it satisfies the D20 pure/Android module gate that `:core:astronomy` is held to.
- Stated accuracy: always within 1 arcminute of NOVAS.
- **Not on Maven Central** (checked — zero hits). Distributed via JitPack as
  `io.github.cosinekitty:astronomy`.

What it would replace or fix:

- Precession, nutation, aberration and light-time, handled rigorously with explicit frame
  transforms — i.e. Part 1 stops being our problem, including the open rise/set gap.
- Planet positions to ~1′, versus ~10′ on Saturn today.
- `searchLocalSolarEclipse` — partial begin/peak/end and obscuration for an observer. This is
  exactly what `MeeusEphemerisTest` currently reverse-engineers with a three-hour, one-second scan
  loop.
- Magnitude including Saturn's ring tilt; true lunar phase angle; apsides.
- Rise/set with refraction (`RiseSet.kt`), moon phases/quarters (`LunarPhase.kt`).

### Why this is cheaper than it sounds

`Ephemeris.kt` already says: *"A future VSOP87/ELP2000 implementation can slot in behind this same
interface."* **The seam was designed for this.** The work is an adapter class,
`AstronomyEngineEphemeris : Ephemeris`, not a rewrite. `KeplerianEphemeris` can stay for one
release as a differential-test oracle and then be deleted along with `OrbitalElements`,
`EphemerisGeometry`, `MeeusEphemeris`, `Precession` and most of `RiseSet`/`LunarPhase`.

### Open questions before committing

1. **Dex size after R8.** 433 KB of source, but `GravitySimulator`, Lagrange points and the
   Galilean moons are dead code here and should shrink away. Not measured.
2. **JitPack vs. vendoring.** JitPack builds from source on demand, which is friction for the
   fdroid flavor's reproducibility. Since it is one MIT file with no dependencies, **vendoring it
   into the repo under its MIT header is probably the right call** — it sidesteps JitPack entirely
   at the cost of manual updates, and the underlying model is static.
3. **Golden-test churn.** The v1-faithful goldens stop being meaningful for these paths. Arguably
   overdue, but it is real work and a judgement call about how much v1 parity still matters.
4. **Maintenance signal.** Last push Jan 2025. Quiet, not dead — less alarming for an ephemeris
   than for most libraries, but worth a look before depending on it.

### Suggested next step

A spike, not a commitment: implement `AstronomyEngineEphemeris` behind the existing interface and
diff it against `KeplerianEphemeris`/`MeeusEphemeris` across 1800–2050 for every body. That gives
the real accuracy delta and the real dex cost before anything is deleted. Small, because the seam
already exists.

### Sources

- <https://github.com/cosinekitty/astronomy>
- <https://www.astro.com/swisseph/swephinfo_e.htm> (licensing)
- <https://www.astro.com/swisseph/swedownload_e.htm> (Java port, Moshier mode)
