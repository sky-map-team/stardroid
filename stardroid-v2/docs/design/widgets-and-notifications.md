# Widgets & Notifications ("stickiness" surfaces)

**Status: IMPLEMENTED through phase 3** — architecture and the moon widget (D75), the
`:core:events` engine plus the tonight and countdown widgets (D76), and the shower-peak and
tonight-digest notifications (D77). All of it ships behind experiment flags and is excluded
from launch copy (D80). **Phase 4 is not built** and remains a sketch; the phasing table
below marks each row. UX reference: [mockups/stickiness.html](mockups/stickiness.html).

Home-screen widgets and rare, high-signal notifications, all computed offline from the
shipped catalog and ephemeris. Product principles (from the mockup, binding for all
phases):

- **Quiet by default** — every notification channel ships off; widgets are the default
  stickiness surface because placing one is itself the opt-in.
- **Rare and actionable** — at most one notification per channel per day, only when there
  is something to do tonight; quiet nights are skipped, not filled.
- **We do the astronomy** — every surface carries the judgment a calendar can't: moon
  interference, altitude, local darkness hours.
- **One engine, many faces** — widgets and notifications are renderers over one offline
  events scan; the engine is built once, in phase 2.

## Phasing

| Phase | Surface | New infrastructure | Status |
|---|---|---|---|
| 1 · Moon widget | Glance widget + info-card promo row + pin dialog | Glance/WorkManager plumbing, `moon_widget_enabled` flag | **Built** (D75) |
| 2 · Tonight widget | Tonight digest widget (+ countdown as near-free sibling) | `:core:events` engine v1 — N-day scan → ranked (event, time, quality) | **Built** (D76) |
| 3 · Notifications | Shower peak + tonight digest, settings screen, permission flow | Channels, digest scheduler, engine suppression threshold | **Built** (D77) |
| 4 · If wanted | Conjunctions, moon phases, rise alerts, rise/set widget | Engine minima scan; per-object watch list | **Proposed** — not built |

Phases 2 and 3 got their own design passes (D76, D77) before implementation; phase 4 still
needs one. This doc fixes the architecture they all fit.

## Architecture: pure core, thin platform faces

Portability dictates the layering: **everything except the last-mile rendering
lives in pure Kotlin modules**. The pure modules today
use `skymap.pure-kotlin` (kotlin-jvm); converting them to multiplatform later is a
plugin swap that stays mechanical only while they avoid JVM-only APIs — kotlinx-datetime
is already multiplatform. The Konsist gate (D20) already keeps Android out; new pure code
should also shun `java.*` so the swap stays clean.

| Layer | Android home |
|---|---|
| Astronomy (phase, illumination, rise/set) | `:core:astronomy` (exists) |
| Display models (`MoonWidgetModel`, later `SkyEvent`) | `:core:astronomy` now; `:core:events` from phase 2 |
| Rendering, scheduling, flags | `app/` — Glance, WorkManager, `ExperimentConfig` |

Consequence for phase 1: the widget's Kotlin/Glance code contains **no astronomy** — it
formats a `MoonWidgetModel` and draws a disc from two numbers (illuminated fraction,
waxing flag).

## Phase 1: the moon phase widget

2×2 Glance widget: drawn phase disc (correct terminator, hemisphere-correct orientation),
phase name, illumination %, today's moonrise/set. See mockup widget A.

### Pure layer — `:core:astronomy`

New file `MoonWidgetModel.kt` (name mirrors its consumer, content is plain astronomy):

```kotlin
data class MoonWidgetModel(
    val phase: LunarPhase,            // lunarPhaseBucket(), exists
    val illuminatedFraction: Double,  // (1 + cos(phaseAngleDeg)) / 2, from Ephemeris
    val waxing: Boolean,              // phase angle decreasing, as lunarPhaseBucket does
    val mirrored: Boolean,            // southern-hemisphere view: terminator flips
    val riseTime: Instant?,           // nextRiseSetTime(MOON, …), null when no location
    val setTime: Instant?,
)

fun moonWidgetModel(time: Instant, location: LatLong?): MoonWidgetModel
```

