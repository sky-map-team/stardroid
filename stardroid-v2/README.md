# Sky Map v2

The ground-up Kotlin rewrite of [Sky Map](../README.md) — the real-time planetarium that shows
you the sky you are actually standing under, offline, with no account and no ads.

v2 is currently in **beta**, shipping as an update to the existing Play listing
(`com.google.android.stardroid`). The v1 app in [`../stardroid-v1`](../stardroid-v1) remains the
reference for existing behavior until v2 fully replaces it.

**Our north star:** Sky Map is not trying to be Stellarium. It should be small, very fast,
uncluttered, and easy to use — a few things done well.

---

## What's new in v2

Everything v1 did, plus the following. Ordered by what you notice first.

### The sky is in the right place

- **The Moon is drawn where *you* see it.** v2 computes the Moon topocentrically rather than
  from Earth's centre — up to ~1° of correction (two Moon-widths), and tens of minutes on
  moonrise and moonset.
- **Eclipses peak when they actually peak.** The Sun and Moon come from full analytic series
  instead of v1's approximations, which could be ~30 minutes out — enough to send you outside
  at the wrong moment. The same accuracy sharpens every Sun/Moon conjunction.
- **Bodies occlude each other correctly.** When one passes in front of another, the nearer one
  draws on top. v1 used a fixed ordering that put the wrong body in front when Mercury or
  Venus passed behind the Sun.

### Things look like themselves

- **The Moon's phase is computed, not picked from eight pictures.** The terminator is drawn for
  the actual illuminated fraction, so the phase is right on any night rather than snapping to
  one of eight stock images — and the Man in the Moon stays still instead of rotating through
  the month. A New Moon still shows, as a dark disc lit by earthshine.
- **Venus and Mercury have phases.** A small full disc when far, a broad crescent when near.
  Mars picks up its gibbous phase too. Zoom in and watch.
- **Labels stay put when you zoom**, holding a fixed distance from their object and clearing
  large discs properly. In v1 they drifted further away the more you zoomed. Default label
  sizes are also a notch larger — v1's ladder was set for the screens of 2010.
- **Smooth panning on high-refresh screens.** Flick-and-release now glides at your screen's
  full refresh rate, up to 120 Hz. v1 moved the sky 20 times a second once your finger left it.

### You can find things

- **Tap any labelled object for an info card** — photo, description, fun fact, data rows, and
  **the times it next rises and sets where you are** (or that it never does). v1 could only do
  rise/set for the Sun, buried in the time-travel picker. Tap-to-identify is also **on by
  default** now; v1 shipped it switched off and most people never found it.
- **268 new IAU star names** on the map, and Bayer/Flamsteed designation search — "Alpha
  Cygni", "α Cyg" and "50 Cyg" all find Deneb.
- **A pointing readout.** A live corner display shows right ascension and declination, altitude
  and azimuth with a compass point, and the current field of view. v1 never told you where you
  were looking.
- **A full-screen image gallery** over the bundled celestial photography.

### What ships in the box

Entirely offline — no network needed for anything below.

| | |
|---|---|
| Stars | **3,186** |
| …searchable by name or designation | **2,195** |
| …carrying a proper name on the map | **324** (268 of them new IAU names) |
| Deep-sky objects | **135** |
| Constellations | **88** (89 figures — Serpens is drawn in two halves) |
| Solar-system bodies | **26** — the Sun, seven planets, 17 moons and a dwarf planet |
| Meteor showers | **10** |
| Celestial photographs | **250** |
| Time-travel range | **1600 – 3000** |
| Languages | **31** (UI translation coverage varies by locale) |

Counts are generated from [`source-data/`](source-data) at build time by `:data:generator`;
regenerate with `./gradlew :data:generateCatalogDb`.

Eight layers — Stars, Constellations, Deep Sky, Solar System, Meteor Showers, Grid, Horizon,
Ecliptic — plus a daylight sky gradient, and a night mode red-shifted across both the Compose
theme and the renderer at three dimming levels.

---

## Building

JDK 17. Android SDK with compileSdk 36; **minSdk 29** — a deliberate raise over v1's 26, since
sub-Android-10 devices are ~1.6% of installs and keep working v1 via in-place upgrade.

Always specify a **flavor** — there is no plain `assembleDebug`:

