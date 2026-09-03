# Improvements over v1

Two things in one file: the full at-launch feature surface (what a user actually gets today —
the input for help text, promotional copy, and screenshot selection), and the narrower "what's
new versus v1" list drawn from it for launch highlights and release notes.

**Everything in this file is launched: unflagged and unconditional** — no experiment gate, no
device-capability condition beyond the obvious (sensors, camera). Features that are built and
shipping in the binary but *unannounced* are deliberately excluded from both sections below;
they live in [the flagged table](#flag-guarded--not-for-launch-copy) at the foot of this file
and stay there until announced.

**Audited** 2026-08-04 against the code, not the docs; **counts re-verified 2026-08-27**
against a regenerated `skymap.db` (`./gradlew :data:generateCatalogDb`), which is the
authority — `source-data/` holds pre-filter rows. Re-verify before reuse if the catalog has
changed.

## Read this before writing copy

One constraint that would otherwise produce a false claim:

- **Comets are the one unported v1 layer** (D37, D58). Any v1 material mentioning comets
  does not carry over.

UI translation is no longer a caveat: the `tm` pipeline (D78) shipped full coverage across
all **28 core languages** as of 2.0.0-beta07, including the help document, which was split
into per-section keys precisely so it could be translated the same way as everything else.
**Safe to say the app is fully translated into 28 languages, help included.** Three more
locale directories ship alongside those 28 — `ru`, `ca`, `hu` — but they're intentionally
excluded from core and sit at 11–25% coverage (partial/legacy, not a bug to fix or backfill).
Don't count them toward "the app speaks N languages," and don't shoot screenshots in them.

## The full at-launch feature surface

Everything here is launched and unflagged, including faithful v1 ports — not just what's new.
For the narrower "what changed versus v1" list, see the [highlights table](#whats-new-versus-v1)
below.

### Sky rendering

- Real-time sky driven by device sensors; auto (sensor) ⇄ manual mode toggle
- Drag, pinch-zoom, and rotate; double-tap toggles horizon auto-leveling
- **Eight layers** — Stars, Constellations, Deep Sky, Solar System, Meteor Showers, Grid,
  Horizon, Ecliptic — reachable from the layer rail; long-press opens the full sheet
- Sky gradient (the daylight dome) as a separate display toggle
- Night mode, red-shifted across both the Compose theme and the renderer, with three
  dimming levels
- "Deep Space / Star Gold" brand color scheme (D73)
- Chrome auto-hides after a few seconds; a sky tap brings it back

### Catalog — bundled, fully offline

| | Count |
|---|---|
| Stars | **3,186** (v1's set, magnitude ≤ 5.6) |
| Stars with a searchable name | **2,195** — see the naming caveat below |
| Stars with a proper name (map label) | **324**, including 268 new IAU primaries |
| Deep-sky objects | **135** |
| Constellations | **88** — drawn as **89** figures (Serpens splits into Caput and Cauda) |
| Solar-system bodies | **26** — the Sun, 7 planets, 17 moons, 1 dwarf planet |
| Meteor showers | **10** |
| Celestial photos | **250** referenced by the catalog (254 files ship; 4 unreferenced) |
| Catalog-data locales | **30** |
| UI locales | **31** — 28 core at 100% coverage, plus 3 partial/legacy (`ru`, `ca`, `hu`) |

**"88 constellations" and "89 figures" are both correct — don't merge them.** The catalog holds
89 rows under `layer_kind='constellations'` because Serpens is drawn as two separate figures.
Copy should say **88 constellations**; only engineering docs want the figure count.

**Don't say "2,195 named stars" — that figure is searchable names, not map labels.** Of the
3,186 bundled stars, 2,195 carry at least one name row, but most of those rows are Bayer or
Flamsteed designations ("α Cyg", "50 Cyg") rather than proper names. Designations are
search-only by design (D63's primary-only label gate, `CatalogLayers.kt`): a star whose only
name is a designation never sprouts a map label. Only the **324** stars with a true primary
name (e.g. Deneb, Mizar, Thuban) are labeled on the map. Safe framing: "2,195 stars are
searchable by name or designation; 324 carry a proper name on the map, 268 of them new."

Bayer and Flamsteed designations are searchable ("Alpha Cygni", "α Cyg", "50 Cyg" all find
Deneb) but do not appear as map labels. Deep-sky markers are an original, All-Rights-Reserved
icon set (D62).

Note: `source-data/stars.csv` holds 5,998 rows, but the generator filters to 3,186 for the
bundled DB (D63). **Use 3,186 in copy** — the remainder is seed data for a future pack.

### Find and identify

- As-you-type ranked search, doubling as v1's "Did you mean?"
- RA/Dec coordinate entry
- A successful search re-enables the target's layer if hidden, then slews to it
- Pulsing crosshair plus a red-near→blue-far bearing arrow
- **Tap-to-identify, on by default in sensor mode** — v1 shipped this switched off (D74)
- Info cards: photo, credit, description, fun fact, data rows, see-also chips, and
  **rise/set times** at your location
- Image gallery: a full-screen, pinch-zoomable grid over the bundled photos

### Time travel

- Year range **1600–3000**
- Speed ladder with slower / faster / pause / now controls; the player shows its own state
- Presets: next sunset, next sunrise, next full moon, next new moon, plus dated 2026 events
  (lunar and solar eclipses, Perseids, Geminids, Lyrids, the six-planet parade, Venus–Jupiter
  and Mars–Uranus conjunctions, a Jupiter occultation)
- Purple travel flash on entering and leaving time travel (the whoosh sound effects that
  accompanied it were removed in D111 — the app now has no audio at all)

### Position and sensors

- Automatic location (fused provider on gms, platform fallback), manual entry with
  place-name geocoding, and a static map of the confirmed fix
- **Map HUD**: right ascension / declination, altitude / azimuth with a compass point, and
  the current field of view
- Diagnostics screen with live per-sensor status
- Compass calibration screen with the figure-eight animation
- Legacy-sensor path, magnetic declination correction, and magnetometer Z-axis reversal for
  devices with bad or mis-mounted sensors

### Onboarding and support

- Splash, EULA, warm-welcome tour over the **live** app chrome, What's New on upgrade, and
  natively rendered Help (no WebView)
- **Every user sees the warm welcome in this first version**, including people upgrading
  from v1: v2 ships under v1's `applicationId`, but v1 recorded its welcome in
  SharedPreferences while v2 reads DataStore, and v1's preferences are deliberately not
  migrated (D1), so the pager shows for everyone (D80). Its experiment flag was retired at
  launch — this is a fully shipped feature, safe for help text and store copy.
- The tour is replayable any time via overflow → Tutorial
- Settings in five sections: Controls, Appearance, Sensors, Notifications, Other
- Analytics opt-out on gms; the F-Droid build has no analytics at all
- 31 UI locales — 28 core languages fully translated including Help, plus 3 partial/legacy
  (`ru`, `ca`, `hu`) — see above (D78)

### Screenshot shortlist

Strong, safe, and all unflagged: the map with constellations; an info card showing rise/set
rows; the gallery grid; the time-travel player mid-travel; search with the bearing arrow;
night mode; the map HUD readout; and the warm-welcome tour — the single most-seen surface in
the app, and it shows real UI rather than a mock.

Avoid anything from the flagged set — see the caveat on the temporary in-AR exposure
sliders in [the flagged table](#flag-guarded--not-for-launch-copy) below.

## What's new versus v1

Net-new capabilities and fixes in v2 that v1 did **not** have, drawn from the surface above —
the subset to lead with in launch highlights and release notes.

**Scope.** Only things a user could notice or that we'd genuinely claim as an improvement.
This is *not* a port log: faithfully reproducing v1 behavior (even where v1 was quirky) does
not belong here — that's what decisions.md records. An entry earns its place
by being a capability v1 lacked or a v1 bug we deliberately fixed rather than preserved.

**How to use.** When a slice adds something that isn't in v1, append a row here in the same
change, link the decision(s) that justify it, and phrase the "What it means for users" column
the way we'd say it to a user — not to a compiler. Keep the newest at the top.

| Feature | What it means for users | Since | Decisions |
|---|---|---|---|
| The Moon's phase is drawn, not picked from eight pictures | The terminator is computed for the actual illumination, so the phase is right on any night rather than snapping to one of eight stock images — and the Man in the Moon now stays still. v1 rotated the whole picture to face the Sun, which swung the familiar face around through the month. A New Moon is still visible too, as a dark sphere lit by earthshine, instead of vanishing. | unreleased | D88 |
| Venus and Mercury have phases | Venus goes through phases like the Moon's — a small full disc when it's far away, a broad crescent when it's near — and Mercury does the same. Zoom in and you can watch it. v1 drew both as flat, fully-lit dots whatever the date. Mars picks up its gibbous phase as well. | unreleased | D88 |
| Eclipses happen at the right time | The Sun and Moon are computed from full analytic series rather than v1's rough approximations, so an eclipse peaks when it actually peaks. v1 could be ~30 minutes out — enough to send you outside at the wrong moment. The same accuracy sharpens every Sun/Moon conjunction and the Moon's position against nearby stars. | unreleased | D84 |
| Rise and set times on every info card | Open any object — a planet, a star, a galaxy — and the card tells you when it next rises and sets where you are, or that it never sets (circumpolar) or never comes up at all. During time travel the times follow the sky you're looking at. v1 could only do this for the Sun, buried in the time-travel picker. | unreleased | D51, D50 |
| A pointing readout on the map | A live corner display shows exactly where you're aimed — right ascension and declination, altitude and azimuth with a compass point, and the current field of view. v1 never told you where you were looking. | unreleased | D65, D66 |
| Tapping objects works out of the box | Tap any labeled object for its info card — including in the normal sensor-driven mode, where v1 shipped this switched off and most users never found it. | unreleased | D74, D45 |
| Labels stay put when you zoom | Object labels now sit a fixed distance from their object at every zoom level, and clear big bodies properly — a Saturn label clears Saturn's disc. In v1 labels drifted further and further from their objects as you zoomed in. | unreleased | D74, D39 |
| Faint deep-sky labels now appear at all | A 2.0-magnitude declutter bonus keeps dim deep-sky objects from losing their labels to nearby bright stars, so they're findable on the map instead of tap-only. v1's declutter rules starved them out. | unreleased | D60 |
| Bigger, more readable sky labels | The label-size ladder is re-based so the default is one notch larger than v1's — the old "Large" is the new "Medium". v1's sizes were set for the screens of 2010 and ran small on modern phones. | unreleased | D74 |
| Hundreds more star names, searchable designations | 268 new IAU-recognized star names (e.g. Mizar, Thuban, Zosma) now label the map, and you can search any bright star by its Bayer or Flamsteed designation — "Alpha Cygni", "α Cyg", or "50 Cyg" all find Deneb, even though designations don't label the map themselves. 2,195 of the 3,186 bundled stars are now searchable by name or designation (324 carry a map-label proper name); v1 knew nothing about designations. | unreleased | D63 |
| Location-accurate Moon | The Moon is drawn where *you* actually see it, not where it'd appear from Earth's centre — up to ~1° (two Moon-widths) different depending on where you stand. Fixes moonrise/set times by tens of minutes and makes the Moon line up correctly against nearby stars. | unreleased | D54 |
| Smooth panning on high-refresh screens | Flicking the sky and letting go now glides at the full refresh rate of your screen — 120 Hz where the phone has it. v1 moved the sky 20 times a second after your finger left it, which looked like a stutter on anything faster than an old 60 Hz panel. The horizon spring and the fly-to-a-search-result glide are smoother for the same reason. | unreleased | D93 |
| Correct eclipse/conjunction occlusion | When one body passes in front of another, the nearer one now correctly draws on top. v1 used a fixed ordering that showed the wrong body in front when Mercury or Venus passed behind the Sun. | unreleased | D18 |

## Known not-yet-improvements (deferred)

Things we've *scoped* as future improvements but haven't shipped — do not put these in launch
copy until they move up to the table above.

- **Sharper solar-system imagery.** The planet, Sun and Moon sprites are still v1's 16–64 pixel
  bitmaps, blurry when zoomed in and shimmering when zoomed out. Designed but not built: 256- to
  1024-pixel art, alpha-blended limbs and mipmaps. See D85 and
  [design/solar-system-imagery.md](design/solar-system-imagery.md).
- **True-scale discs for realistic eclipses and occultations.** Today every body is drawn far
  larger than life for legibility — Sun and Moon ~4.6× — so eclipses only overlap qualitatively.
  Designed but not built: a zoom-aware minimum-size floor that hands off to true scale as you
  zoom in, for every body, plus a deeper zoom limit to reach it. See D86
  (which supersedes D55).
- **Continuously accurate Moon phase, and Venus/Mercury phases.** The Moon's terminator is
  quantised to eight fixed bitmaps, and the surface features rotate with the phase. Designed but
  not built: a procedural terminator, with inner-planet phases falling out of the same mechanism.
  See D88.
- **Saturn's ring tilt and Jupiter's Galilean moons.** Designed but not built; the astronomy
  reaches the info cards before the map. See D89.

## Flag-guarded — not for launch copy

Built, shipping in the binary, but **off by default** pending rollout — and unannounced. The
flags are kill-switches and staged-rollout levers, not "unfinished" markers. As of 2.0.1, every
flag below is off in both the shipped defaults (`ExperimentConfig.Static`,
`remote_config_defaults.xml`) and live Remote Config — nothing in this table is actually live
for users yet, whatever its eventual default. The help text does not mention any of these, and
neither should store copy or release notes until they are announced and turned on. When one
ships enabled, move it into [the highlights table](#whats-new-versus-v1) (if net-new versus v1)
or [the full surface](#the-full-at-launch-feature-surface) above with a real
"What it means for users" line — and drop this note about it still being off.

| Feature | Flag | Decisions |
|---|---|---|
| Through-camera (AR) mode, with drag-to-align | `camera_ar_enabled` | D64, D67, D68 |
| Share the sky (overflow row + in-AR shutter, camera composite layouts) | `share_sky_enabled` | D70, D71 |
| Moon-phase home-screen widget | `moon_widget_enabled` | D75 |
| Tonight's-Sky and Countdown widgets | `tonight_widget_enabled` | D76 |
| Shower-peak and tonight-digest notifications | `notifications_enabled` | D77 |
| Satellite tracking — layer, pass predictor, CelesTrak fetch | `satellites_enabled` | D92 |

The warm-welcome onboarding pager used to sit in this table. It never belonged: it was
inherited from v1 as an A/B lever on the first-run funnel, not a gate on something
unannounced. Its flag was retired (D80) and it is now a launched feature —
free to describe in help text and store copy, and one of the better things to screenshot,
since every fresh install sees it and it is built on the live app chrome (D61).

One caveat if these are ever screenshotted: the in-AR controls still carry the **temporary
manual ISO/shutter dev sliders** (visible only on `MANUAL_SENSOR` cameras), explicitly slated
for removal or a dev flag once night defaults settle — see D69.
