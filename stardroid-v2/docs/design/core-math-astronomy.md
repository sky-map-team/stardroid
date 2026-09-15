# Detailed Design: `:core:math` and `:core:astronomy`

**Status: IMPLEMENTED** (slice 2 — `:core:math` + `:core:astronomy`, golden-tested against v1,
D25/D27; slice 5 — the sky model and sensor fusion, D36). Later additions: the rise/set solver
(D50/D51) and the Meeus Sun/Moon with the J2000 frame fix (D84 — see
[ephemeris-accuracy.md](ephemeris-accuracy.md) for the error budget). Detailed design
increment 1 (with [render-api.md](render-api.md)).

Source baseline: v1 `math/`, `ephemeris/`, `space/`, and `control/AstronomerModelImpl.kt`.
Both modules are pure Kotlin: no Android imports, no DI framework, constructor injection only.

## Findings from v1 that drive this design

| v1 issue | v2 consequence |
|---|---|
| `julianDay()` formula valid only 1900–2099 | Time travel silently degrades outside that window. Replace with the exact identity `JD = epochMillis / 86 400 000 + 2440587.5` — valid for all dates, simpler, and removes `Calendar`/`TimeZone` use. |
| `Float` throughout (2009 Dalvik performance choice) | Replace with `Double` in all core computation. Sidereal time multiplies Julian-day deltas by ~360.98 deg/day: a `Float`'s ~7 significant digits costs visible arcminutes decades from J2000. Modern ART/NEON makes the perf argument obsolete; conversion to `Float` happens once, at the render boundary. |
| Mutable `Vector3` (`normalize()`, `updateFromRaDec()` mutate in place); `AstronomerModelImpl` explicitly documents that it returns non-defensive internal state | All v2 math types are immutable data classes. The "snapshots are trivially testable" renderer contract (D13) depends on this. |
| `SolarSystemBody` enum carries `R.drawable`/`R.string` IDs; `OrbitalElements` uses `android.util.Log` | Resources and logging leave the core. The enum keeps only physics; imagery/name mapping lives app-side (see below). |
| `AstronomerModelImpl` calls `SensorManager.getRotationMatrixFromVector()` and caches sensor arrays | The sensor-to-rotation-matrix conversion is Android's job, at the edge. The core model receives an orientation as a plain matrix. |
| `MathUtils` object is a Java-interop shim (its own TODO says delete it) | Gone; call `kotlin.math` directly. |
| `getNextFullMoonSlow` (hour-stepping loop), duplicated phase/magnitude code paths | Replaced by one analytic implementation with tests. |

## `:core:math`

Immutable value types and pure functions. No state, no time, no astronomy-specific constants
beyond coordinate conventions.

```kotlin
data class Vector3(val x: Double, val y: Double, val z: Double)   // +, -, *, dot, cross,
                                                                  // norm, normalized()
data class Matrix3(...)   // column/row construction, transpose, inverse, operator *(Vector3)
data class Matrix4(...)   // perspective/look-at builders; :render:api's pure SkyProjection
                         // (D21) composes these — backend and search arrow share the result
data class RaDec(val raDeg: Double, val decDeg: Double)           // + HMS/DMS parsing/format
data class LatLong(val latitude: Double, val longitude: Double)
```

