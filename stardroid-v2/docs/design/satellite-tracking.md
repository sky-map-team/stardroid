# Satellite Tracking: TLEs, SGP4, and Visible Passes

**Status: ACCEPTED** (D92). **Phases 1–2 implemented** (D94,
D95) — `Tle.kt`, `Sgp4.kt`, `SatelliteEphemeris.kt`, `SatellitePass.kt` and
`SatelliteMagnitude.kt` are in `:core:astronomy`, validated against Vallado's vectors and, for the
frame chain and the pass search, against Skyfield. **Phase 3 is nearly done**
(D96, D97) — client, cache, circuit breaker, the WorkManager
job, `Experiment.SATELLITES`, the consent setting and the analytics events have all landed; only
the debug force-fetch UI remains. The feature ships **off** behind its experiment flag.
**Phase 4 is done** (D99–D103): map layer, freshness badge, empty state, pass surfaces and pass
alerts. **Phase 5 (the ~100-brightest fleet) is not started.**

Executes the satellite arm of the extensibility path sketched in
[core-math-astronomy.md](core-math-astronomy.md) under "Future extensibility" and recorded as
deferred in D26. That sketch named TLE/SGP4 as a future orbital model and
pre-identified the three frictions it forces; this document turns the sketch into a decided
design. v1 never tracked satellites, so there is no port to be faithful to.

### Consequences of D84, reviewed

D84 landed after this design was first drafted and touches exactly the
frame-chain ground this document stands on. Two follow-ons, both incorporated below:

- **`MeeusEphemeris` is now the app-wide ephemeris**, not `KeplerianEphemeris` — the shadow test
  and magnitude phase angle (§"Visibility", §"Magnitude") get the Sun from `MeeusEphemeris`, not
  `KeplerianEphemeris` as originally written. This is a net simplification: D84 already rotates
  `MeeusEphemeris`'s result to J2000 before it leaves the ephemeris, so the Sun position this
  feature consumes is J2000-native rather than of-date, matching the map layer's frame.
- **`Precession.kt` now exists** (`Precession.dateFromJ2000`/`j2000FromDate`, IAU 1976 Lieske
  angles) and is exactly the rotation §"Frame chain" step 3 originally proposed building from
  scratch. Reuse it — see the updated step 3 below — rather than re-deriving the same angles a
  second time.