```bash
./gradlew :app:installFdroidDebug    # pure open source, no Google dependencies
./gradlew :app:installGmsDebug       # adds Play Services (Analytics, fused location)
```

`gms` *release* builds additionally need `no-checkin.properties`, which is not in the repo.
F-Droid builds need nothing extra.

## Testing

```bash
./gradlew check                      # unit tests (JUnit 5 + Truth), ktlint, architecture gate
./gradlew connectedDebugAndroidTest  # instrumented; needs a device or emulator
./gradlew ktlintFormat               # auto-fix most style violations
```

`check` must pass before any commit. CI runs both suites on every PR.

## Module layout

Ten Gradle modules, split hard between **pure Kotlin** and **Android**. Pure modules have no
Android SDK on the classpath at all — `import android.*` is a compile error there, enforced by
a Konsist architecture gate rather than by convention.

| Module | Type | Purpose |
|---|---|---|
| `:core:math` | pure | `Vector3`, `Matrix3`, `RaDec`, `LatLong`, angles, geometry |
| `:core:astronomy` | pure | Ephemeris, sky model, sensor-fusion math, time, rise/set, lunar phase |
| `:core:catalog` | pure | Celestial-object domain model, repository interfaces, locale fallback |
| `:core:events` | pure | Sky-events engine ("what's up tonight") |
| `:render:api` | pure | Renderer contract: primitives, camera, the shared projection |
| `:data:generator` | pure (build tool) | Deterministic catalog-DB generator over `source-data/` |
| `:render:gles1` | Android lib | OpenGL ES 1.0 backend implementing `:render:api` |
| `:data` | Android lib | Room catalog store implementing `:core:catalog` |
| `:app` | Android app | Compose UI, ViewModels, Hilt, sensors, location |
| `:konsist` | test-only | The architecture gate itself |

Dependencies point inward only: `:app → {:render:*, :data, :core:*}`,
`:render:gles1 → :render:api → :core:math`, `:data → :core:*`.

The renderer sits behind a backend-agnostic contract that takes domain and mathematical types,
never fixed-function GL concepts — so the GLES 1.0 backend can be replaced by a shader-based
one without the layers above noticing. The ephemeris engine sits behind the same kind of seam.

## Docs

[`docs/README.md`](docs/README.md) is the index: it groups every design document as built,
proposed, or reference, tracks implementation slice by slice, and carries the standing
"Not built" list. [`docs/code-overview.md`](docs/code-overview.md) is the top-down tour of the
codebase as it actually stands.

## Contributing

See the repository [contributing guide](../CONTRIBUTING.md). Pull requests need a signed
[Contributor License Agreement](../CLA.md) — a bot prompts you on the PR, it's a single comment,
and it's once ever. You keep the copyright in your contributions.

Two v2-specific things:

- **Kotlin only**, following the
  [Google Kotlin Style Guide](https://developer.android.com/kotlin/style-guide) at a
  100-character line wrap. No `m`-prefix on properties. `./gradlew ktlintCheck` enforces it.
- New strings go in **US English**; translation to other locales runs as a separate pipeline
  after a feature lands.

Comments carry `Dnn` tags (`(D51)`, `// D18: …`) marking a recorded design decision. The comment
always states its own reasoning — the tag is just an index into the decision log.

## Licensing

**GPLv3** covers all source code in this module *and* its functional resources — strings,
translations, themes, layouts and functional UI artwork. Those are part of the Corresponding
Source and are free software. A GPL §7 additional permission makes the app distributable
through application stores without conflicting with their terms.

Reserved separately is a **short, enumerated list of brand identity assets** — currently the
launcher and notification icons, the welcome backdrop, and the sky-marker icon set. Material
inherited from Sky Map v1 stays Apache-2.0, and bundled scientific imagery and catalog data
stay under their own terms (a mix of public domain and CC BY 4.0).

Every shipped asset's terms are declared in [`ASSET-LICENSES.txt`](ASSET-LICENSES.txt), which
`tools/check_asset_licenses.py` enforces in CI — an undeclared asset fails the build.
[`LICENSE.md`](LICENSE.md) and [`NOTICE.md`](NOTICE.md) are the authoritative notices; read them
there rather than relying on this summary.
