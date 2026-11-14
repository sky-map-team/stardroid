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
https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=CSV   (ISS)
https://celestrak.org/NORAD/elements/gp.php?CATNR=48274&FORMAT=CSV   (CSS / Tianhe)
```

One request per object, by catalog number, each a header row plus one data row (~0.5 KB). The app
used to fetch `GROUP=stations` (21 objects, mostly docked visiting vehicles and debris it then
discarded); CelesTrak asked clients to request only what they draw (#1269, #1299). `GROUP=visual`
(the ~100 brightest, ~50 KB) remains the phase 5 option. `FORMAT` accepts
`tle`/`3le`/`2le`/`xml`/`kvn`/`json`/`csv`.

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

### Wire format: OMM CSV, not TLE text

CelesTrak ran out of 5-digit catalog numbers on 2026-07-11, and TLEs cannot carry the 6-digit
numbers now issued, so the feed moved to the OMM CSV format (`FORMAT=CSV`, the format CelesTrak's
operator recommended). The ISS and Tiangong still have 5-digit numbers, but a TLE parser would
silently lose every new object, so the app moved before it mattered.

CSV over JSON or XML: the header row names the columns, so the parser (`Tle.parseOmmCsv`) finds
fields by name and ignores extras, and `:data` still needs no JSON dependency. The mean elements
are the same SGP4 inputs a TLE carries, so `Sgp4` and the Vallado vectors are unchanged;
`Tle.parse` for TLE text remains for those vectors and the fixtures. A CSV row has no checksum, so
the parser validates values instead (positive mean motion, eccentricity in [0, 1)) and skips bad
rows. The two responses are merged into one CSV (one header, both data rows) for the cache.

Requests are unconditional (no `If-Modified-Since`): with two objects there is no single
`Last-Modified` to send, and each body is a few hundred bytes.

Mirror `widget/WidgetScheduler.kt` — `enqueueUniquePeriodicWork`, `ExistingPeriodicWorkPolicy.KEEP`,
a 12 h period — with two deliberate departures: add `NetworkType.CONNECTED` constraints (nothing
in the repo currently sets constraints, and this job must, so it waits rather than fails), and
schedule only for people using the feature, from a session they can see (§"Who downloads").

Cache **the merged OMM CSV text** (`elements.csv`). It is already compact, so re-serializing
to JSON or Room gains nothing; and storing the text as received means the network and cache paths
exercise one parser rather than two.

Store under `filesDir/satellites/`, **not `cacheDir`** — the OS may evict `cacheDir` at any time,
and losing the TLE silently kills the feature offline. Write atomically (temp file, then rename)
so a kill mid-write cannot leave a truncated element set.

### Who downloads: people using the feature (#1272)

In 2.3.0 each install's own limits held: the 2-hour floor, the 12-hour job and the breaker. But
nothing limited how many installs were downloading. The job was scheduled on every process start,
so a reboot (`PassAlertBootReceiver`), a clock change (`WidgetClockChangeReceiver`) or a widget
update kept installs nobody used fetching every 12 hours, and the combined load overloaded
CelesTrak (#1269). Download volume now follows use, not installs.

`SatelliteFetchConditions` (in `:shared:viewmodels`, so both platforms use it) allows a download
only when all of these hold:

1. `Experiment.SATELLITES` is on: the stored value from the last activation. The worker also
   checks a fresh one before downloading (see "Order inside the worker" below).
2. The user has consented (`satelliteDataEnabled`).
3. The Satellites layer is on. It starts off.
4. The terms are accepted (`StartupRouter.needsEula` is false).
5. The app was in the foreground, where the user could see it, within the last **14 days**
   (`StartupState.lastForegroundAt`). Alternatively, something shows satellite data while the
   app is closed (`satelliteBackgroundUse`, shared by both platforms):
   - a placed Tonight widget, the only widget with passes (Android only);
   - pass alerts turned on;
   - the D77 tonight digest turned on.

   Alerts and the digest count only with `Experiment.NOTIFICATIONS` on and notifications allowed
   by the OS, because a notification that can't be posted needs no data. This waives only the
   recent-use rule: rules 1 to 4 still apply, so the layer must be on.

The timestamp is written each time `MainActivity` starts and each time iOS enters the
foreground. A background start never writes it. A stamp more than a day in the future means the
clock was wrong when it was written, so it doesn't count.

**Existing installs count as unused until opened.** An upgrade from a release without the
timestamp has none recorded. Until the user next opens the app, such an install keeps
downloading only if a widget, alert or digest from rule 5 needs the data. Any other install's
schedule is cancelled at its next run.

**Who schedules.** Only a session the user sees. While `MainActivity` is started it follows the
conditions, scheduling or cancelling. `SkyMapApplication.onCreate` runs on every process start,
including boot, clock broadcasts, widget updates and other jobs, so there it may only cancel
(`satelliteScheduleAction`). It cancels when the flag or one of the user's choices is off
(`switchedOn`), but not for lapsed use. A cold start may be about to show the map, and a cancel
computed from the previous visit could land after `MainActivity` schedules. The worker re-reads
every condition on each run and cancels its own schedule once they fail. An install whose user
stops opening the app therefore makes no request after day 14 and wakes at most once more.
Opening the app again reschedules, and the first fetch follows within the usual jitter, still
subject to the 2-hour floor.

**Order inside the worker.** On each run:

1. The local conditions come first, including the stored flag. If they fail, the worker cancels
   its schedule, with no Firebase fetch and no CelesTrak request.
2. Then comes the fresh flag (§"Settings", gate 0):
   - UNKNOWN retries. Each retry repeats the Firebase fetch only, and passes step 1 again first,
     so a lapse during backoff still cancels.
   - OFF cancels the schedule, like a closed gate. Switching the flag back on then resumes
     downloads as people open the app, rather than in every live job at once.
   - ON goes on to the download.

A consequence on gms: an install whose stored flag is OFF cancels without a fresh fetch, so it
learns of a remote ON only when the app is next opened and Remote Config activates. A remote
flip never restarts downloads in the background.

**iOS** applies the same conditions in three places:
- the fetch on app open;
- the request for a `BGAppRefreshTask`. When the app leaves the foreground, Swift asks Kotlin
  (`backgroundRefreshWanted`) and schedules only if the answer is yes, otherwise dropping any
  pending request. A layer switched on in the foreground is therefore armed on the next move to
  the background;
- each background run, which asks for the next slot only while the conditions hold.

Both downloads also need a fresh ON flag, read at call time rather than at launch (§"Settings",
gate 0). The app-open fetch asks for it only once the cache is stale.

Before this change, every move to the background and every run rescheduled unconditionally
(#1278, finding 5).

The same task also replans iOS notifications, which needs no network, so it is also requested
while notifications are on (flag, a kind switched on, permission). A run then downloads only if
the conditions hold, and replans either way.

## Staleness and offline behaviour

TLE degradation is smooth and well characterised, so the UX can be honest rather than binary:

| TLE age | Error | Behaviour |
|---|---|---|
| < 3 days | < ~2 km | Normal. No UI mention. |
| 3–10 days | ~2–15 km | Show passes, with a quiet "orbit data N days old" note on the card. |
| > 10 days | tens of km, growing | Keep the map layer (roughly right, still pleasant), but **suppress precise pass times**. A confidently wrong "21:47" is worse than admitting the data is stale. |
| none | — | Actionable empty state: Explanatory card ("Satellite positions need an internet connection to download…"), with no button: downloads are not user-triggered (#1284). |

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

   Before each download the worker waits up to 10 s for a fresh Remote Config fetch (gms: a
   15-minute minimum fetch interval) and reads the flag after activation. If the fetch fails,
   is throttled or times out the flag is unknown, so the worker returns `Result.retry()` and
   WorkManager's default backoff (30 s, doubling, capped at 5 h) re-attempts only that Firebase
   fetch. The CelesTrak download needs a fresh ON and is still bounded by the 2-hour floor and
   the circuit breaker; it is never retried by this path. A fresh OFF cancels the job (§"Who
   downloads"). F-Droid has no server and always reads its static value.

   iOS uses the same shared gate, `satelliteFlagState`, before both of its downloads (#1285):
   the app-open fetch and the background refresh. iOS freezes its flags for the launch, so the
   gate reads the flag at call time (`ExperimentConfig.isEnabledNow`), after a fetch that has
   the same 15-minute floor as gms. iOS has no WorkManager to retry with. An unknown flag skips
   the download, and the next app open or background slot tries again. A failed Remote Config
   fetch isn't recorded, so while the cache stays stale each app open or slot fetches the flag
   again, at most once each. A fresh OFF ends the background chain, as Android's cancel does.

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

**Update (November 2026): a third gate, the layer itself.** The Satellites layer now starts off
(`LayerRegistry.DEFAULT_DISABLED_IDS`, like the alt/az grid), and neither platform fetches until
the user turns it on: Android's job is scheduled only while the layer is on, and iOS fetches on
app open (if stale) or when the layer is switched on. That makes the privacy policy's "only if you
turn on the satellite layer" true, lets the user decide whether to show their IP address to
CelesTrak, and spreads first requests by when people choose to try it. The empty-state card says
the first download can take a few minutes. Users who never touched the toggle see the layer go
from on to off on update; those who set it keep their choice.

**Update (November 2026, #1272): accepted terms and recent use.** After CelesTrak was overloaded
(#1269), downloads also need accepted terms and use within the last 14 days, or a widget, pass alert
or digest that shows the data. They are scheduled only from a session the user sees. See §"Who
downloads".

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
**Superseded by [info-card-live-content.md](info-card-live-content.md) (proposed, D141):** #1142
asks for the list on the card itself, as that doc's "Upcoming" section, with no new screen.

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
  connection to download…", with no Refresh button (#1284), matching the §"Staleness and offline
  behaviour" table.
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