- One new frame-consistency requirement falls out of the Sun now arriving in J2000: the shadow
  test and magnitude phase angle need the satellite's geocentric position and the Sun's direction
  in the *same* frame, and TEME (what SGP4 emits, and what step 1's WGS84 observer is built in)
  is an of-date frame. `Precession.dateFromJ2000(time)` rotates the J2000 Sun into that frame
  before the dot product — one call, and it is the same "rotate the of-date quantity inward"
  pattern D84 established rather than a new idea.

No other change in the interim affects this design. D86's `angularDiameterDeg` and D88's
`Terminator` extend `Ephemeris`/`ImagePrimitive`; `SatelliteEphemeris` deliberately implements
neither (see below), so both pass through untouched. D91's per-layer parameter wiring is relevant
and addressed in §"Settings".

## Glossary

This area carries more unexpanded acronyms than the rest of the codebase, because they are what
the source literature uses and renaming them would make the code harder to check against the
papers, not easier. They are expanded once here instead.

| Term | Expansion | What it means here |
|---|---|---|
| **TLE** | Two-Line Element set | The distribution format for orbital elements: two 69-character fixed-column lines, optionally preceded by a name line ("3LE"). |
| **NORAD** | North American Aerospace Defense Command | Origin of the catalog numbering; the "NORAD id" is the stable per-object identifier. |
| **GP** | General Perturbations | CelesTrak's name for its element-set endpoint (`gp.php`) — elements meant for an analytic propagator rather than numerical integration. |
| **SGP4** | Simplified General Perturbations 4 | The near-earth analytic propagator this design implements. Inseparable from the TLE: the elements mean nothing except as input to it. |
| **SDP4** | Simplified Deep Space Perturbations 4 | The deep-space companion (period ≥ 225 min), adding lunar-solar periodic and resonance terms. **Out of scope** — see §"Scope". |
| **B\*** | "B-star" drag term | SGP4's drag parameter, in inverse Earth radii. A fit parameter, not a physical ballistic coefficient, and legitimately negative sometimes. |
| **LEO** | Low Earth Orbit | Roughly below 2000 km; where everything this feature tracks lives. |
| **TEME** | True Equator, Mean Equinox | The frame SGP4 emits positions in — **not** J2000 and **not** GCRF. Its defining property here: GMST is exactly the angle relating it to the Earth-fixed frame. |
| **ECI** | Earth-Centred Inertial | The general class of non-rotating Earth-centred frames; TEME and J2000 are both ECI frames. |
| **ECEF** | Earth-Centred, Earth-Fixed | Earth-centred frame that rotates *with* the Earth, so a ground observer is stationary in it. |
| **GCRF** | Geocentric Celestial Reference Frame | The modern IAU inertial standard. Named only to say TEME is not it. |
| **J2000** | The epoch 2000-01-01 12:00 TT | The frame the app draws in: the star catalog, `Precession`, and the map layer all use it. |
| **GMST / LMST** | Greenwich / Local Mean Sidereal Time | The Earth's rotation angle, at Greenwich or at the observer's meridian. `meanSiderealTimeDeg` supplies it. |
| **WGS72 / WGS84** | World Geodetic System 1972 / 1984 | Earth shape models. SGP4 is fitted against **WGS72** and must keep it; the observer's position uses **WGS84**, ordinary modern geodesy. Not an inconsistency — see D94. |
| **RAAN** | Right Ascension of the Ascending Node | Where the orbit crosses the equator going north; one of the six orbital elements. |
| **AOS / LOS** | Acquisition / Loss Of Signal | Borrowed from tracking-station usage: when the satellite rises above and sets below the horizon during a pass. |
| **J2 / J3 / J4** | Zonal harmonic coefficients | Terms describing the Earth's departure from a sphere; what makes orbits precess, and most of what SGP4 models. |

## Motivation

Every other object Sky Map draws is either fixed (stars, deep-sky objects) or computable from
coefficients baked into the app (planets, the Moon). Satellites are neither. Their orbits decay
unpredictably — atmospheric drag at 400 km varies with solar activity — so their orbital elements
must be re-measured and redistributed continuously.

The practical consequence, and the constraint that shapes this entire design:

| TLE age | ISS positional error | Usable for |
|---|---|---|
| < 1 day | ~1 km | everything |
| 3 days | ~2 km | everything |
| 10 days | tens of km | rough position only |
| 1 month | hundreds of km | nothing |

A star catalog ships in the APK and is correct for a century. A satellite element set shipped in
the APK is wrong by the time a slow updater installs it. **Satellites are necessarily a connected
feature**, and the design question is how to be connected without running a server.

### The feature that matters

Two surfaces are possible, and they are not equally valuable:

- **The map layer** — draw the ISS in its true sky position, moving in real time. Pleasant, and
  the obvious thing to build.
- **The visible-pass predictor** — *"Next visible pass tonight at 21:47, rising NW, reaching 68°,
  magnitude −3.1, fading into Earth's shadow at 21:51."*

The pass predictor is the higher-value half, and the phasing below reflects that. The ISS is only
interesting when it is *visible*, which requires a narrow simultaneous condition — the satellite
in sunlight while the observer is in darkness — occupying a couple of hours after dusk and before
dawn, in multi-day seasons separated by multi-week gaps. A map layer answers "where is it now?",
which is usually "below the horizon, in daylight, invisible". The pass predictor answers the
question people actually have.

## Data source

**CelesTrak** (Dr T.S. Kelso), the public front door for US Space Force orbital data since the
1980s and the source essentially every amateur tracker uses.

```
https://celestrak.org/NORAD/elements/gp.php?GROUP=stations&FORMAT=tle
```

`GROUP=stations` is ~1 KB (ISS, Tiangong, station-adjacent objects). `GROUP=visual` is the ~100
brightest, ~50 KB. `FORMAT` accepts `tle`/`3le`/`2le`/`xml`/`kvn`/`json`/`csv`.

### Usage policy — binding constraints

Verified against `celestrak.org/usage-policy.php`. These are hard requirements, not guidance:

- **Minimum GP re-query interval: 2 hours.** Our 12 h refresh is comfortably compliant and matches
  the real update cadence (element sets are re-issued roughly daily).
- **Clients must stop querying on any non-200 response and report to a human.** The stated
  consequence is blunt: *"Repeatedly ignoring them will end up sending your IP address to the
  firewall."* 301 means an outdated URL, 404 an old URL format, 50x server overload.
- **Download only what you need, when you need it, once per update.**

No User-Agent, mobile, or commercial restrictions are stated.

The stop-on-non-200 rule is the most important input to this design. At scale, the entire install
base looks like a single client to CelesTrak. A retry bug shipped to production could get Sky
Map's aggregate traffic firewalled — breaking the feature for every user, with no client-side fix
and no recourse but a new release. The circuit breaker below is deliberately over-engineered for
this reason.

### Rejected alternatives

| Source | Why not |
|---|---|
| **Space-Track.org** | The authoritative USSPACECOM source, but requires per-user account credentials and its terms forbid redistribution. Either every user registers an account, or we host a credential — which is the server we are avoiding. This is precisely why CelesTrak exists. |
| **N2YO** | Requires an API key. Shipping it in the APK makes it extractable and rate-limited per key, so one bad actor breaks the feature for everyone; hosting a proxy is again the server we are avoiding. Also returns *predictions* rather than elements, making every query network-dependent instead of one fetch per 12 h. |
| **Heavens-Above** | No public API at all. |
| **tle.ivanstanojevic.me** | Free and keyless, but is itself a CelesTrak mirror — useful as a transient fallback, but not independent failure-domain insurance. Not worth the second code path at launch. |

CelesTrak is therefore a genuine single point of failure. That risk is accepted explicitly, and
mitigated by the kill switch under "Risks" rather than by a second source.

## Module placement

**SGP4 goes in the existing `:core:astronomy`.** Not a new `:core:satellites`.

It is a peer orbital model to `KeplerianEphemeris.kt` and `OrbitalElements.kt` — exactly the
"small closed set of orbital *models*" framing D26 uses. The pass predictor needs `altitudeDeg`
and `azimuthDeg` from `RiseSet.kt`, and the sunlit test needs the Sun from `MeeusEphemeris` (the
app-wide ephemeris since D84), so a separate module would need `api(project(":core:astronomy"))`
and gain nothing. Purity is
satisfied trivially: SGP4 is arithmetic over doubles, and `:core:astronomy` already carries no
Android dependency.

The cost side is concrete. `konsist/src/test/kotlin/.../ArchitectureTest.kt` hardcodes the pure
modules **twice** — once in a path regex, once in an allow-list of package prefixes — so a new
pure module means editing both, plus `settings.gradle.kts` and a new `build.gradle.kts`, for no
benefit.

A useful signal that the placement is right: this design touches **no** `build.gradle.kts`, no
`settings.gradle.kts`, no `libs.versions.toml`, and no Konsist file.

### Wire format: TLE text, not `FORMAT=json`

CelesTrak's `FORMAT` parameter accepts `json`, which would remove the need for `Tle.kt`'s
line-oriented parser and checksum validation. Rejected anyway, for three reasons: the Vallado
verification vectors and every reference SGP4 implementation are written against TLE text, so
parsing TLE text is what the verification strategy below actually exercises; the two-line format
is what "cache the raw text verbatim" (§"Scheduling and cache") caches, so JSON would mean a
second serialization the cache never uses; and `:data` — where the CelesTrak client lives — has
no JSON dependency on its runtime classpath today (`kotlinx-serialization-json` is currently
`:data:generator`-only, a build-time tool, per `build-and-tooling.md`), so `FORMAT=json` would
add a runtime dependency this design otherwise avoids for no offsetting benefit. The line above
about touching no `libs.versions.toml` would stop being true.

New files, all in `core/astronomy/src/main/kotlin/com/google/android/stardroid/astronomy/`:

| File | Contents |
|---|---|
| `Tle.kt` | `data class Tle` (NORAD id, name, epoch, inclination, RAAN, eccentricity, argument of perigee, mean anomaly, mean motion, B\*, derivatives) and the parser — including mod-10 checksum validation and the two-digit-year epoch pivot (57–99 → 19xx, 00–56 → 20xx). |
| `Sgp4.kt` | The propagator: one-time initialization from a `Tle`, then `propagate(minutesSinceEpoch)`. |
| `SatelliteEphemeris.kt` | The frame chain — TEME → topocentric, WGS84 observer, precession to J2000. |
| `SatellitePass.kt` | Pass search and the `SatellitePass` result type. |
| `SatelliteMagnitude.kt` | Brightness model and the Earth-shadow test. |

### `Ephemeris` and `SolarSystemBody` stay untouched

Every member of the `Ephemeris` interface is the wrong shape for an object 400 km up:
`earthDistanceAu` measures in AU, `illuminatedFraction` assumes planetary phase, `validRange` is a
1800–2050 global, and `maxAngularVelocityDegPerDay` is degrees per *day* for a body that moves
4° per *minute*. `SolarSystemBody` is a closed enum that a satellite cannot join.

D26 anticipates all of this and proposes a `sealed Body` migration. **That migration stays
deferred.** `SatelliteEphemeris` is a standalone type with its own signature; it does not
implement `Ephemeris`. This keeps the feature additive and leaves D26's broader reframe to be
driven by whichever feature genuinely needs the unified key — probably minor bodies, which fit the
existing Keplerian kernel and would actually benefit.

### Coordinate types

`:core:math` has unit-vector `Vector3` and angle-only `RaDec`; satellites need range as well
(topocentric parallax, magnitude, shadow geometry all depend on it).

**Do not add a distance-carrying type to `:core:math`.** `Vector3` is already a plain `(x, y, z)`
triple with `length`, `minus`, and `normalized()`, unit-ness being a convention rather than an
invariant — and `RaDec.fromGeocentricVector`'s KDoc already states its input "need not be a unit
vector", which `KeplerianEphemeris.topocentricLunarPosition` relies on today.

Define instead, in `:core:astronomy`, with units in the property names:

```kotlin
data class TemeState(val positionKm: Vector3, val velocityKmPerSec: Vector3)
data class TopocentricPosition(val raDec: RaDec, val rangeKm: Double, val rangeRateKmPerSec: Double)
```

If a second consumer ever needs a general distance-carrying vector, promote it then.

## Scope: SGP4 only; deep-space element sets are rejected

The near-earth/deep-space split is orbital period ≥ 225 minutes. Every target in scope is
near-earth: the ISS is ~93 minutes, and the `visual` group is essentially all LEO.

Omitting SDP4 — the deep-space path, with lunar-solar periodic terms and 12 h/24 h resonance
handling — costs geostationary and Molniya orbits. Those are not visible-pass targets:
geostationary satellites are magnitude 11+ and, by definition, do not move against the sky, which
is the opposite of what makes this feature appealing.

The important part is the failure mode. **Running SGP4 on a deep-space element set does not
degrade gracefully; it produces grossly wrong positions.** The design therefore requires an
explicit guard —

```kotlin
val isDeepSpace: Boolean get() = TWO_PI / meanMotionRadPerMin >= 225.0
```

— with construction refused for deep-space TLEs and the repository filtering them at parse time.
One line converts a silent-wrong-answer bug class into a clean, testable absence.

## Frame chain

SGP4 emits positions in **TEME** (True Equator, Mean Equinox) — an idiosyncratic frame that is
neither J2000 nor GCRF. Getting from there to something the app can draw is where most third-party
SGP4 ports go wrong, so the error budget is stated explicitly.

### 1. Observer position — WGS84, not a sphere

`KeplerianEphemeris.topocentricLunarPosition` (around line 222) builds the observer's geocentric
offset on a **spherical** Earth, at exactly one Earth radius:

```kotlin
val observerFromGeocentre = Vector3(cos(latRad) * cos(lmstRad), cos(latRad) * sin(lmstRad), sin(latRad))
```

For the Moon at 60 Earth radii, sphere-versus-ellipsoid costs arcseconds. **For the ISS at ~1.07
Earth radii it costs 1–3°** — the ~21 km difference between equatorial and polar radii is a large
fraction of a 400 km range. This is the single most important correctness detail in the feature,
and the lunar pattern must **not** be copied verbatim.

Use the WGS84 ellipsoid (`a = 6378.137 km`, `f = 1/298.257223563`), geodetic → ECEF via the
standard `C = 1/√(1 − e² sin²φ)` formulation, then rotate by GMST + longitude into TEME.

Two notes: `LatLong` carries no altitude, so sea level is assumed — worth ~0.1° for an observer at
1 km elevation, acceptable and documented. And the rotation should reuse `meanSiderealTimeDeg`
from `Time.kt` rather than introducing a second GMST; its low-precision linear formula errs by
~0.0004° here, which is irrelevant. GMST is precisely the angle relating TEME to the Earth-fixed
frame — that is what TEME is *for* — so using it here is correct rather than approximate.

### 2. Topocentric vector

`satelliteTeme − observerTeme`, both in TEME. Because the frames match, the resulting **altitude
and azimuth are correct to arcseconds** without any further conversion. This is all the pass
predictor needs, and alt/az is what a pass prediction is expressed in.

### 3. Precession to J2000, for the map layer only

The map layer needs RA/Dec consistent with a J2000 catalog. TEME differs from J2000 by precession
since J2000 — **~0.36° by 2026**, more than half a Moon-width, so this is not optional — plus
nutation and the equation of the equinoxes, together under 0.02°.

Reuse `Precession.j2000FromDate(time)` — landed by D84 with exactly these IAU-76 (Lieske) angles,
so this step is a rotation call, not new arithmetic. Nutation and the equinox equation stay
omitted, both far below the layer's resubmit threshold and below label decluttering scale;
D84 documents the same omission for its own callers, so the precedent is established.

### Time

`Time.kt` treats instants as UTC, and SGP4 wants UTC for both the epoch and minutes-since-epoch,
so the two are self-consistent. **Do not introduce ΔT.**

## Pass prediction

### Search

```
coarse scan at 30 s steps over the horizon window:
    altitude(t) via the chain above
    on upward zero crossing   -> bisect [t-30s, t] for AOS
    on downward zero crossing -> bisect [t-30s, t] for LOS
    within a pass, track max altitude; refine the peak by parabolic fit
```

**Step size.** Near the horizon — where AOS and LOS live — the ISS climbs at ~0.15–0.3°/s, and a
pass lasts 4–10 minutes. A 30 s step therefore guarantees 8–20 samples inside any real pass; none
can be stepped over. The only thing a coarser scan misses is a marginal sub-degree pass, which the
minimum-elevation filter discards anyway.

**Bisection.** Six iterations on a 30 s bracket converges to ~0.5 s, ample when the displayed
precision is a minute.

**Horizon: 48 hours, parameterized.** This answers "tonight and tomorrow" honestly and correctly
returns *empty* during the multi-week gaps between ISS pass seasons — which is a useful answer, not
a failure. A longer horizon lets the UI say "next pass Thursday" at roughly linear cost; the
parameter exists so a "more passes" screen can ask for it. Beyond ~5 days TLE drift makes
predicted times uncertain by tens of seconds, which is a reason not to promise a fortnight.

**Cost.** 48 h ÷ 30 s = 5760 propagations per satellite, at ~2–5 µs each ≈ 25 ms. Trivial for the
ISS alone; ~2.5 s for a 100-satellite fleet, which wants a coarse 5-minute pre-filter (fine-scan
only windows approaching the horizon, ~5× saving) and `Dispatchers.Default` regardless.

### Reuse and one anti-pattern

`altitudeDeg` and `azimuthDeg` from `RiseSet.kt` are used directly. `:core:events`
`TonightSky.planetEvents()` — a coarse sample over a window, tracking first-qualifying and best
altitude — is the existing precedent this scales up, not a new pattern.

But **`RiseSet.nextRiseSetTime` must not be used.** Its hour-angle iteration
(`CONVERGENCE_HOURS = 0.008`, `MAX_ITERATIONS = 25`, day offsets 0..2) is tuned for bodies moving
≤15° per *day*; it will not converge for one moving 4° per *minute*. The scan-and-bisect above
exists precisely because the general solver does not generalize this far.

### Visibility — three simultaneous conditions

A pass is *visible*, rather than merely geometric, when all three hold:

1. **Satellite above 10° elevation.** Below that, buildings and horizon haze make it a non-event.
2. **Observer in darkness** — Sun altitude below −6° (civil twilight), the conventional cut.
   `RiseSet.kt` defines `CIVIL_TWILIGHT_SUN_ALT_DEG = -6.0`; reuse that constant so the two
   surfaces cannot drift apart. (It was `TonightSky`'s and named `SKY_DARK_SUN_ALT_DEG` when
   this was written — see D95 for why it moved and was renamed.)
3. **Satellite in sunlight.** Cylindrical shadow test: with `r` the satellite's geocentric
   position and `s` the unit vector to the Sun, it is lit if `r · s > 0`; otherwise lit iff
   `|r − (r·s)s| > R⊕ + 30 km`. The 30 km term stands in for atmospheric refraction and
   penumbra — a bare cylinder overestimates lit time by 10–20 s at shadow entry.

   `r` is the satellite in TEME (SGP4's native output, undisturbed). `MeeusEphemeris` gives the
   Sun in J2000 (D84), so `s` must be `Precession.dateFromJ2000(time)` applied to that result
   before the dot product — otherwise the test carries a ~0.36° frame mismatch between the two
   vectors, which is exactly the class of error D84 exists to close. Both `r` and `s` end up in
   the same of-date frame; nothing is precessed to J2000 here, matching step 3's "only the map
   layer needs J2000" scope.

**Shadow entry inside a pass is worth capturing as a first-class field.** A satellite that fades
out mid-sky is the single most-asked question after someone watches it happen, and
*"fades into Earth's shadow at 21:51"* converts a confusing disappearance into the most memorable
thing the app can tell them.

### Magnitude

```
mag = stdMag − 15.75 + 2.5 · log10(rangeKm² / phaseFactor)
phaseFactor = (1 + cos(phaseAngle)) / 2
```

where `stdMag` is the satellite's brightness at 1000 km and 90° phase (ISS: about −1.3) and the
phase angle is measured at the satellite between Sun and observer, using the same
frame-consistent Sun direction as the shadow test above. This yields roughly −3.5 for a high ISS
pass, matching observation.

Accuracy is ±0.5 magnitude — real satellites are not Lambertian spheres, and the ISS's solar
arrays swing its brightness by a magnitude either way as they rotate. **Display one decimal place
and imply no more.** For ISS-only, hardcode `stdMag` with its citation; a fleet needs a small
bundled table (a few KB, no concern against D19's budget).

Note that `(1 + cos θ)/2` is the same general identity `core-math-astronomy.md` already flags as
misplaced in `KeplerianEphemeris`. Implementing it here gives it a second call site, strengthening
the case for lifting it to an `Ephemeris` default — but that refactor is not this feature's job.

### Result type

```kotlin
data class SatellitePass(
    val satelliteName: String,
    val noradId: Int,
    val start: Instant,
    val culmination: Instant,
    val end: Instant,
    val maxAltitudeDeg: Double,
    val startAzimuthDeg: Double,
    val endAzimuthDeg: Double,
    val peakMagnitude: Double,
    val shadowEntry: Instant?,
)
```

Azimuth stays numeric. Mapping 315° to "NW" needs localized strings and is presentation, so it
belongs app-side — the precedent `TonightSky` already sets by returning raw `azimuthDeg`.

## Networking

### Client: `java.net.HttpURLConnection`

The entire requirement is one GET, ~1 KB of text, a timeout, and a status code. That is ~30 lines
against the JDK.

**No HTTP client dependency is added.** This extends the stance D79 already
took for data packs — "no new networking dependencies" — and the empty HTTP section of
`libs.versions.toml` is a deliberate position rather than an oversight. Fewer dependencies also
suits F-Droid reproducibility.

Note that D79's own choice, the system `DownloadManager`, is the wrong tool here: it is built for
large resumable file downloads, not a 1 KB text fetch. Same principle, different mechanism.

The one genuine advantage a library would bring — automatic retry with backoff — is something this
feature must specifically **not** have, given CelesTrak's policy. Manual control is a feature.

**Reconsidered, since a dependency is on the table if it earns its place**: nothing else a client
library offers (connection pooling, HTTP/2, interceptors, `MockWebServer`-backed tests) pays for
itself against one ~1 KB GET every 12 h. There is no OkHttp/Retrofit already in the dependency
graph to piggyback on — adding one would be net-new, purely for this feature. Testability doesn't
need it either: the client is small enough to sit behind a one-method interface the circuit
breaker can fake in tests without a real socket. If a second, higher-volume network use case
shows up later (the deferred data-packs work in D79/`design/data-packs.md` is the likely
candidate), that is the point to revisit a shared client — not this single GET.

The client needs no flavor split: plain HTTPS, no Google dependency, so it lives in `main`. It
belongs in `:data` alongside the other repositories, which already depends on `:core:astronomy`.

### The circuit breaker

This is the part that protects the install base, and it inverts normal retry instincts.

- **Distinguish transport failure from server refusal.** No connectivity, DNS failure, or timeout
  never reached CelesTrak, and is a legitimate `Result.retry()`. Any non-200 *response* is a policy
  event and must never be retried on the normal schedule.
- **On any non-200**, increment a failure counter and set `circuitOpenUntil = now + backoff`,
  where backoff runs **24 h → 48 h → 7 days**, capped. Far more conservative than typical retry
  policy, deliberately.
- **The worker returns `Result.success()`, never `Result.retry()`, on an HTTP error.** WorkManager's
  retry machinery must never be allowed to drive requests at CelesTrak — that is exactly the storm
  the policy forbids. The 12 h periodic job fires again on schedule and no-ops against the breaker.
- **301 is terminal.** The policy says it means the URL is outdated, which no amount of retrying
  fixes. Open the breaker for the full 7 days and log loudly.
- **304 is not an error.** Send `If-Modified-Since` from the last success; handle Not-Modified as a
  success that refreshes timestamps without touching the cache.
- **Send a descriptive User-Agent** naming the app with a contact URL. CelesTrak's operator has
  historically preferred to make contact before firewalling; an anonymous UA forecloses that.
- **Report to a human**, as the policy requires: surface last-fetch status and code in the app's
  diagnostics surface so a user reporting "satellites don't update" can hand us the code, plus a
  gms-only analytics event behind the existing consent flag (`AnalyticsEvents`, alongside
  `diagnostics_opened` and the other menu/error events already logged there).

  **The analytics event, concretely** — this is the aggregate signal that stands in for the
  "report to a human" the policy demands, since we cannot see individual devices' logs: a
  `satellite_fetch_failed` event on every non-200/301 (params: HTTP code, whether it opened or
  extended the circuit breaker, and the new `circuitOpenUntil`), and a `satellite_circuit_closed`
  event the next time a fetch succeeds after a breaker was open (param: how long it was open).
  Both are cheap, both are gms-only same as the rest of the app's analytics, and together they are
  what would tell us — before CelesTrak firewalls anyone — that a retry bug is loose in
  production, which is the entire reason the circuit breaker exists.

### Forcing a fetch — debug builds only

Testing the network path otherwise means waiting up to 12 hours, or clock-fiddling. So there is a
**force-fetch affordance that bypasses the breaker, the 12-hour interval and the jitter** — and
because that is precisely the behaviour the rest of this section exists to prevent, its
constraints matter more than its convenience.

- **Debug builds only, gated on `BuildConfig.DEBUG`.** A discoverable "fetch now" in release is
  the retry storm the circuit breaker is built to stop: at scale it converts curiosity into
  request volume against a source that firewalls by IP. This must not be a hidden release
  affordance, a long-press easter egg, or anything reachable by an ordinary user — a build-type
  gate, so it cannot ship by accident.
- **It is the codebase's first `BuildConfig` use.** Nothing in `app/src/main` references
  `BuildConfig` today and `buildConfig` is not enabled in the convention plugin, so this needs
  `buildConfig = true` in `skymap.android-application`. Small, but it is new build configuration
  rather than a free win, and it is the reason this is called out rather than assumed.
- **Distinct from the user-facing Refresh.** The empty state's Refresh button (§"Staleness and
  offline behaviour") **respects** the breaker and the minimum interval; this one does not. Two
  different affordances with two different contracts — the design should not let them share an
  implementation, or the safe one will inherit the unsafe one's behaviour in some later edit.
- **Lives in Diagnostics**, beside the last-fetch status and code the circuit breaker already
  surfaces there, so the trigger and its result are in one place.
- **Logs loudly on every use**, and the KDoc should say plainly that CelesTrak's 2-hour minimum
  applies to the developer's IP as much as to a user's: someone hammering this can get the office
  address firewalled, which breaks testing for everyone.
- **Most testing should not need it.** Phases 1–2 are driven by frozen TLE fixtures and are fully
  testable in CI without a network; this exists to exercise the *client, cache and breaker*
  specifically, which is a narrower job than it first appears.

### Avoiding a thundering herd

- A fixed 12 h period is fine on its own — installs are already scattered by install time, and
  WorkManager's batching and Doze add jitter.
- **Add 0–60 minutes of random initial delay** on first schedule. A staged Play Store rollout
  reaching millions of devices in a coordinated window is a real herd source.
- **Never fetch on app open.** This is the most important of the three: it converts user behavior,
  which is strongly diurnally correlated across a timezone, directly into request volume.
- **Fetch only what is needed.** `GROUP=stations` (~1 KB) for ISS-only; `GROUP=visual` (~50 KB)
  only when the fleet ships. This is the policy's "only download the data you need" verbatim.

### Scheduling and cache

Mirror `widget/WidgetScheduler.kt` — `enqueueUniquePeriodicWork`, `ExistingPeriodicWorkPolicy.KEEP`,
a 12 h period — with two deliberate departures: add `NetworkType.CONNECTED` constraints (nothing
in the repo currently sets constraints, and this job must, so it waits rather than fails), and
schedule only while the satellite layer is enabled.

Cache **the raw TLE text, verbatim**. It is already compact and line-oriented, so re-serializing
to JSON or Room gains nothing; and storing bytes-as-received means the network and cache paths
exercise one parser rather than two.

Store under `filesDir/satellites/`, **not `cacheDir`** — the OS may evict `cacheDir` at any time,
and losing the TLE silently kills the feature offline. Write atomically (temp file, then rename)
so a kill mid-write cannot leave a truncated element set.

## Staleness and offline behaviour

TLE degradation is smooth and well characterised, so the UX can be honest rather than binary:

| TLE age | Error | Behaviour |
|---|---|---|
| < 3 days | < ~2 km | Normal. No UI mention. |
| 3–10 days | ~2–15 km | Show passes, with a quiet "orbit data N days old" note on the card. |
| > 10 days | tens of km, growing | Keep the map layer (roughly right, still pleasant), but **suppress precise pass times**. A confidently wrong "21:47" is worse than admitting the data is stale. |
| none | — | Actionable empty state: "Satellite positions need an internet connection to update." with a Refresh button, which still respects the breaker. |

The 10-day bound belongs on the model as its `validRange` — per-model rather than an interface
global, which is exactly the migration D26 anticipated satellites would force.

### No bundled fallback TLE

Tempting, and wrong. A TLE baked in at build time is stale by the release-to-install latency,
which for slow updaters is *months*. A months-old ISS element set draws the station visibly in the
wrong part of the sky and predicts passes that never happen — worse than showing nothing, harder to
test, and the kind of confidently-wrong behaviour that earns one-star reviews. The empty state is
honest and this is understood to be a connected feature.

## Settings

Three distinct gates, not two — a remote kill switch was added on review, and it sits above the
other pair rather than replacing either of them:

0. **`Experiment.SATELLITES` (`satellites_enabled`)** — the remote kill switch, following the
   `Experiment`/`ExperimentConfig` pattern D79/D80 already established
   (`startup/ExperimentConfig.kt`). This is the primary gate: nothing below is reachable unless
   it is on. **Ships off for both flavors** — `false` in `ExperimentConfig.Static` (fdroid) and
   in `remote_config_defaults.xml` (gms). Unlike the D75 widget flags this design started from,
   which default true as staged-rollout levers over already-decided features, satellite tracking
   is new and CelesTrak-dependent enough that off-everywhere is the honest shipping posture,
   same as D80's camera-AR and share-sky launch.

   The two flavors then diverge in *how* they get turned on, and the asymmetry is deliberate:

   - **gms is enabled remotely**, via Remote Config, per D80's staged-rollout pattern. No release
     needed, and it can be rolled back the same way if CelesTrak traffic looks wrong.
   - **fdroid is enabled by a binary release** that flips the `Static` default, once the feature
     has proven safe in the field on gms. There is no remote lever on fdroid — that is precisely
     why gms goes first: it is the flavor where a mistake is recoverable without shipping.

   This ordering is the mitigation for the single-point-of-failure risk below, not merely a
   convenience: the unprotected flavor only turns on after the protected one has demonstrated the
   circuit breaker holds in production.
1. **Layer visibility** comes free, same as before. `LayerId("computed/satellites")` auto-keys
   `layer_visible/computed/satellites` through the existing `DataStoreSettings` scheme, and adding
   the id to `TOGGLEABLE_IDS` surfaces a toggle with no new settings code — but the toggle itself
   is only reachable once gate 0 is on; `LayerRegistry` should not register the layer at all
   otherwise, the same way `CAMERA_AR`-gated surfaces are absent rather than disabled.
2. **`satelliteDataEnabled`** — consent to make network requests, gating the WorkManager job. Not
   the same thing as wanting to see the layer, and still meaningful underneath gate 0: a user who
   has the feature (gate 0 on) can still decline the network fetch.

Defaults for gate 2 still differ by flavor via `FlavorEdges`, unchanged from the original design:

- **gms: default on** (once gate 0 is on for that install) — these users already run an app that
  talks to Firebase; fetching public orbital data is unremarkable.
- **fdroid: default off.** F-Droid flags network access in its anti-features metadata and that
  audience expects opt-in. A first-run prompt when the layer is first enabled turns this into one
  tap rather than a hunt through settings.

One hazard to note for whoever implements it: `SettingsViewModel`'s `combine` unpacks its flows by
**positional index** (`values[0]` … `values[15]`). Append only; inserting mid-list silently shifts
every subsequent index.

### What belongs in `SatelliteLayer.PARAMETERS`, not in Settings

D87/D91 landed per-layer parameters (`SkyLayer.parameters`, a companion-declared static list,
`LayerRegistry.create(settings: Settings)`) since this design was drafted, using the Solar
System layer's Auto/True-scale/Glyphs disc-size choice as the first case. Two satellite settings
follow the identical wiring — declared once in `SatelliteLayer.PARAMETERS`, read by the layer's
own companion factory, no bespoke Settings-screen row:

| Parameter | Kind | Lands in | Detail |
|---|---|---|---|
| `pass_alerts` | boolean | phase 4b | The opt-in for the time-critical pass notification — see §"Pass alerts" |
| `fleet` | enum ("ISS only" / "Bright fleet") | phase 5 | Rides with the `GROUP=visual` expansion; phases 1–4 have exactly one satellite and nothing to choose between |

`pass_alerts` arrives first and is the **first boolean parameter in the codebase**, which is the
one piece of new machinery this feature asks of the D87/D91 mechanism — see §"Pass alerts" for
the `LayerParameter.Toggle` recommendation.

## Map layer

`SatelliteLayer` follows `SolarSystemLayer.kt`, with one part that must be rewritten rather than
copied.

**The resubmit gate.** `SolarSystemLayer` re-emits when any body has moved
`RESUBMIT_THRESHOLD_DEG = 0.01`, which against the Moon's ~15°/day means about a minute. At the
ISS's ~4°/minute the same threshold fires every ~150 ms — a scene rebuild per frame. Use a fixed
cadence of ~1 s with a ~0.25° threshold (about 4 s of ISS motion, sub-pixel at typical FOV), and
record it as a deliberate divergence rather than an oversight.

**Depth 55** — in front of the solar system (60), behind the horizon. D18's occlusion sort by
descending distance naturally places a satellite nearest the viewer, which is correct.

## Where the pass predictor surfaces

The original draft argued the pass predictor is the higher-value half of the feature and then
never said where it lives — phase 4 listed only the map layer. Closing that gap, because it
changes what phase 4 is.

**Primary: the object-info sheet.** Tapping the ISS — or its label, which D83 made a valid target
— opens the existing tap-to-identify sheet, and that is where "what is that?" already gets
answered. For a satellite the honest answer is mostly *"and here is when to look again"*, so the
next pass belongs in the same place as the identification. This needs **no new screen and no new
nav route**: `Routes` has no `TONIGHT` entry and does not gain one, `ObjectInfoUi` gains a pass
row for satellite targets, and everything about the sheet's existing lifecycle applies unchanged.

**Second, and nearly free: `SkyEvent.SatellitePassTonight` in `:core:events`.** A visible pass is
precisely what that engine exists to surface, and joining the sealed hierarchy puts passes on the
tonight widget and the D77 notification digest with no new UI at all — those surfaces render
`SkyEvent`s and nothing else.

Purity survives, and the mechanism already exists: `tonightSky()` takes `showers:
List<MeteorShower>` as a **parameter** rather than fetching them, so passes arrive the same way —
the caller computes them and hands them in. `:core:events` stays a `skymap.pure-kotlin` module
with no network and no Android, exactly as its KDoc promises ("computed offline"). The
network-ness lives in the app-side caller, which is where the catalog's shower table already
comes from.

Two details this forces:

- **Quality ranking.** `PLANET_QUALITY` tops out at Venus (0.68), and a −3.1 ISS pass is brighter
  than anything in the sky but the Moon. On brightness alone it outranks everything; but a pass
  is a ~6-minute window and a well-placed planet is available all evening, so raw magnitude is
  the wrong scale to borrow. Proposed: quality from peak magnitude, banded so only a genuinely
  bright pass (brighter than ~−2) clears Venus, keeping ordinary passes below the planets.
- **Passes reach the widget and digest whenever the layer is on.** The gate belongs at the engine
  boundary, not inside the notification code: `:core:events` is pure and cannot read a DataStore
  preference, so the caller simply does not pass any passes in when the layer is off —
  `if (layerEnabled) passes else emptyList()`. One gate covers the widget and the digest line
  consistently, and the purity argument above stays intact.

**Deferred: a "more passes" list screen.** §"Pass prediction" already parameterizes the horizon
so such a screen could ask for a longer one. The sheet answers the common case ("when next?"),
and a dedicated list is worth building only once passes prove popular — not at launch.

## Pass alerts: a separate, time-critical notification

Distinct from everything above, and deliberately **not** a claim on D77's one nightly slot.

**The opt-in is a layer parameter, not the layer's on/off state.** `SatelliteLayer` declares a
`pass_alerts` parameter alongside the phase-5 fleet choice, so it appears as an inline expander
under the satellite row in the Layers sheet (D87), persists through the same DataStore path, and
is read by the layer's own companion factory (D91). Turning the layer on to *look* at satellites
is not the same act as asking to be interrupted about them, and this keeps the two separable.

**`LayerParameter` gains a `Toggle` subtype.** It currently models only an enumerated choice —
its KDoc says so, and that "a slider or a toggle would be a new subtype here, not a change to this
one". Pass alerts are boolean, and encoding them as a two-option `on`/`off` enum would render as
a radio expander where a switch is meant. So make `LayerParameter` a sealed interface now:
**decided, on the grounds that more toggles are coming** (see the notification direction below,
where meteor-shower alerts become the same shape), and the second parameter is a cheaper place to
absorb that than the fifth. Costs a touch of D91's wiring; nothing about the declared-once
property changes.

### Where notification settings live, and where they are going

This puts one notification opt-in in the Layers sheet while D77's shower alerts and tonight digest
live in Settings → Notifications. That is a split, and worth being clear that it is **the first
instance of an intended pattern rather than a wart**:

- **The per-layer affordance is the point.** A notification about satellites belongs where you
  engage with satellites — and meteor-shower alerts, today a Settings row, are the same shape and
  are expected to gain the same in-layer toggle.
- **A single place to manage notification kinds in bulk is wanted too**, once there are enough of
  them to need one. The two are complementary: bulk management for "what may interrupt me at
  all", the in-layer toggle for "while I am here, alert me about this".
- **Deferred**, explicitly. Building the unified surface now would be designing for a scale that
  does not exist — there would be three kinds in it. The near-term mitigation stands: Settings →
  Notifications names satellite pass alerts and points at the Layers sheet, so someone asking
  "why am I getting these?" is not sent hunting.

The consequence for this design is only that `pass_alerts` should not be built as a satellite
special case — it is the first of a family, so the `Toggle` subtype is shared machinery from the
start.

### Timing, and why the existing poster cannot deliver it

Fire **6 minutes before AOS** — enough to get outside and let eyes adjust, short enough that the
alert still reads as "now". This is the notification the feature actually wants; the sunset+30
digest line is advance notice, not a call to action.

**The blocker: `NotificationScheduler.armPoster` uses WorkManager `setInitialDelay`, which makes
no timeliness guarantee.** Under Doze, delivery can slip well past the target. For a "tonight's
highlights" post at sunset + 30 min a 20-minute slip is invisible; for a 6-minute lead on a
6-minute event, the same slip means the notification arrives after the pass is over — strictly
worse than sending nothing, because the user walks outside to an empty sky. **Reusing D77's
scheduling path for this is not an option**, and that is the single most important implementation
note in this section.

Options, in preference order:

1. **`AlarmManager.setAndAllowWhileIdle()` with a ~10-minute lead.** No special permission, wakes
   the device in Doze, fires within a few minutes of target. The lead absorbs the imprecision so a
   late fire still lands before AOS. Recommended: it is the only option with no permission story.
2. **`setExactAndAllowWhileIdle()` guarded by `canScheduleExactAlarms()`**, falling back to (1).
   Exact, but on Android 12+ needs `SCHEDULE_EXACT_ALARM`, which Play restricts to apps whose core
   function is alarms — we would not qualify, so most installs land on the fallback anyway. The
   guard and fallback are pure cost for a minority of devices.
3. `setAlarmClock()` is exempt from Doze and needs no permission, but puts an alarm icon in the
   status bar. Wrong signal for a sky event; rejected.

Whichever lands, the lead time is a named constant with the Doze reasoning in its KDoc, so nobody
later "tightens" it to 6 minutes and silently reintroduces the late-delivery failure.

### Volume control

The alert is time-critical and therefore intrusive, so the volume rules are part of the design:

- **Its own channel, `CHANNEL_PASSES`.** Android's per-channel controls then let someone keep
  shower alerts while muting pass alerts, which the existing `canPost` check already respects.
  Sharing `CHANNEL_SHOWERS` would make muting one mute the other.
- **At most one alert per night — the best pass by peak magnitude.** The ISS can make two or three
  visible passes in a good evening, and alerting on each is precisely the pattern that gets an app
  silenced. The brightest is the one worth going outside for.
- **Naturally self-limiting.** Visible passes cluster into multi-day seasons separated by
  multi-week gaps (§Motivation), so even at one per night this is not a nightly notification.
- **It does not consume D77's slot**, and D77's slot does not suppress it: a shower alert at
  sunset + 30 and a pass alert at 21:41 are different messages at different times about different
  things. This is a deliberate divergence from D77's one-notification-per-night invariant, and the
  justification is exactly that time-criticality — recorded so it does not read as an oversight.

### Iconography and states — mocked before implementation

Two new drawables are needed and neither exists yet: a rail glyph (`ic_layer_satellites`) and the
map marker. A visual bench covering both, plus every data-freshness state, sits at
<https://claude.ai/code/artifact/acde95a1-f383-4d0c-822f-ecfad4ab2b70> — built from the real
`Theme.kt`/`SkyColors.kt` tokens, with a day/night switch, since the night palette collapses to
one hue and is where status distinctions usually break.

The decisions it proposes:

- **Rail glyph: the bus-and-solar-panels silhouette**, tinted `outline` when off and `primary`
  when on, exactly as `RailItem` already paints every other layer (D90 — no new tinting logic).
  Rendered at the real 44 dp pitch beside its actual neighbours, which is the collision test D83
  established: it must not read as the ringed solar-system glyph or the HUD's bracket corners.
- **Map marker: star-white, distinguished by a short fading trail, not by a new hue.** Naked-eye
  the ISS looks like a fast steady star. Lens Blue already means deep-sky and Planet Red already
  means planets/comets/showers (`SkyColors.kt`), so claiming a third hue would dilute an existing
  meaning to say something motion already says better. The label reuses `SKY_LABEL`, putting
  satellites in the same "computed, real-time" family as planets and meteor showers.
- **Freshness is a corner badge on an unchanging icon**, drawn from the existing two-tier status
  palette (AGENTS.md): none when fresh, `status_ok` at 3–10 days, `status_warning` past 10 days,
  and `status_absent` with the glyph dashed and greyed when there is no TLE at all. Keeping the
  glyph's shape fixed keeps "is the layer on?" and "is the data good?" as two separately readable
  questions rather than one overloaded icon.
- **The empty state is a card, not a missing layer** — "Satellite positions need an internet
  connection to update." with a Refresh button that still respects the circuit breaker, matching
  the §"Staleness and offline behaviour" table.
- **Panel D of the bench mocks the pass surfaces** from §"Where the pass predictor surfaces": the
  object-info sheet with its pass row, the tonight widget with a pass sitting among the ordinary
  `SkyEvent` rows, and the degraded/empty variants of the sheet. It also states the notification-
  slot contention as an open question rather than burying it.

The bench is a review artifact, not a spec; the drawables still get authored as
`res/drawable/ic_layer_satellites.xml` in the set's existing idiom, with a header comment naming
the glyphs they must not resemble (the convention D83 introduced).

## Phasing

Each phase is independently valuable and independently reviewable. Phases 1–2 are pure Kotlin with
no network and no UI, which means they are fully testable in CI.

| Phase | Contents |
|---|---|
| 1 | **Done (D94).** `Tle.kt`, `Sgp4.kt`, `SatelliteEphemeris.kt` — parser, propagator, frame chain, validated against Vallado's vectors and against Skyfield for the chain |
| 2 | **Done (D95).** `SatellitePass.kt`, `SatelliteMagnitude.kt` — pass search, visibility, brightness, driven by a fixed TLE and validated against Skyfield’s own pass search |
| 3 | **Done (D96, D97, D98).** Client, cache, circuit breaker, WorkManager job, `Experiment.SATELLITES`, settings and the F-Droid opt-in, the two analytics events, and the debug-only force-fetch with its Diagnostics rows |
| 4 | **Done (D99, D100).** `SatelliteLayer`, registration, the rail glyph, the freshness badge and the offline empty-state card, per the signed-off bench |
| 4b | **Done (D101, D102).** `SkyEvent.SatellitePassTonight` reaches the tonight widget and the D77 digest, and the object-info sheet gains its next-visible-pass row |
| 4c | **Done (D103).** The `LayerParameter.Toggle` subtype, `pass_alerts`, `CHANNEL_PASSES`, and the `AlarmManager` scheduling path |
| 5 | The ~100-brightest expansion: `GROUP=visual`, standard-magnitude table, pass pre-filter, and the `fleet` layer parameter |

## Verification strategy

**Vallado's verification vectors.** *Revisiting Spacetrack Report #3* (AIAA 2006-6753, hosted at
`celestrak.org/publications/AIAA/2006-6753/`) ships `SGP4-VER.TLE` with expected position and
velocity output — the canonical fixture for any SGP4 implementation. Inline a curated near-earth
subset as literals in the `RiseSetTest.kt` idiom (GPL header, backticked names, the published
figure cited in a comment above each assertion, tolerances as named constants with justification):

| Catalog # | Why this one |
|---|---|
| 00005 | Vanguard — the canonical first case in every SGP4 suite |
| 06251 | Near-earth normal drag, ~100 min period — the closest analogue to the ISS |
| 28057 | Sun-synchronous, low eccentricity — the typical shape of the `visual` group |
| 22312 | Exercises the perigee < 220 km simplified-drag branch, which naive ports silently get wrong |
| 04632 or 09880 | Deep-space — asserted **rejected**, pinning the scope decision above |

A correct double-precision port matches to ~1e-6 km, so a tolerance of 1e-4 km on position leaves
room for library variance while failing any real algorithmic slip by orders of magnitude.

**A second, independent golden — and this one matters more.** Vallado's vectors validate the
propagator **in ECI only**. They cannot catch a wrong observer model or a botched frame
conversion, which is exactly where third-party SGP4 ports actually fail — and where the 1–3°
spherical-Earth trap lives. So pin the *chain*: take a frozen ISS TLE, propagate to a known pass,
and assert altitude and azimuth against an independent reference to ~0.5°.

**As built (D94)**, that reference is **Skyfield** rather than a published Heavens-Above
prediction. Skyfield implements the full IAU precession/nutation/polar-motion chain, so it checks
the same thing while being reproducible and machine-generated — a screen-scraped prediction is
neither, and cannot be regenerated if a fixture ever needs to change — and being able to generate
more cases turned out to matter, because one pass over one city does not exercise the chain.

Five passes are pinned, chosen to span the geometry: London at 45.5° and at 87.2° (overhead),
**Sydney** (negative latitude — a sign error in the ellipsoid's `sin(lat)` term would be invisible
from the northern hemisphere, and azimuth sweeps through south instead of north), **Quito**
(latitude ~0, western longitude), and **Tromsø with catalog 28057** — inside the Arctic Circle and
a sun-synchronous 98.4° retrograde orbit, since the ISS never clears 10° from that latitude at all
and a polar satellite is the only honest way to test it. All five agree to **0.05°**, an order of
magnitude inside the budget above. Azimuth is not asserted within 10° of the zenith, where it is
genuinely ill conditioned rather than merely imprecise.

The frozen TLE is a golden fixture, not live data — it propagates from a fixed epoch to a fixed
time and never goes stale. Say so in the KDoc so nobody "helpfully" refreshes it.

## Risks

- **SGP4 transcription errors are silent.** The algorithm is several hundred lines of dense,
  unexplained coefficients, and a single dropped term yields plausible-looking but wrong positions.
  The verification vectors are the only real mitigation — which is why phase 1 is scoped as
  "validated against Vallado", not merely "implemented".
- **The frame chain is the gap Vallado cannot cover**, addressed by the second golden above.
- **CelesTrak is a single point of failure shared across the install base.** If they change the URL
  or firewall our aggregate traffic, the feature breaks for already-shipped versions with no
  client-side fix.

  **Decided** (see §"Settings"): ships behind `Experiment.SATELLITES`, reusing the D79/D80
  staged-rollout pattern, **off for both flavors at launch** — a remote kill switch is exactly
  what those flags exist for. The limitation is stated honestly: those flags are Firebase Remote
  Config, so **the switch is remotely toggleable only on gms; F-Droid builds are unprotected**
  and need a release to change either way.

  That asymmetry sets the rollout order rather than just being noted. gms is enabled remotely
  first, because it is the flavor where a mistake is recoverable without shipping; fdroid is
  enabled later by a binary release, once the breaker has proven itself in production on gms.
  Turning on the unprotected flavor first would mean discovering a retry bug in exactly the
  build that cannot be fixed remotely.
