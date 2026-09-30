# Detailed Design: Supporting Screens and Startup Flow

**Status: IMPLEMENTED** (slice 16, D48) — detailed design increment 4 (the final
pre-implementation increment). The warm welcome was later rebuilt as a live-chrome tour
(D61) and its experiment flag retired (D81); per-section status notes appear inline below.
These ports were mostly mechanical; this doc records the non-obvious behavior that had to
survive and the few structural choices.

## Navigation

Single-activity Navigation-Compose graph:

```
map (start)                      settings          gallery
  ├─ dialogs/sheets owned by      diagnostics       locationManagement
  │  map features (search         compassCalibration
  │  results, object info,        onboarding (conditional start, see below)
  │  time travel, help, …)
```

*Status (D48)*: implemented. Map (or the warm welcome) is the start destination; settings,
gallery, diagnostics, and calibration are destinations; dialogs/sheets (including help and
the D43 location sheet) stay map-owned.

## Startup routing (port of `StartupRouter`)

The v1 gating logic ports verbatim — it encodes hard-won behavior:

1. **EULA**: gated on an accepted-EULA *version int* (so a future EULA change re-prompts).
   The EULA sheet carries the analytics opt-in.
2. **Warm welcome** (onboarding): fresh installs only, and behind the `WARM_WELCOME`
   experiment flag (Remote Config on gms; the fdroid `ExperimentConfig` is a static no-op
   with defaults). Completing it also marks What's New as seen and suppresses the
   missing-sensor warning — **fresh installs must never see the What's New dialog**
   (v1 fix, 2026: commit 14e83daf).
3. **What's New**: shown when the stored seen-version ≠ current app version — i.e. upgrades
   only, given (2).

State moves from SharedPreferences ints/longs to a small DataStore `StartupState`
(eulaVersionAccepted, warmWelcomeSeenVersion, whatsNewSeenVersion). Since v1 prefs aren't
migrated (D1), existing upgraders will see What's New once and (if the experiment says so)
the warm welcome — acceptable; flagged here so it's a known consequence, not a surprise.

There is no splash *activity*: the themed Compose splash (AndroidX SplashScreen API) covers
catalog-repository warmup, and routing composes the right start destination.

*Status (D48)*: implemented as designed (`StartupState` + `StartupRouter`, AndroidX
splash over the routing read). *Update (D49)*: the gms flavor now backs `ExperimentConfig`
with Firebase Remote Config; fdroid keeps the static all-off defaults.

## Settings

DataStore-backed, Compose preference screens mirroring v1's four sections (controls /
appearance / sensors / other) with all v1 keys' semantics retained: object-info-on-tap (+
auto mode), auto-level horizon, auto dimness, font size (→ `RenderState.labelScaleFactor`,
D18), sky gradient toggle (→ `RenderState.skyGradient`), gyro disable, sensor speed/damping,
reverse magnetic Z, magnetic correction toggle, manual compass adjustment, viewing direction
(STANDARD/ROTATE90/TELESCOPE), rotate horizon, sound effects, analytics opt-in.
`SettingsRepository` exposes typed Flows; consumers (`MapViewModel`, `OrientationSource`,
`RenderConnector`) collect what they need — no `OnSharedPreferenceChangeListener` fan-out.

## Diagnostics

Compose screen fed by a `DiagnosticsViewModel` observing: sensor presence/accuracy/values
(via `OrientationSource` taps), location state, network state, app/device versions. The
two-tier status color scheme (status_good/ok/warning/bad/absent with red-shifted night
variants, per AGENTS.md) becomes semantic colors in the Compose theme, switching with night
mode automatically.

*Status (D72)*: implemented as a full-screen overlay off the map. Raw sensor data comes
through the new `SensorStatusSource` (not `OrientationSource` taps — the fused stream and
the raw per-sensor taps stayed separate contracts); the polled facts are a 500 ms sampled
snapshot, as in v1.

## Compass calibration

Port of v1's screen: live magnetometer accuracy readout (`SensorAccuracyMonitor`),
figure-eight instruction content, "don't show again" preference, and the auto-prompt when
accuracy degrades on the map screen. The future device-calibration mode (manual bias
correction, roadmap) will extend this screen — keep its ViewModel separate from Diagnostics.

*Status (D72)*: implemented as a full-screen overlay; the monitor ported as a cold prompt
flow the map collects, and the figure-eight gif plays via `ImageDecoder` instead of v1's
WebView wrapper (`ImageDecoder` shipped in API 28, within our minSdk).

## Gallery

v1's `ImageGalleryActivity` + `GalleryAdapter` become a Coil-backed `LazyVerticalGrid` over
the same `celestial_images` assets (catalog-DB `image_ref`s later, when packs bring imagery).
Tapping an image offers "find in sky" (routes into the search flow by object id) — v1's
gallery→search wiring, kept. Full-screen view is a zoomable Compose overlay replacing
`ImageExpandDialogFragment`.

