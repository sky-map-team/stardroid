# Sky Map v2 Documentation

Documentation for the Sky Map rewrite. The v1 specs under `../stardroid-v1/specs/` are
background information only — the v1 *code* is the definitive reference for existing behavior,
and this folder is the definitive record for v2 product and design decisions.

Docs are grouped by what they are: **built** (the design describes shipped code — read it as
documentation), **proposed** (design only, nothing implemented — read it as a plan that may
still change), and **reference/living** (audits, logs, and inventories that are neither).
Every design doc repeats its own status in its first lines; that header is authoritative if
the two ever drift.

### Built — the code exists and matches these docs

| Document | Purpose | Landed |
|---|---|---|
| [design/high-level-architecture.md](design/high-level-architecture.md) | Module structure, layer separation, rendering/ephemeris API abstractions | approved D15; realized across slices 1–19 |
| [design/data-layer.md](design/data-layer.md) | Catalog storage, localization, and download strategy | approved D15; realized in slice 4 |
| [design/build-and-tooling.md](design/build-and-tooling.md) | Module graph, convention plugins, CI architecture/perf/DB gates | slice 1, D20 |
| [design/core-math-astronomy.md](design/core-math-astronomy.md) | `:core:math`, `:core:astronomy` | slices 2/5, D25–D27, D36 |
| [design/render-api.md](design/render-api.md) | `:render:api` contract, `:render:gles1` port notes | slices 3a/3b, D28–D31 |
| [design/catalog-and-schema.md](design/catalog-and-schema.md) | `:core:catalog`, Room schema, build-time DB generation | slices 4a–4d, D32–D35 |
| [design/layers-and-app.md](design/layers-and-app.md) | Layer system, ViewModel decomposition, app edges | slices 4d–23; **comets still to come** |
| [design/screens-and-startup.md](design/screens-and-startup.md) | Supporting screens, startup routing, settings | slice 16, D48 |
| [design/ux-polish.md](design/ux-polish.md) | Two hands-on feedback rounds: chrome, dialogs, time travel, snackbars, location map, meteor layer | slices 20–23, D52–D58, D60; **one loose end — the splash cross-fade** |
| [design/map-hud.md](design/map-hud.md) | The top-right map pointing readout — RA/Dec, Alt/Az, FOV, alignment correction | agreed D65, implemented D66 |
| [design/camera-ar-mode.md](design/camera-ar-mode.md) | Through-camera (AR) mode, drag-to-align, share | D64, D67–D71 (flag-gated, D80) |
| [design/color-scheme-proposal.md](design/color-scheme-proposal.md) | The "Deep Space / Star Gold" brand palette — rationale and before/after | accepted and implemented, D73 |
| [design/widgets-and-notifications.md](design/widgets-and-notifications.md) | Home-screen widgets, notification channels, events engine | phases 1–3, D75–D77 (flag-gated, D80); **phase 4 unbuilt** |
| [design/lunar-eclipse.md](design/lunar-eclipse.md) | Shadow-cone astronomy, tonight/digest highlights, Moon info-card row, GLES1 map tinting, opt-in alert | D106, D107 |

### Proposed — design only, no code

