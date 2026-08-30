# UX polish: responses to the first hands-on feedback round

**Status: IMPLEMENTED**, with one loose end. Slice 20 shipped the quick fixes (D52); slice 21
shipped items 3–6 (D53); item 1's visual design was agreed 2026-07-11 (D56) and shipped in
slice 22 (D57); item 7 shipped in slice 23 (D58). The only part still **proposed** is item 2's
second step — the GL sky cross-fade — its first step (the branded splash icon) is in. Each
section quotes the feedback it answers, and carries its own status marker. A **second**
feedback round (D60) is written up under "Round 2" at the end of this doc; every Round 2 item
is fixed.

## 1. The map chrome ("a jumble of plain buttons") — ✅ done (slice 22, D57)

> The dated 3 dots menu is gone, but instead we just have a jumble of plain buttons at the
> bottom of the screen with nothing to distinguish them. … It should look similar to Sky Map
> v1 (the family resemblance should be obvious) while still Material 3 compliant.

Slice 20 already made the chrome tap-to-show/auto-hide (v1's `FullscreenControlsManager`
behavior) and hides it entirely during a search. What follows is the **agreed visual
design** (reviewed on interactive mockups, D56), **implemented in slice 22** (D57,
`ui/map/MapChrome.kt`); it replaced the previous eleven equal-weight `FilledTonalButton`s
in a `FlowRow` with a three-zone chrome. Everything stays inside the existing
`AnimatedVisibility` container, so tap-to-show/auto-hide and hide-during-search apply
uniformly, and night mode re-tints all of it through the red scheme (D46).

### Zone A — the layer rail (left edge, vertically centered)

A slim vertical column of `IconToggleButton`s in a translucent pill (scrim ~60% over the
sky, hairline outline), sitting where v1's sliding sidebar sat. Contents, top to bottom:

1. Object layers: stars, constellations, deep sky, solar system, meteor showers (D37).
2. *Thin divider*, then the non-object reference elements: grid, horizon.
3. *Thin divider*, then an **expand button** (M3 "tune"/sliders glyph — a chevron on a
   left-edge rail reads as "move this panel") that opens the Layers sheet. Long-press on
   the rail is a bonus gesture for the same thing, never the only entry.

Icons are v1's metaphors (`star_on/off`, `planet_on/off`, `b_meteor_on`, `grid_on`,
`horizon_on`, `stars_on` = constellations, `deep_sky_objects_on`…) redrawn as stroke
vectors. State is shown v1-style by tint, not by container: checked = primary color plus a
~14% primary pill behind the icon; unchecked = `outline`/dim grey. The rail carries the
family resemblance through silhouette and placement; the *tint follows the theme* (M3
baseline lavender by day, red at night) rather than porting v1's orange, which would fight
the night palette. (If a stronger v1 color identity is ever wanted, the right move is a
custom day `ColorScheme` seeded from v1 orange — a theme change, not a rail change.)

**Rail budget.** Every rail item keeps the 48 dp minimum touch target (M3/accessibility
baseline); the visual pill inside stays ~36 dp so the rail still reads slim. Landscape is
the binding constraint: on a ~393 dp-tall landscape phone only about seven 48 dp slots fit
after safe-area padding — not all eight (seven toggles + expand). Rail membership therefore
borrows the action-bar mechanism: each item is **always**, **ifroom**, or **never**
(sheet-only). The expand button and the core object layers (stars, constellations, solar
system, deep sky) are `always`; meteor showers, grid, and horizon are `ifroom` and drop
from the end of the rail — never from the Layers sheet — when height runs out. **New layers
default to `never`** (satellites, comets, whatever comes). If the primary set ever needs
to be user-chosen, per-row pins in the Layers sheet controlling rail membership layer
cleanly on top of this design (same rail, same sheet, one pin per row); deliberately not
built now.

### Zone B — primary actions (bottom-right)

The four actions used *while looking at the sky*, as icon buttons, plus overflow:

- **Search** — the sole `FilledIconButton` (primary container). It's the marquee action;
  making all four filled-primary would recreate the jumble in miniature.
- **Time travel**, **Night mode** (moon ↔ sun glyph swap), **Auto/Manual**
  (compass ↔ hand glyph swap) — `FilledTonalIconButton`s.
- **⋮ More** — a plain `IconButton` opening the overflow sheet. Three-step visual
  hierarchy: filled → tonal → bare.