*Status (D47)*: implemented as a full-screen overlay off the map (the Navigation graph still
doesn't exist), fed by `CatalogRepository.galleryItems` — the DB's `image_ref`s, not an asset
walk. Coil stayed out (D44 decodes assets directly; the grid adds downsampling + an LRU);
"find in sky" goes through the info card's Find button, and the zoomable expand overlay
serves both the gallery and the map card.

## Onboarding (warm welcome)

v1's 436-line activity becomes a Compose pager: value proposition, sensor/location
permission priming, and the calibration nudge. Content unchanged for the initial port
(UX latitude per D11 applies to styling, not flow). Analytics events preserved
(warm-welcome funnel was recently instrumented in v1 — see
`specs/warm-welcome-analytics/`).

*Status (D48)*: implemented as a Compose pager (VM-less; sensor presence via
`SensorStatusSource`). Analytics funnel events wait for the analytics edge (D45).

## Help / Credits / EULA content

v1 renders bundled HTML assets. Initial port keeps the HTML in a (themed) WebView wrapped in
Compose — converting years of localized HTML to native Compose text is cost without user
benefit. Revisit only if night-mode theming of the WebView proves ugly.

*Status (D48)*: went the other way — v2 has no localized HTML corpus to preserve, so the
content ports as HTML string resources rendered with `AnnotatedString.fromHtml` (no
WebView). Credits folds into the help document, as v1's `help_text` already embedded it.

*Update (search and deep links)*: two complaints from #1030 — "the help page is very long and
unsearchable", and that finding a feature in the text still left you to go find it in the app
— closed #1032 and #1064 together.

The document is no longer one concatenated string. `HelpDocument.kt` holds it as an ordered
list of sections, each with a permanent anchor id, rendered into a `LazyColumn`. That buys
three things a single string could not offer:

- **Search.** A persistent box under the top bar filters the document to the sections matching
  what is typed, with the term washed in `DocumentColors.searchHighlight`. Matching is
  case- and diacritic-folded (`ui/common/TextSearch.kt`) because the document ships in 28
  locales — a French reader typing "etoile" has to find "étoile". `<h1>` dividers drop out of
  a filtered list, since a bare section title above filtered results titles nothing.
- **Deep links.** `htmlWithLinks` now passes `AnnotatedString.fromHtml` the
  `linkInteractionListener` it always accepted, so a private `skymap://` scheme in the help
  strings routes into the app: Settings, Diagnostics, Calibrate, Gallery, the tutorial replay,
  the system's per-app settings, the widget catalogue, and `skymap://help#<anchor>` for another
  part of the same document. Still no WebView, so D48 holds.
- **Anchors.** "See the troubleshooting section below" is now a link that scrolls there.

Each destination pushes above Help, so Back returns the reader to the paragraph they left —
with one exception, `skymap://tutorial`. The warm welcome's `leaveWelcome` pops back to the map
(`popBackStack(MAP, inclusive = false)`), which drops Help from the stack on the way. That is
the pre-existing replay behaviour and arguably right, since the tutorial points at the map's
own controls, but it does mean the tutorial link is the one that does not come back.

Two things worth knowing about the mechanism:

- Supplying a link listener **replaces** Compose's default URI opening, so `htmlWithLinks` has
  to hand the document's ordinary `https:`/`mailto:` hrefs to `LocalUriHandler` itself. Wiring
  deep links without that branch silently breaks the four real links help.xml already had.
- Help raises its **own** `WidgetsSheet` rather than navigating to the map's. A sheet is not a
  destination — nothing outside `MapScreen`'s composition can flip its boolean — and routing
  there would mean a pending-action channel from the nav host for one link, while costing the
  reader the one-tap add they have today. The sheet is stateless and doesn't touch the map, so
  two screens can each raise it. This is why `HelpScreen` keeps `experimentConfig`.

The hrefs live in translatable strings, so `parseHelpLink` returns null for anything it does
not recognise — a mistranslated target leaves the link inert rather than crashing the screen —
and `HelpLinksTest` scans every locale's `help.xml` to assert no locale carries an app link
English does not. That test is containment, not equality: a locale behind an English copy edit
is merely stale, which `tm languages` reports.

## Location management

The v1 screen (recent `002-modern-location` work) ports as a destination owned by
`LocationViewModel`: current provider state, manual lat/long entry with validation,
permission rationale and permanently-denied paths, acquiring-timeout dialog. The gms/fdroid
split stays behind v1's `LocationProvider` interface shape (fused vs `LocationManager`).

*Status (D43)*: implemented as a `ModalBottomSheet` off the map screen plus state-driven
Compose dialogs. *Update (D49)*: the gms flavor binds the fused provider (with a platform
fallback when Play Services is unusable); fdroid keeps the platform `LocationManager`.

## Analytics

`AnalyticsInterface` port: Firebase implementation (gms) / no-op (fdroid), opt-in gated.
Event set carried over (session-length buckets, preference-change tracking, warm-welcome
funnel, search usage). New v2-specific events wait until after the port (faithful-port rule).

*Status (D49)*: implemented — `Analytics`/`AnalyticsEvents` with v1's event names verbatim,
the `enable_analytics` opt-out in settings, events wired at the ViewModels; see D49 for the
handful of v1 events with no v2 hook.

## Accessibility scope (D24)

The font-size preference drives `RenderState.labelScaleFactor` (D18), so sky labels honor it.
The Compose chrome inherits Material 3 RTL, contrast, and TalkBack support for free. Deep
accessibility of the GL sky surface itself — TalkBack descriptions of celestial content — is
**out of scope for the faithful port** (v1 has none) and revisited as a post-port feature; this
is a recorded scoping decision, not an oversight. No full RTL/screen-reader audit is part of the
port beyond what Material 3 and `labelScaleFactor` provide.

## Testing

Startup routing is the one genuinely fragile piece: `StartupRouterTest` ports with its cases
(fresh install sees warm welcome not What's New; upgrade sees What's New; EULA re-prompt on
version bump; experiment-off path). Screens get one Compose test each on their critical
interaction; settings round-trip through DataStore typed accessors.