| Document | Purpose | Decision |
|---|---|---|
| [design/solar-system-imagery.md](design/solar-system-imagery.md) | Solar-system disc quality, true angular size, procedural phases, Saturn's rings, Jupiter's moons | D85–D88 and D91 implemented (PRs #104–#107); D89 deferred |
| [design/data-packs.md](design/data-packs.md) | Downloadable data/image packs — format, storage, delivery, image-resolver seam | D79 (design accepted, implementation not scheduled) |
| [design/localization.md](design/localization.md) | String chunking, v1 translation reuse, and what the Room catalog changes about i18n | undecided; the groundwork half shipped as D78 |
| [design/ephemeris-accuracy.md](design/ephemeris-accuracy.md) §3 | Replacing the hand-rolled ephemeris with Astronomy Engine | undecided (§1–2 are reference — see below) |
| [design/satellite-tracking.md](design/satellite-tracking.md) | ISS/satellite tracking — CelesTrak TLEs, SGP4, visible-pass prediction | D92 accepted; phases 1–2 done (D94, D95), phase 3 nearly done (D96, D97). Phases 4–5 not started |
| [design/render-gles3.md](design/render-gles3.md) | The GL ES 3.0 backend — parity port, then the sky/horizon/constellation-art/eclipse unlocks | proposed — design only |
| [design/time-travel.md](design/time-travel.md) | Time-travel redesign — anchors (what stays still), the scrubber and fling-to-play, object trails as a standalone feature, the re-housed player and presets | proposed — D112, D113, D114 |

### Reference and living documents

| Document | Purpose |
|---|---|
| decisions.md | Running log of product/technical decisions and their rationale — the authority behind every status above |
| [code-overview.md](code-overview.md) | Top-down tour of the codebase as built — module graph, build machinery, subsystem pointers, doc-health table |
| [improvements-over-v1.md](improvements-over-v1.md) | The full at-launch feature surface (unflagged only) plus the net-new-vs-v1 highlights drawn from it — the source for help text, store copy, screenshots, and release notes |
| [info-card-coverage.md](info-card-coverage.md) | Which labelled objects still lack info cards, why that blocks tap-to-identify, and how to write new ones |
| [design/sensor-correction-model.md](design/sensor-correction-model.md) | Sensor error physics and why drag-to-align's az/alt correction matches them |
| [design/ephemeris-accuracy.md](design/ephemeris-accuracy.md) §1–2 | Coordinate-frame audit and the residual error budget (precession fix, D84) |
| [user-testers.md](user-testers.md) | How testers join the internal/alpha/beta channels |

## Status

Implementation underway, following the porting order in
[design/high-level-architecture.md](design/high-level-architecture.md):

- **Slice 1** — build scaffolding, convention plugins, Konsist architecture gate (D20).
- **Slice 2** — `:core:math` + `:core:astronomy`, golden-tested against v1 (D25, D27).
- **Slice 3a/3b** — `:render:api` contract + shared projection (D21, D28), GLES1 backend
  (points, lines, images, labels; D29, D30), test-scene activity and D19 perf gate (D31).
- **Slice 4a/4b** — `:core:catalog` pure domain (D32), `:data` Room catalog store: schema,
  FTS prefix search, locale fallback, transactional pack replacement (D33).
- **Slice 4c** — `source-data/` harvested from v1 (stars, DSOs, constellation figures, ~30
  locales of names and info cards), the deterministic `:data:generateCatalogDb` generator,
  and `createFromAsset` wiring (D34).
- **Slice 4d** — catalog-backed layers through the 3b backend: `SkyLayer`/`CatalogLayer`, the
  render-API `Icon` addendum + GLES1 `IconDrawer`, DB failure recovery, and the test activity
  rendering the real bundled catalog (D35).
- **Slice 5** — the sky model and sensor pipeline (porting-order step 4): pure
  `SkyModel`/`LocalFrame`/`Pointing` + the vector-rejection sensor fusion in `:core:astronomy`,
  the `OrientationSource`/`MagneticDeclinationSource` Android edges, and a sensor-driven camera
  mode in the test activity (D36).
- **Slice 6** — the computed layers (porting-order step 5 begins): `GridLayer`,
  `EclipticLayer`, `HorizonLayer`, and `SolarSystemLayer` with D18 distance-ordered images,
  phase-bucketed Moon imagery in the `planet/` asset namespace, and the `LayerStrings` locale
  edge; the test activity now renders them in place of its synthetic scenes (D37).
- **Slice 7** — the Compose map screen (porting-order step 5 continues): `MainActivity` +
  `AppGraph` (hand-wired DI; Hilt deferred), `MapViewModel` (D14 sensor/manual reference
  frame, v1 drag/rotate/zoom semantics), `LayersViewModel` + `LayerRegistry` with DataStore
  visibility toggles, the `RenderBinder` flow→renderer bridge, night mode spanning theme and
  render state, and `ResourceLayerStrings` (D38).
- **Upstream sync** — the post-fork upstream v1 visual changes ported to v2 (D40): the
  true-ARGB color scheme centralized in `SkyColors`, the graduated Star Gold ecliptic at
  depth 5, the green horizon with its additive glow (`GlowPrimitive`/`GlowDrawer` — the render
  API's first mesh), and Lens-Blue-tinted white DSO glyphs.
- **Slice 8** — the sky-gradient render state (porting-order step 5 continues):
  `RenderState.skyGradient` carrying the sun's celestial direction, the GLES1
  `SkyGradientDrawer` (v1 `SkyBox` dome behind all layers, skipped in night mode), the
  `show_sky_gradient` DataStore preference in the layers sheet, and `MapViewModel`'s
  five-minute sun tick (D41).
- **Slice 9** — time travel (porting-order step 5 continues): the v1 clock pair ported pure
  (`TimeTravelClock` speed ladder, `TransitioningClock` smoothstep sweep), the app-scoped
  `TimeController` clock bus every time consumer shares (paying off D41's sky-gradient
  clock debt), `TimeTravelViewModel`, and the Compose dialog + time-player bar with v1's
  preset events (D42).

- **Slice 10** — search (porting-order step 5 continues): `SearchViewModel` over the
  slice-4 ranked catalog search + `CoordinateParser`, ephemeris resolution for the
  position-less solar-system rows, the Compose search dialog (as-you-type hits doubling as
  v1's "Did you mean?"), and the overlay — pulsing crosshair, red-near→blue-far bearing
  arrow, and found-circle — drawn over the shared `SkyProjection`; manual mode slews to
  the target and adopts the catalog's search FOV, and selecting a hit re-enables its
  hidden layer (D43).

- **Slice 11** — location (porting-order step 5 continues): v1's `LocationController` state
  machine app-scoped like the clock bus, the platform `LocationProvider` edge, typed
  `Settings` persistence (`no_auto_locate`, saved lat/long) seeding the sky at startup,
  `LocationViewModel` + the location sheet, and the v1 dialog set — permission rationale,
  permanently-denied, acquiring-timeout, and manual entry with place-name geocoding (D44).

- **Slice 12** — object info (porting-order step 5 continues): `ObjectInfoViewModel` over
  the catalog's `objectInfo` cards (the DB shipped v1's `education/` content in slice 4),
  the Compose info card with photo/credit/description/fun-fact/data rows, see-also chips
  that open linked cards plus a Find in Sky Map action that re-aims like a search (D33),
  and tap-to-identify — `IdentifyGeometry` inverts the shared projection and hit-tests
  curated objects on enabled layers with v1's FOV-scaled threshold; v1's 254 celestial
  photos ship as assets (D45).

- **Slice 13** — settings (porting-order step 5 continues): the `Settings` interface grows
  v1's ten remaining preference keys (typed, enums stored by name), the full-screen Compose
  settings overlay in v1's section order, and the consumer wiring — `SensorConfig`
  re-parameterizing the sensor source live, declination correction/offset and the
  view-direction mode in `MapViewModel`, `font_size` → `RenderState.labelScaleFactor` (D18),
  the ported `HorizonLeveler` spring behind `auto_level_horizon`, and night-mode screen
  dimming (D46).

- **Slice 14** — the image gallery (porting-order step 5 continues):
  `CatalogRepository.galleryItems` (locale-resolved names over `image_ref`, name-sorted),
  the full-screen Compose grid over the bundled photos (downsampled decode + LRU, no Coil),
  tiles opening the slice-12 info card with Find landing on the map, the ported
  full-screen image expand overlay (now pinch-zoomable) off the card photo, and explicit
  night-mode red tinting for all photographs (D47).

- **Slice 15** — diagnostics and compass calibration (porting-order step 5 continues):
  `SensorStatusSource` (raw per-sensor presence/accuracy/values behind an interface), the
  diagnostics screen over live sensor rows plus a 500 ms polled snapshot
  (location/GPS/network/pointing/times), the two-tier status palette as typed Compose
  colors, the calibration screen (ImageDecoder-driven figure-eight gif, live accuracy,
  don't-show-again), and the ported `SensorAccuracyMonitor` auto-nudge as a cold prompt
  flow the map collects (D72).

- **Slice 16** — startup routing, help, and the Navigation graph (porting-order step 5
  concludes): the v1 `StartupRouter` gates ported verbatim over a DataStore `StartupState`
  (EULA version int → warm welcome behind the `WARM_WELCOME` experiment → What's New on
  upgrades only, with warm-welcome completion suppressing both What's New and the
  missing-sensor warning), the EULA/What's New/Help HTML rendered natively (no WebView),
  the warm welcome as a Compose pager with the sensor-check slide, the AndroidX splash
  covering the startup-state read, and the Navigation-Compose graph turning the
  settings/gallery/diagnostics/calibration overlays into destinations (D48).

- **Slice 17** — the gms/fdroid flavor split (D3's promise; the first post-porting-order
  slice): `FlavorEdges` as the hand-wired per-source-set seam, the `Analytics` edge —
  Firebase adapter (gms) / no-op (fdroid), v1's event names verbatim, the
  `enable_analytics` opt-out in a new "Other" settings section, and events wired across
  the ViewModels (funnels, search, preferences, layers, session buckets, start snapshot) —
  Firebase Remote Config behind `ExperimentConfig` (gms; fdroid stays static-off), and
  the fused location provider with a platform fallback when Play Services is unusable
  (D49).

- **Slice 18** — time-travel polish (the D39/D41/D42/D45 debt): the rise/set solver in
  `:core:astronomy` (USNO-golden-tested), next-sunrise/next-sunset presets over the
  location bus, per-event search targets as stable catalog ids aimed once the clock
  arrives, the `SoundEffects` seam — v1's travel whooshes behind the `sound_effects`
  preference, since removed outright (D111) — the purple travel flash + toasts as
  `TravelEffect` emissions, and v1's keep-screen-on flag (D50).

- **Slice 19** — rise/set on the info cards (the brief's roadmap feature, first half): the
  D50 solver generalized to any `(Instant) -> RaDec` position with a public `altitudeDeg`,
  `ObjectInfoViewModel` resolving each card's next rise/set at the observer's location and
  the map's current time (ephemeris for solar-system cards, fixed catalog positions
  otherwise, circumpolar/never-rises detection), and "Rises:"/"Sets:" rows on the card
  (D51).

Porting-order step 5 is complete — the faithful-port phase ended with slice 19. See the
rewrite brief in [../README.md](../README.md).

## Post-port work (condensed; details live in decisions.md)

- **Slices 20–21** — two UX-feedback rounds: dialog styling, time-travel visibility,
  snackbars, the location map (D52/D53), plus the topocentric Moon (D54) and the
  sun/moon-disc-size stance (D55).
- **Slice 22** — the three-zone map chrome: layer rail, action cluster, overflow sheet
  (D56/D57).
- **Slice 23** — the meteor-shower layer, closing D37's last computed-layer deferral (D58).
- **Slice 24** — Hilt replaces the hand-wired `AppGraph` for the singleton graph (D59).
- **Polish and identity** — second hands-on feedback round (D60), the warm welcome rebuilt as
  a live-chrome tour (D61), redesigned All-Rights-Reserved DSO icons (D62), v1-parity star
  set + IAU/Bayer/Flamsteed names (D63), launcher icon, splash, fastlane, R8 minification.
- **Map HUD** — the top-right pointing readout (D65 agreed, D66 landed).
- **Through-camera (AR) mode** — direction (D64), then four slices: camera underlay,
  drag-to-align, map-only share, camera share layouts (D67–D71).
- **Brand colors** ("Deep Space / Star Gold", D73) and the **UI-nit pass** (D74).
- **Stickiness surfaces** — widgets & notifications architecture (D75), the events engine and
  moon/tonight/countdown widgets (D76), shower-peak and tonight-digest notifications (D77).
- **Localization groundwork** — 31 locales salvaged from v1, the help document split into
  per-section keys (D78).
- **Launch shaping** — data-pack design accepted on paper only (D79), AR/share/widgets/
  notifications put behind experiment flags and out of launch copy (D80), the warm-welcome
  flag retired (D81), first-run chrome discovery and the short-side FOV convention (D82),
  second-round discovery fixes (D83).
- **Ephemeris** — the Meeus Sun/Moon returned in J2000, the frame the app draws in (D84).
- **Solar-system imagery** — the disc-quality overhaul designed on paper only: higher-resolution
  art and the renderer fixes behind it (D85), true angular size with a minimum-size floor
  superseding D55's deferral (D86), parameterized layers carrying the disc-size choice (D87),
  procedural phases replacing the baked Moon bitmaps (D88), and Saturn's rings / Jupiter's moons
  staged to the info cards first (D89).

## Not built

The complete list of things these docs describe that do **not** exist in the code:

- The solar-system imagery overhaul — higher-resolution discs, true angular size, procedural
  phases, Saturn's ring tilt, Jupiter's moons
  ([design/solar-system-imagery.md](design/solar-system-imagery.md), D85–D89).
- Downloadable data packs ([design/data-packs.md](design/data-packs.md), D79).
- Full localization beyond the D78 groundwork ([design/localization.md](design/localization.md)).
- An off-the-shelf ephemeris library ([design/ephemeris-accuracy.md](design/ephemeris-accuracy.md) §3).
- Comet layers ([design/layers-and-app.md](design/layers-and-app.md)).
- The GL ES 3.0 renderer backend and everything downstream of it — true sky gradient,
  ground and horizon profile, constellation art, eclipses
  ([design/render-gles3.md](design/render-gles3.md)).
- Widgets & notifications phase 4 — conjunctions, moon phases, rise alerts
  ([design/widgets-and-notifications.md](design/widgets-and-notifications.md)).
- The splash-to-sky cross-fade ([design/ux-polish.md](design/ux-polish.md) item 2, step 2).

For the user-facing view of what v2 adds over v1, the full at-launch feature surface, and
which features are built but unannounced, see
[improvements-over-v1.md](improvements-over-v1.md).