Conversions (pure functions, as in v1's `CoordinateManipulations`): RaDec ↔ geocentric unit
vector, rotation about an axis, angular separation. Degree/radian/hour constants.

**Carried over unchanged in behavior** (golden-tested against v1): geocentric conversions,
rotation-matrix construction, vector/matrix algebra.
**Dropped:** `MathUtils`, float `mod` variants, `gregorianDate` (kotlinx-datetime covers it),
`clockTimeFromHrs` (UI formatting concern).

## `:core:astronomy`

### Time

`kotlinx-datetime.Instant` is the time currency throughout.

```kotlin
fun Instant.julianDay(): Double          // exact: epochMillis/86_400_000.0 + 2440587.5
fun Instant.julianCenturiesJ2000(): Double
fun meanSiderealTimeDeg(time: Instant, longitudeDeg: Double): Double  // same approximation
                                                                      // as v1, golden-tested
```

Clocks port from v1's design (it was sound): `SkyClock` interface with `RealClock`,
`TimeTravelClock` (controllable rate), and `TransitioningClock` (the animated swoop between
times). All expose `Flow<Instant>` ticks at requested granularity in addition to instantaneous
reads, since v2 layers and ViewModels are Flow-driven.

### Ephemeris

```kotlin
interface Ephemeris {
    fun geocentricPosition(body: SolarSystemBody, time: Instant): RaDec
    fun phaseAngleDeg(body: SolarSystemBody, time: Instant): Double
    fun illuminatedFraction(body: SolarSystemBody, time: Instant): Double
    fun magnitude(body: SolarSystemBody, time: Instant): Double
    /** Max apparent angular speed, for layer re-submission thresholds (D13). */
    fun maxAngularVelocityDegPerDay(body: SolarSystemBody): Double
    val validRange: ClosedRange<Instant>   // KeplerianEphemeris: 1800–2050 (JPL approximation)
}
```

`KeplerianEphemeris` ports v1's implementation faithfully (JPL two-term elements per body,
iterative Kepler solver, Almanac p. D22 lunar theory, Van Flandern–style magnitude polynomials
— including the v1 shortcuts: fixed Saturn magnitude ignoring ring geometry, fixed Moon
magnitude), but in `Double` and with `validRange` made explicit instead of silent. Known v1
quirks preserved deliberately (they are the faithful-port baseline; golden tests pin them):
phase-angle-based lunar elongation approximation. v1's geocentric-Moon quirk is fixed (D54):
`topocentricPosition(body, time, observer)` applies the Almanac's diurnal-parallax correction
for the Moon (a default falls through to `geocentricPosition` for every other body, whose
parallax is below this model's accuracy), and observer-facing callers — the solar-system
layer, rise/set, search, tap-to-identify — use it. A future VSOP87/ELP2000 implementation
slots behind the same interface; rise/set calculation will be added to this module (not the
interface above — it's derivable: a root-find on altitude over `topocentricPosition`).

`SolarSystemBody` becomes a pure enum (Sun, Moon, Mercury…Neptune, Pluto — Pluto stays,
matching v1) with physics-only attributes. The v1 resource couplings move app-side:

- **Imagery:** an `:app` (or `:data`) mapping `SolarSystemBody → ImageRef`, including the
  lunar-phase image selection (8 phase buckets + waxing/waning disambiguation), computed from
  `phaseAngleDeg` — the *logic* stays in `:core:astronomy` as
  `lunarPhaseBucket(time): LunarPhase`, only the drawable lookup is app-side.
- **Names:** localized via the same mechanism as every other celestial object (data layer),
  not string resources baked into an enum.

Moon-phase event finding (v1's `getNextFullMoon` family, used by time-travel presets) becomes
`fun nextLunarPhaseEvent(phase: LunarPhase, after: Instant): Instant` — the analytic version,
not the hour-stepping loop.

### Sky model (replaces `AstronomerModelImpl`)

v1's three-frame math (celestial / phone / local, `T = axesCelestial × axesPhone⁻¹`) is
correct and ports as-is — but as a **pure function**, not a mutable object with cache
invalidation and sensor state:

```kotlin
object SkyModel {
    /** North/Up/East in celestial coords for this time/place (v1: calculateLocalNorthAndUp…) */
    fun localFrame(time: Instant, location: LatLong, magneticDeclinationDeg: Double): LocalFrame

    /** v1: calculatePointing(). orientation = phone→world rotation from the sensor edge. */
    fun pointing(frame: LocalFrame, orientation: Matrix3, mode: ViewDirectionMode): Pointing
}

data class Pointing(val lineOfSight: Vector3, val perpendicular: Vector3)
enum class ViewDirectionMode { STANDARD, ROTATE90, TELESCOPE }   // v1 feature parity
```

- The Android edge (`:app`) owns sensors: rotation-vector → matrix via `SensorManager`, the
  legacy accelerometer+magnetometer fusion (the vector-rejection construction ports from v1
  into a small pure helper here so it stays testable), damping/smoothing, and magnetic
  declination via `GeomagneticField` behind a `MagneticDeclinationSource` interface (with v1's
  zero-declination implementation for the preference toggle).
- v1's 60-second celestial-frame cache disappears: the ViewModel recomputes `LocalFrame` on a
  clock/location tick, which is the same throttling expressed as data flow instead of hidden
  mutable state (and is what makes fast time travel work correctly — v1 special-cased that
  with `forceUpdate`).
- Camera resolution per D14: `ReferenceFrame` (sensor/manual/fixed-point/tracking) resolves to
  a `Pointing` + FOV here, in pure code.
- **Time-travel view animation (G6):** the *time* swoop is the `TransitioningClock` above. The
  *view* swoop (v1 re-aims toward the target as time runs) is a `MapViewModel` concern, not a new
  renderer or clock concept: it animates the `ReferenceFrame` — interpolating pointing toward a
  transient `FixedSkyPoint`/`Tracking` frame — and the existing per-frame camera resolution does
  the rest. No renderer API change (consistent with D14).

## Testing

1. **Golden tests against v1:** port v1's `math/`, ephemeris, and `AstronomerModelImpl` unit
   tests; additionally generate a fixture table from v1 code (body × date grid of RA/Dec/mag/
   phase over 1900–2050) and assert v2 matches within tolerance (≤1 arcmin positions, ≤0.1
   mag) — this catches Float→Double and refactoring regressions at once.
2. **External-reference spot checks:** a handful of almanac values (planet positions, full-moon
   dates, sidereal time) at known epochs, with documented tolerances — guards against
   faithfully porting a v1 bug into the golden fixtures.
3. Property tests for the math types (rotation orthogonality, RaDec round-trips).

## Future extensibility: beyond the built-in solar system (forward-looking)

**Status: not yet built** — this records the evolution path so the slice-2 port isn't a dead end.
The built-in `SolarSystemBody` enum is a faithful port for the fixed base set (8 planets + Sun +
Moon + Pluto, with code-baked lunar theory and fixed Sun/Moon/Saturn magnitudes). It will *not*
accommodate an open or downloadable set of objects, and shouldn't be made to. See decision D26.

**The reframe — open *objects*, closed *models*.** The number of orbital *models* is small and
closed (sun-orbiting Keplerian, Earth-orbiting TLE/SGP4, planet-relative moons, the special
built-in lunar theory). The number of *objects* is open and partly data-driven. So the key the
ephemeris dispatches on should stop being a closed enum and become a `sealed Body` whose
data-carrying arms hold per-object parameters:

```kotlin
sealed interface Body { val id: String }
enum class SolarSystemBody : Body { Sun, Moon, Mercury, /* … */ Pluto }      // built-ins, baked in code
data class MinorBody(override val id: String, val elements: KeplerianElements) : Body   // dwarf planets, asteroids
data class Satellite(override val id: String, val tle: Tle) : Body                       // ISS, Starlink, …
data class PlanetaryMoon(override val id: String, val parent: SolarSystemBody, val theory: MoonTheory) : Body
```

You need an open set of *instances*, not of *types* — the `when` over the sealed model stays
exhaustive. The `Ephemeris` interface is already the correct seam and survives unchanged in spirit;
only its key (`SolarSystemBody` → `Body`) and a few globals move. Concretely:

- **Dwarf planets / asteroids (easy, mostly data).** The reusable kernel
  `heliocentricCoordinates(OrbitalElements)` already handles them; only the hardcoded table in
  `orbitalElementsFor(...)` is built-in. Caveat: planets use JPL *two-term linear fits*; minor
  bodies ship as *osculating elements at an epoch* propagated by mean motion — same kernel,
  different element *source* (a `KeplerianElements` type feeding the existing computed
  `OrbitalElements`).
- **Satellites (a genuinely new model).** TLE + SGP4/SDP4, Earth-centred. Forces three things that
  are currently interface-level globals to become **per-model**: `validRange` (a TLE is valid for
  ~days around its epoch, not 1800–2050), `maxAngularVelocityDegPerDay` (degrees per *minute* for
  LEO), and phase/magnitude (no planetary phase; brightness is sun-glint + Earth-shadow eclipse, so
  those methods become model-specific or no-ops).
- **Galilean (and other) moons.** Position = parent's geocentric position **+** the moon's offset
  from its parent, then convert. The structural requirement they surface is a **parent reference**
  in the model. Phase/illumination relative to the Sun stay meaningful; `maxAngularVelocity` is
  dominated by fast libration around the parent (Io's period ≈ 1.8 d).

**Base set + downloadable content.** `:core:astronomy` stays pure: it defines `Body`/model types
and the math, and computes for any `Body` regardless of origin. A `BodyRepository` in `:data`
supplies the active set = built-ins ∪ downloaded packs (reusing D24's transactional pack model and
the data-layer download strategy). The compute layer never knows the source.

**`illuminatedFraction` is general, not Kepler-specific.** `(1 + cos(phaseAngle)) / 2` is the
geometric lit-fraction of any sphere; it only incidentally lives as a `KeplerianEphemeris` override
today. It belongs as an `Ephemeris` interface default (or a free function on `phaseAngleDeg`) so all
models share it. What *is* model-specific is `phaseAngleDeg` itself (the Sun–body–Earth geometry,
incl. the Moon's elongation approximation).

**Timing.** All additive and low-cost: the standalone `Ephemeris` interface, the isolated
`heliocentricCoordinates` kernel, and the separate `OrbitalElements` type are the seams that make
it so. The enum has no callers outside `:core:astronomy` yet, so the enum→`Body` change stays
contained. Deferred until an actual feature (more bodies, satellites, or DLC) needs it.

## Known accuracy issues to revisit (faithful-port debt)

The slice-2 port deliberately preserves v1's low-precision behavior as the golden-tested baseline.
That baseline carries genuine astronomy/numerical errors that are *intentionally* kept for now
(so the golden tests pin v1) but should be fixed when accuracy work begins — most are subsumed by
the planned VSOP87/ELP2000 upgrade behind the same `Ephemeris` interface. Catalogued here so they
aren't forgotten:

| # | Issue | Where | Effect | Fix when revisited |
|---|---|---|---|---|
| 1 | **Lunar phase frame mismatch.** The anti-solar vector is left in ecliptic coordinates and dotted against the equatorial Moon vector (v1's `calculatePhaseAngle`). | `KeplerianEphemeris.lunarPhaseAngleDeg` | Phase-angle error up to the obliquity (~23.4°), feeding into `illuminatedFraction` and `lunarPhaseBucket`. | Rotate the anti-solar vector with `toEquatorialCoordinates` before dotting (or compute true elongation from ecliptic longitudes). |
| 2 | **Kepler true-anomaly singularity.** `2·atan(√((1+e)/(1−e))·tan(E/2))` blows up near `E = π` (aphelion). | `OrbitalElements.trueAnomaly` | Precision loss / potential overflow near aphelion; benign for the current low-`e` bodies but unsafe for high-`e` minor bodies (see extensibility section). | Use the numerically stable half-angle form: `v = atan2(√(1−e²)·sin E, cos E − e)`. |
| 3 | ~~**Geocentric, not topocentric, Moon.** Observer position on Earth is ignored.~~ **Fixed (D54)**: `Ephemeris.topocentricPosition` applies the Almanac p. D22 horizontal-parallax correction from the observer `LatLong`; all observer-facing callers use it. | `KeplerianEphemeris.topocentricLunarPosition` | Was up to ~1° lunar-position error (lunar parallax). | — |
| 4 | **Low-order lunar theory.** Only the largest Almanac (2008, p. D22) terms are kept. | `KeplerianEphemeris.lunarPosition` | Coarse lunar position/phase. | ELP2000. |
| 5 | **Fixed magnitudes.** Saturn ignores ring geometry; Sun/Moon are constants. | `KeplerianEphemeris.magnitude` | Saturn magnitude wrong by up to ~1; Sun/Moon not phase-dependent. | Ring-aware Saturn model; phase-dependent lunar magnitude. |

Items 1–2 were raised during review and confirmed real; they are held only to keep the v1 golden
baseline intact for this pass (see D25, D27).