In **landscape** the cluster moves to the right edge as a bottom-anchored column (roughly
where v1's right panel sat); the rail stays on the left. Both edges use the existing
safe-drawing/cutout padding.

### Zone C — the overflow sheet (⋮)

A `ModalBottomSheet` of icon + label `ListItem` rows: Gallery, Location, Diagnostics,
Calibrate, Help, Settings. These are destinations and one-shot actions — things that are
*not* sky-drawing controls — so they don't earn permanent pixels. A future **Share** action
belongs here too.

### The Layers sheet, and how the two sheets relate

The two sheets are siblings (same anatomy, both modal over the map) with distinct jobs:
the ⋮ sheet **navigates away** from the map; the Layers sheet **configures** what the map
draws while you stay put. Neither embeds the other. The rail is not a third concept — it is
a shortcut surface showing the primary subset of the Layers sheet, which is the canonical,
unbounded list.

The Layers sheet replaces the current "boring list of check boxes": the same vector icons,
labels, and M3 `Switch`es, grouped to mirror the rail:

1. Object layers (stars … meteor showers, plus future sheet-only layers).
2. Reference: grid, horizon.
3. Display: **sky gradient**, and the future **through-camera / AR mode** toggle — display
   modes live here, not in the rail, unless usage proves they deserve promotion.

Toggling a layer in the sheet updates the rail icon's tint immediately (single source of
truth: the existing layer-visibility preferences).

### Gestures (recorded for the implementation slice)

- Double tap in manual mode toggles horizon auto-leveling (slice 20 behavior — it does
  *not* switch Auto/Manual; an earlier draft of this section misstated that).
- Open item: a gesture for Auto ↔ Manual itself (candidates: two-finger double-tap,
  long-press on the sky). Not part of this slice; the Auto/Manual button is the way.

### Implementation notes

- Chrome container, search-hiding, linger timing: unchanged from slice 20.
- Icon assets are new stroke vectors (24 dp grid, `currentColor` tint) — original drawings
  in the style of v1's metaphors. They are brand assets: they go in
  `app/src/main/res/drawable/` and are **All Rights Reserved** under the split-license
  rules, like everything else in that tree.
- M3 components: `IconToggleButton` (rail), `FilledIconButton`/`FilledTonalIconButton`/
  `IconButton` (actions), `ModalBottomSheet` + `ListItem` + `Switch` (sheets),
  `Tooltip` on the action buttons for labels.
- An interactive HTML mockup of all of the above (day/night, both sheets, the four
  rail-to-sheet alternatives evaluated, and the pinnable-rail future) was reviewed on
  2026-07-11; the agreed variant is the one described here.

## 2. Splash screen — ⚠️ step 1 done, step 2 still proposed

> On first open the splash screen is plain and generic

v2 uses the AndroidX `SplashScreen` API. Two-step proposal:

1. **Quick — ✅ done:** a branded `windowSplashScreenAnimatedIcon` (the Sky Map logo) on
   `Theme.SkyMap.Starting`, over `splash_navy` from the brand palette (D73); assets live in
   `app/src/main/res/` (All Rights Reserved per the split-license rules).
2. **Richer — proposed, not built:** v1 faded a full-bleed night-sky photograph
   (`stardroid_big_image`) into the
   app. The modern equivalent: keep the system splash minimal, then cross-fade the GL sky in
   from black over ~1 s on first composition (the renderer already starts black), which
   reads as "the sky reveals itself" rather than a static plate. No extra asset needed.

## 3. EULA / What's New / Help dialogs — ✅ done (slice 21, D53)

> The EULA and What's new dialogs are ugly. The v1 versions had color and better spacing.
> … The Help text is ugly compared to v1. No color.

v1 rendered these in a WebView with `help.css`: colored headings (h1 `#56B0F5` blue, h2
`#F5B056` amber, h3 `#F67E81` salmon), a callout box, 1.5 line height. D48 moved v2 to
native `AnnotatedString.fromHtml`, which flattens all of that to default typography.

Slice 20 fixed the invisible links (theme-colored + underlined) and the cutout intrusion.
**Shipped in slice 21:** the `StyledHtml` composable (`ui/common/StyledHtml.kt`) splits a
document on its h1/h2/h3 tags and renders headings via `MaterialTheme.typography`
`headlineSmall`/`titleLarge`/`titleMedium` in the v1 accent colors (night-mode variants
red-shifted), with paragraph spacing; body runs keep the D48 `fromHtml` path — no WebView.
Reused by EULA, What's New, Help, and the calibration screen. Help is now a full-screen
Navigation destination and the EULA a full-screen startup gate, both with a collapsing
`LargeTopAppBar`; What's New stays a (now styled) dialog since it's short.

