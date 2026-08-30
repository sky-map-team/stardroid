# At-launch feature set

The complete, audited list of what a user gets on day one — the input for help text,
promotional copy, and screenshot selection.

**Scope.** Everything here is **unflagged and unconditional**: no experiment gate, no
device-capability condition beyond the obvious (sensors, camera). Features that are built
and shipping but *unannounced* are deliberately excluded — they live in the flagged table
in [improvements-over-v1.md](improvements-over-v1.md#flag-guarded--not-for-launch-copy).

**Related docs.** [improvements-over-v1.md](improvements-over-v1.md) is the narrower
"what's new versus v1" list for release notes; this file is the whole surface, including
faithful v1 ports. [README.md](README.md) tracks implementation history by slice.

**Audited** 2026-08-04 against the code, not the docs; **counts re-verified 2026-08-27**
against a regenerated `skymap.db` (`./gradlew :data:generateCatalogDb`), which is the
authority — `source-data/` holds pre-filter rows. Re-verify before reuse if the catalog has
changed.

## Read this before writing copy

Two constraints that would otherwise produce false claims:

- **UI translation is partial, and uneven by locale.** 31 locale directories ship, but the
  salvage carried 213 of 337 strings (~63%) over from v1 — most locales sit around **50%
  coverage**, so a non-English user sees a mix of translated and English text. The help
  document is not translated at all yet: it was split into 14 per-section keys and awaits
  retranslation (D78). Catalog data (object names, info cards) is separately localized and
  in better shape. **Safe to say the app speaks 31 languages; do not claim it is fully
  translated, and check per-locale coverage before shooting non-English screenshots.**
- **Comets are the one unported v1 layer** (D37, D58). Any v1 material mentioning comets
  does not carry over.

## Sky rendering

- Real-time sky driven by device sensors; auto (sensor) ⇄ manual mode toggle
- Drag, pinch-zoom, and rotate; double-tap toggles horizon auto-leveling
- **Eight layers** — Stars, Constellations, Deep Sky, Solar System, Meteor Showers, Grid,
  Horizon, Ecliptic — reachable from the layer rail; long-press opens the full sheet
- Sky gradient (the daylight dome) as a separate display toggle
- Night mode, red-shifted across both the Compose theme and the renderer, with three
  dimming levels
- "Deep Space / Star Gold" brand color scheme (D73)
- Chrome auto-hides after a few seconds; a sky tap brings it back

## Catalog — bundled, fully offline

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
| Catalog-data locales | **30** (see the translation caveat above) |
| UI locales | **31**, partially translated (~50% coverage in most) |

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

## Find and identify

- As-you-type ranked search, doubling as v1's "Did you mean?"
- RA/Dec coordinate entry
- A successful search re-enables the target's layer if hidden, then slews to it
- Pulsing crosshair plus a red-near→blue-far bearing arrow
- **Tap-to-identify, on by default in sensor mode** — v1 shipped this switched off (D74)
- Info cards: photo, credit, description, fun fact, data rows, see-also chips, and
  **rise/set times** at your location
- Image gallery: a full-screen, pinch-zoomable grid over the bundled photos

## Time travel

- Year range **1600–3000**
- Speed ladder with slower / faster / pause / now controls; the player shows its own state
- Presets: next sunset, next sunrise, next full moon, next new moon, plus dated 2026 events
  (lunar and solar eclipses, Perseids, Geminids, Lyrids, the six-planet parade, Venus–Jupiter
  and Mars–Uranus conjunctions, a Jupiter occultation)
- Purple travel flash on entering and leaving time travel (the whoosh sound effects that
  accompanied it were removed in D111 — the app now has no audio at all)

## Position and sensors

- Automatic location (fused provider on gms, platform fallback), manual entry with
  place-name geocoding, and a static map of the confirmed fix
- **Map HUD**: right ascension / declination, altitude / azimuth with a compass point, and
  the current field of view
- Diagnostics screen with live per-sensor status
- Compass calibration screen with the figure-eight animation
- Legacy-sensor path, magnetic declination correction, and magnetometer Z-axis reversal for
  devices with bad or mis-mounted sensors

## Onboarding and support

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
- 31 UI locales, partially translated — see the translation caveat above (D78)

## Accuracy and quality wins over v1

- **Location-accurate Moon** — topocentric, correcting up to ~1° of placement error and
  tens of minutes on moonrise/set (D54)
- **Correct eclipse and conjunction occlusion** — the nearer body draws in front (D18)
- Labels hold a fixed pixel distance from their object at every zoom level (D74)
- Faint deep-sky labels now appear at all, via a 2.0-magnitude declutter bonus (D60)
- Bigger default sky labels — the ladder is re-based so old "Large" is the new "Medium" (D74)

## Screenshot shortlist

Strong, safe, and all unflagged: the map with constellations; an info card showing rise/set
rows; the gallery grid; the time-travel player mid-travel; search with the bearing arrow;
night mode; the map HUD readout; and the warm-welcome tour — the single most-seen surface in
the app, and it shows real UI rather than a mock.

Avoid anything from the flagged set — see the caveat on the temporary in-AR exposure
sliders in [improvements-over-v1.md](improvements-over-v1.md#flag-guarded--not-for-launch-copy).