`illuminatedFraction` is a new one-line addition next to `lunarPhaseBucket`;
rise/set reuses the D51 solver. `location == null` (fresh install, widget placed before
the app ever ran) degrades to a geometry-only model — the widget then omits the times
row rather than guessing.

Tests: golden values against a published almanac for a handful of dates (illumination,
phase bucket, rise/set within the ephemeris' documented few-minute tolerance), both
hemispheres for `mirrored`, null-location degradation.

### App layer — new `widget/` package

- `MoonWidgetReceiver : GlanceAppWidgetReceiver` + `MoonWidget : GlanceAppWidget`.
  Layout per mockup: brand navy surface at 86% opacity, 22 dp radius, Star Gold for the
  illumination figure only. Text follows Glance day/night for contrast; the navy stays
  (brand decision, mockup note "shared mechanics").
- `MoonDiscRenderer` — draws the disc to a `Bitmap` with android Canvas from
  (`illuminatedFraction`, `waxing`, `mirrored`): lit circle, terminator ellipse with
  semi-minor axis `r·|2f−1|`, few fixed maria blotches as in the mockup. No image
  assets; any drawable we did ship would live under `res/` (All Rights Reserved) —
  drawn-from-math avoids the question entirely.
- `MoonWidgetRefreshWorker`: calls `updateAll`; the model is computed inside
  `provideGlance`, which reads `Settings.savedLocation` (DataStore — the location
  snapshot; **never** wakes GPS) through a Hilt `@EntryPoint` (no `hilt-work` artifact
  needed). Scheduled as unique periodic work every 12 h (rise/set are per-day figures;
  12 h keeps them fresh across midnight without exact alarms); placement itself triggers
  the first composition, so no one-shot is needed.
- Manifest: receiver + `appwidget-provider` metadata — `targetCellWidth/Height 2×2`,
  `previewLayout` (API 31+ live preview), `description`, `widgetCategory home_screen`,
  resizable both axes with 2×2 as the minimum.
- Tap action: open `MainActivity` deep-linked to the Moon on the map (same intent the
  search result uses).

New catalog entries: `androidx.glance:glance-appwidget` and
`androidx.work:work-runtime-ktx`.

### Experiment flag

`Experiment.MOON_WIDGET("moon_widget_enabled")`, **default true** everywhere:

- `remote_config_defaults.xml` entry `true` (gms shipped default before first fetch).
- `ExperimentConfig.Static` returns true for it (fdroid — no Remote Config there, so for
  fdroid this is effectively a shipped constant, as with `WARM_WELCOME`).

Gating mechanics (the flag is a kill switch, so it must bite everywhere the widget
surfaces):

1. **Picker/pin visibility** — on app start (and on each Remote Config activation),
   `PackageManager.setComponentEnabledSetting` on `MoonWidgetReceiver`:
   `COMPONENT_ENABLED_STATE_ENABLED/DISABLED`. Disabled removes it from the widget picker
   and from `requestPinAppWidget`. Note the standard Remote Config caveat: a flag flip
   reaches clients on their next fetch cycle (12 h throttle), not instantly.
2. **Refresh path** — `WidgetRefreshWorker` exits without updating when the flag is off
   (covers instances placed before a flip; they freeze rather than crash, and the
   component disable prevents new ones).
3. **Promo row** — the info-card row checks the flag before offering.

### Discovery (ships with the widget, not after)

- Promo row at the bottom of the Moon's object-info UI (`ui/objectinfo/ObjectInfoUi.kt`):
  "Keep the moon on your home screen → Add widget" → `requestPinAppWidget` (one-tap
  system dialog; minSdk 29 ≥ API 26 so it's always available, but
  `isRequestPinAppWidgetSupported` still guards launchers that opt out — fallback is a
  short instruction dialog). Dismissed once → never shown again (DataStore flag).
- Settings → Widgets gallery row and the one-time what's-new card are phase-1-optional;
  the promo row is the primary path (mockup, Discovery section).

### Explicitly out of scope for phase 1

Material You dynamic color, night-mode red-shifting, widget resizing, the events engine,
any notification code. All recorded as open questions in the mockup.

## Open questions

Carried in the mockup's "Open questions for the decision log" section; none block
phase 1.