## 4. Time travel state visibility — ✅ done (slice 21, D53)

> Time travel has no visual indication of whether you are travelling in time and how fast

The `TimeTravelPlayer` (top center whenever the clock isn't real-time) already shows the
simulated date/time, a rate label ("Traveling > @ 1 day/sec", "Time travel frozen"), and
slower/pause/faster/return controls. That it wasn't noticed is itself the finding: it reads
as a passive caption. **Shipped in slice 21**, as proposed:
- the player gets a `tertiaryContainer` surface while the rate ≠ 0 (calm `surface` when
  frozen), so motion has a color — night mode gained explicit red `tertiary*` roles;
- direction glyphs (◀◀ / ▶▶) prefix the rate label, sized by the speed step, mirroring
  v1's `<`/`>` labels;
- the date readout ticks in with a slide-up `AnimatedContent` while the clock is sweeping.

## 5. Toasts — ✅ done (slice 21, D53)

> The app uses Toasts. Are they still a thing in modern android?

They still work and are not deprecated (only custom toast *views* are), but they float
outside the app's theming — night mode cannot tint them, which matters for an astronomy
app. **Shipped in slice 21:** a single `SnackbarHost` on the map screen (state owned by
`SkyMapNavHost`, so calibration-complete can enqueue before popping back to the map); all
six call sites migrated (calibration ×2, time travel ×2, location fix, sun-won't-set, plus
slice 20's auto-level toggle). Snackbars follow the M3 color scheme and red-shift in night
mode (the night scheme gained red `inverse*` roles); "Undo" on the auto-level toggle and
"Open" on the calibration nudge carry actions.

## 6. Location picker map — ✅ done (slice 21, D53)

> Location - where did v1's map go?

v1's `LocationManagementActivity` shows a **static Geoapify map image** in a plain
`ImageView` (no Play Services dependency, so it works in both flavors), red-tinted in night
mode. **Shipped in slice 21:** ported into v2's `LocationSheet` — a Coil
`SubcomposeAsyncImage` to the Geoapify staticmap endpoint centered on the confirmed fix,
lat/long readout beneath, `NightPhotoTint` multiply in night mode. The key loads from the
git-ignored `app/no-checkin.properties` (`geoapify.api.key`, v1's scheme) into a
`resValue`; key-less builds stay text-only and a failed load shows v1's Map Unavailable
label. Both flavors ship the key, following v1's precedent.

## 7. Meteor-shower layer — ✅ done (slice 23, D58)

> Searching for meteor showers doesn't work - nothing gets found. That layer isn't listed
> in layers - looks like it was missed.

Not missed — deferred by D37; slice 20 gave the ten showers their radiant positions so
search now aims at them. **Shipped in slice 23 (D58):** `MeteorShowerLayer` renders a
radiant icon + label per active shower over a new `meteor_shower` catalog sidecar table
(activity window + peak ZHR, v1's IMO-2011 data), swapping to the denser v1 glyph when the
linearly interpolated rate tops v1's 10/hour threshold. Toggleable from the rail (`ifroom`
group, D57), auto-enabled by a shower search, with `search_fov` 45° and see-also links from
each shower's info card to its host constellation(s).

## 8. Answered in slice 20 (for the record)

- Calibration: 10 s startup grace; video link now a real link; text verified against v1's
  latest wording (v2 already carried it).
- "Find in Sky Map" → v1's "Find in sky", gallery-only.
- Sensor settings: "Use legacy sensors" rename; speed/damping/reverse-Z hidden unless on.
- Labels vanishing near the long screen edges: cull cone now scales by the long/short
  viewport ratio (was v1's landscape-only formula).
- Search: layer auto-enables on a successful search; RA/Dec entry formats shown under the
  field; cancel bar no longer collides with the buttons (chrome hides in search mode).
- Double tap in manual mode toggles horizon auto-leveling.

---

# Round 2: second hands-on feedback round (D60)

Eight issues from a second device test, all fixed on `feature/ux-feedback-batch`.

## R2.1 Faint deep-sky objects never labeled

> Some DSOs never show labels even at very high zoom (e.g. the Ring Nebula)

The per-frame label filter uses a shared FOV→magnitude threshold
(`LabelDeclutterer.magnitudeThreshold`) that rises as you zoom in but bottoms the FOV at
`MIN_FOV_DEG = 0.5°`, capping the threshold near **8.4**. The Ring Nebula is mag 9.0, so its
label was gated at *every* reachable zoom. Fix: deep-sky labels carry a fixed **2.0-mag
decluttering bonus** (`DeepSkyMapping.label`, `CatalogLayers.DSO_LABEL_MAGNITUDE_BONUS`) — the
declutterer sees M57 as mag 7.0 and shows it once zoomed in, while stars (which pass no bonus)
stay gated. Chosen over lowering the global curve, which would also surface fainter stars; DSOs
are few and each named one is worth a label. Decluttering *priority* keeps the true magnitude.

## R2.2 Time-travel controls under the camera cutout

> The Time Travel controls appear under the camera cut out

The app's fullscreen theme hides the status bar, so the player's `statusBarsPadding()` resolved
to ~0 and it slid under the notch. Added `displayCutoutPadding()` to the top-center player box
(`MapScreen`). See R2.4 for the same root cause on the full-screen destinations.

## R2.3 Search "Found:" box text unreadable

> a box appears saying "found: <object>" but the text is black and almost impossible to read

`SearchControlBar`'s `Surface` set a translucent `surface.copy(alpha = 0.8f)` color but no
`contentColor`; because that isn't an exact M3 role, `contentColorFor()` returned Unspecified
and `Text` fell back to black. Fix: `contentColor = MaterialTheme.colorScheme.onSurface`.

## R2.4 Titles behind the cutout (Gallery, Calibrate, Help, …)

> "Sky Map Gallery" is behind the camera cutout … Same with the calibrate dialog and Help.

Same root cause as R2.2: under the fullscreen theme the M3 `TopAppBar` default insets
(`systemBars`) collapse and never include the display cutout. Introduced one shared
`ui/common/topBarWindowInsets()` = `WindowInsets.safeDrawing.only(Top + Horizontal)` and applied
it to every opaque full-screen destination's top bar — Gallery, Help, Settings, Diagnostics,
and the calibration screen — so notches are cleared in portrait and landscape. Also shortened
the two verbose titles: "Sky Map Gallery" → **Gallery**, "Help for Sky Map" → **Help**.

## R2.5 "Set Location" should resolve a typed place before closing

> if the user enters a place name it's easy to forget to tap resolve … "Set Location" should
> resolve before closing the dialog.

Resolve and Set-Location were fully decoupled: confirm only read the coordinate fields, which a
place name populated only via the Resolve button. Added a suspend `confirmManualLocation` on
`LocationViewModel`: if a place name is present and not already resolved into the current fields,
it geocodes first, applies the result, then dismisses; a failed lookup leaves the dialog open
with the error. Already-resolved places skip the redundant lookup (`resolvedQuery`).

## R2.6 Help text out of date

> The Help text needs editing to match the new version.

Audited every UI claim against the current source and corrected the mismatches: time-travel
range **1600–3000** (was 1900–2100) and **Go / Start from now** (was "Go!"); playback described
as the **◀◀ / ❚❚ / ▶▶ / Now** stepper; **Celestial grid**, **Ecliptic**, **Meteor showers**,
and **Sky gradient** as their own layers (no combined grid); search exit is **Cancel** (not ✕);
**Use legacy sensors** (was "Disable Gyro"), **Damping**, **Auto screen dimming**, **Additional
compass adjustment**, **Use magnetic correction**.

## R2.7 Warm welcome rebuilt

> The Warm Welcome is now broken … just has some Explore the Cosmos text, out of date, and no
> visual guidance. Tab 2 has a picture of a nebula … Tab 3 shows the 3 sensors [v1 checked them
> one by one with haptic feedback].

Nothing crashed — "broken" meant out-of-date and unguided versus v1. Rebuilt all three slides:

- **Slide 1 — "Find Your Way Around":** a Compose-drawn navigation guide (a stylized phone with
  a mini sky and a bottom control bar, plus a color-keyed **Search / Time travel / Layers**
  legend) replaces the stock "Explore the Cosmos" text. Chose Compose illustrations over porting
  v1's annotated screenshot, which showed v1's chrome and would be stale against the shipped
  three-zone redesign (D56/D57).
- **Slide 2:** the generic Crab-Nebula photo is replaced by a **tap → info-card** illustration —
  an actual v2 feature rather than a stock image.
- **Slide 3:** the sensor list now **animates one sensor at a time** (0.8 s cadence, spinner →
  result) with **per-sensor haptics** — a happy tap when present, a double-buzz when absent —
  porting v1's `WarmWelcomeActivity.buzz()` (`ui/onboarding/SensorCheckHaptics.kt`) behind a new
  `VIBRATE` permission.
