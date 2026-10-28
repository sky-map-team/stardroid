# Info Card Live Content: "Now" facts and "Upcoming" events

**Status: IN PROGRESS.** Phases 1 (#1187) and 2 are built; phases 3 and 4 are not. The decisions
entry is reserved as **D141**. This is the follow-up that D107 asked for. D107 recorded the card's
one-slot-per-object-type shape and decided to wait for a fourth slot that repeated a pattern
before generalizing it. #1131 and #1142 are the fourth and fifth requests, and they share a
pattern. #1181 is the pointer issue for this work.

Driving issues:
- #1131: Moon age on the Moon's card. The card half shipped in phase 2; the widget-picker
  preview and the device checks on the widget remain.
- #1142: a table of good satellite passes on the satellite card. **Shipped as a stopgap** in #1179
  (see §1.1); this design replaces the stopgap.
- #1143: the card half of it. The widget row can stay short because the card carries the
  detail. The widget half has shipped (#1148).

#1144 (the pass notification has no tap action) is fixed (#1146), and #1175 added a second,
"happening now" alert that also opens the card.

## 1. The problem

Every tonight-widget row, Moon widget, digest line and alert links to an object's info card
(`MainActivity.handleDeepLink`, D75). **The card usually knows less than the surface that linked
to it:**

| Surface says | The card says |
|---|---|
| Moon widget: phase, % illuminated, "Day 12" (#1124), rise/set | Rise/set and the next eclipse. No phase, no illumination, no age |
| Tonight widget: "21:34 · ISS", peak 62°, mag −3.2 | Up to five passes over five days, with dates (since #1179), picked by a bar the widget does not use (§1.1) |
| Tonight widget: "Jupiter well placed from 21:10" | Rise/set only |
| Tonight widget: "Perseids peak in 3 days, dark skies" | Catalog text only, nothing about the peak |

Tapping through should never show less information.

**The slot shape makes the problem worse.** `ObjectInfoCard`/`ObjectInfoBody` (now in
`shared/ui`) have one optional composable parameter per special case: `promoRow`,
`satellitePassRow` (D102) and `eclipseRow` (D106). Each one means:
- another parameter on both composables;
- one to four `StateFlow`s on `ObjectInfoViewModel` (`showingMoon`, `lunarEclipse`,
  `upcomingSatellitePasses`, `satellitePassTimesReliable`, …);
- wiring at **every** call site.

Call sites miss it. The gallery's card (`SkyMapNavHost`) still wires only `promoRow`, so the
Moon's eclipse row, and now the satellite pass table, disappear when the card is opened from the
gallery instead of the map. Adding #1131 the same way would mean more of these gaps.

### 1.1 What the #1179 stopgap changed

#1179 deliberately extended an existing slot instead of waiting for this design:
- `satellitePassRow` is now a table of up to **5** passes over **5 days**, with date, rise
  direction, peak altitude, magnitude and the shadow fade-out.
- The app-side caller is `upcomingVisiblePasses(context, norad, location, from)` in
  `TonightPasses.kt`, which replaced `nextVisiblePass`. `MainActivity` injects it into the view
  model as the `upcomingPasses` lambda. `passTimesReliable` stays a second lambda.
- The view model gained a second per-type flow beside `showingSatellite`/`showingMoon`:
  `upcomingSatellitePasses`, built with `mapLatest` so a stale search is cancelled.
- The selection rule is `List<SatellitePass>.goodPasses()` in `core/astronomy`: the first pass
  always, then only passes peaking at **30° or higher**.

**This breaks §2.** The tonight widget and digest rank passes by peak *magnitude* (`passQuality`
in `TonightSky.kt`, with −0.5 as the "ordinary" boundary). The card now filters by *altitude*.
A dim, high pass appears on the card but not in the widget, and a bright, low pass the other
way round, except for the first pass, which is always listed. Phase 3 removes `goodPasses()`
and puts both on the one bar.

## 2. The principle

> **Anything a widget, notification or digest says about an object, its card also says, from the
> same computation and through the same formatter.**

The card is where "tap for more" goes, which lets the other surfaces stay short. #1143 already
relies on this: the widget's pass row dropped its compass direction because the card shows it.

## 3. Two sections, two closed vocabularies

Everything object-specific on the card is either a fact about the object **now**, or a time to
**look** at it later. Each kind gets one generic section, drawn from a closed (sealed) type with
one exhaustive formatter. When a new kind of object needs card content, it adds entries to a
vocabulary. It does not add a card parameter.

### 3.1 Now: live facts as label/value rows

These are appended to the existing `DataRows` and computed at the map's current time.

```kotlin
sealed interface CardFact {
    /** Today's `RiseSetState`, moved here unchanged (D51). */
    data class RiseSet(val state: RiseSetState) : CardFact
    data class MoonPhase(val phase: LunarPhase, val illuminationPercent: Int) : CardFact
    data class MoonAge(val dayOfCycle: Int) : CardFact
}
```

Rise/set is already a live fact under another name, so it moves into this list. The Moon's
values come from `moonWidgetModel()`, the same function the widget draws from.

### 3.2 Upcoming: the events engine's own `SkyEvent`s

The Upcoming rows **are** `SkyEvent`s: the type the tonight widget and digest already render,
filtered by the same quality bar. This is what makes §2 true by construction rather than by
discipline. A pass that qualifies for the widget is in the card's list because it is the same
object, from the same predicate.

Each row shows a date and time, plus the event's trailing note from `highlightLine`. Some event
types also get detail lines that appear only on the card, such as a pass's rising direction and
its "fades into Earth's shadow at …". The event's name is dropped, because the card's title
already says it. Dates read "Tonight", "Tomorrow", or a short weekday and date. The stopgap
table already shows a date and time per pass, in the locale's date-time format; the relative
words are the remaining change.

The section keeps D102's rule that it never collapses silently. It is in exactly one state:

```kotlin
sealed interface Upcoming {
    data class Events(val events: List<SkyEvent>) : Upcoming
    /** Fresh data, nothing qualifies — "normal, not broken" (visible passes come in seasons). */
    data object NothingComing : Upcoming
    /** Satellite element sets too stale to time a pass honestly (D92 §"Staleness"). */
    data object DataTooOld : Upcoming
    data object NoLocation : Upcoming
}
```

An object with no Upcoming source at all (most stars and deep-sky objects) gets `null` and shows
no section.

### 3.3 What stays a slot

`promoRow` stays a slot. The add-a-widget offer is an action, not a fact about the object, so it
fits neither vocabulary.

## 4. Where the computation lives

**`:core:events` (pure).** Pull the per-kind finders out of `tonightSky()` and add a per-object
query beside it:

```kotlin
fun upcomingEvents(target: EventTarget, from: Instant, horizon: Duration, ...): List<SkyEvent>

sealed interface EventTarget {
    data class Body(val body: SolarSystemBody) : EventTarget
    data class Satellite(val passes: List<SatellitePass>) : EventTarget
    data class Shower(val shower: MeteorShower) : EventTarget
}
```

`tonightSky()` answers "tonight, every object, ranked". `upcomingEvents()` answers "this object,
over the next N days". Both call the same finders and the same **single quality bar**.

Pass alerts (`PassAlerts.armBestPass`) adopt that bar too. Today they arm the brightest visible
pass, even when it is a faint one. Since #1175 each armed pass gets two alarms, an advance warning
and a "happening now" alert on its own channel; both follow whichever pass is chosen, so the bar
needs applying once.

Satellite passes still arrive as a parameter, for the D92 reason: the element sets come from the
network. The app-side caller, `upcomingVisiblePasses(...)`, already exists (#1179). It returns
unfiltered visible passes and lets `upcomingEvents()` apply the bar, instead of calling
`goodPasses()` itself.

**`ObjectInfoViewModel` (shared/viewmodels).** One `CardLiveSource` resolves the shown card's id
to its facts and its `EventTarget`, and returns:

```kotlin
data class CardLive(val now: List<CardFact>, val upcoming: Upcoming?)
```

It is exposed as one `live: StateFlow<CardLive?>`. That replaces `riseSet`,
`upcomingSatellitePasses`, `satellitePassTimesReliable`, `showingSatellite`, `showingMoon` and
`lunarEclipse`. The platform pieces are injected as lambdas, as today: `upcomingPasses` and
`passTimesReliable`. The `mapLatest` cancellation from #1179 carries over, so one `live` flow
covers it.

The model is **typed and string-free**. `ObjectInfoViewModel` lives in `shared/viewmodels`, which
cannot see `shared/ui`'s Compose `Res`. This matches today's split, where the view model exposes
`SatellitePass` and the UI formats it.

**`ObjectInfoBody` (shared/ui).** It takes `live: CardLive?` in place of `riseSet` and the two
slots, and renders the two generic sections. The map and the gallery both get the content
automatically, because it comes from the view model and not from the call site. The welcome
screen's canned card has no view model and passes `null`, as it passes `riseSet = null` today.

**Time base.** The card uses the **map's** time, so it follows time travel, as rise/set, the
eclipse row and the pass row already do. Widgets keep using the wall clock.

## 5. Formatting and strings

Each vocabulary has exactly one formatter: an exhaustive `when` over `CardFact`, and one over
`SkyEvent`. Glance widgets and notifications read Android resources. The card reads Compose
resources converted from the same files (D136). So the two use the **same string keys**, and a
translation fixed once is fixed on both.

The strings involved are:
- **New:** a section heading, "Tonight" and "Tomorrow", and labels for the Now rows.
- **Reused:** the Moon's values (`moon_widget_age`, `moon_widget_illumination`, `moon_phase_*`)
  and the satellite empty-state lines.

## 6. Per-object content when this ships

| Object | Now | Upcoming |
|---|---|---|
| Moon | Rise/set, phase, % illuminated, age (#1131) | Next full moon, next new moon, next lunar eclipse (moved from the eclipse row) |
| ISS, Tiangong | — | Qualifying passes over ~5 days, at most ~6 rows (#1142) |
| Planets | Rise/set | Well placed tonight |
| Meteor showers | Rise/set of the radiant (any shower with a catalog position) | Upcoming peak, with moonlight |
| Stars, deep-sky objects, Sun | Rise/set | No section |

## 7. Phasing

0. ~~**Wait for `feature/ios-screens` to merge.**~~ **Done** (#1160). `ObjectInfoUi` is in
   `shared/ui` and `ObjectInfoViewModel` in `shared/viewmodels`.
1. **Change the structure, not the behaviour.** **Done** (#1187).
   - Add the model and the two sections.
   - Move rise/set, the eclipse and the #1179 pass table into them, unchanged.
   - Delete `satellitePassRow`, `eclipseRow` and their flows.

   The only visible change is that the gallery gains the eclipse line and the pass table.
   `ObjectInfoViewModelTest` is the guard. The pass table keeps its 30° rule for now, as a
   `SkyEvent`-shaped list or a temporary adapter, so this phase changes no behaviour.
2. **Moon Now facts** (#1131, the card half). **Done.** `CardFact.MoonPhase` and `MoonAge` come
   from `moonWidgetModel()`, the widget's own function, and render as three plain lines under
   the data rows (phase name, "N% illuminated", "Day N"). The ten strings they use
   (`moon_phase_*`, `moon_widget_age`, `moon_widget_illumination`) moved from `app` to
   `shared/ui` with `tools/move_strings.py`, so the widget and the card share keys and
   `tm languages` is unchanged.
3. **One quality bar for passes**, replacing the #1179 stopgap. Remove `goodPasses()`. The bar
   applies across the card, widget, digest and alerts in the same change, so they cannot disagree
   even briefly.
4. **Showers and planets** in Upcoming.

## 8. Ideas, not scope

- **Tap an Upcoming row to time-travel to it.** The map would jump to the pass or the eclipse,
  with the object centred. `time-travel.md`'s anchors and popular dates are the natural home.
- **Live planet Now facts.** Current magnitude, distance and phase, replacing the static catalog
  values that `DataRows` shows today.
- **Live satellite Now facts.** Current altitude, and whether it is sunlit or in shadow right now.
  Satellites now move continuously outside time travel (#1178) and search targets follow them
  (#1174), so the live position these rows need already exists.

## 9. Open questions

1. **The quality bar for passes.** Peak magnitude ≤ −0.5 is the events engine's existing
   "ordinary pass" boundary. The shipped card instead uses a 30° peak-altitude floor plus "always
   the first pass" (#1179). Magnitude already accounts for range and altitude. A combined rule is
   possible, but it must be the one rule every surface uses. Whether to keep "always show the
   first pass" is part of this question: it stops the card disagreeing with the widget, but it
   admits a faint pass.
2. **Satellite range and row cap.** #1179 shipped **5 days and 5 rows**, with a comment that TLE
   drift makes later times dishonest. Keep those values, or move to the 6 rows first proposed?
3. **Section heading.** "Upcoming", or "When to look"?
4. **Moon list length.** One of each phase, or every phase inside a fixed range?

## 10. Carried over from the #1179 review

Deferred when the stopgap shipped. They belong in this work and are not yet scheduled:
- **Stale list when switching directly between satellite cards.** The previous list shows until
  the new search returns. `Upcoming` needs a loading state, not an empty list (the sealed type
  in §3.2 has no such case yet).
- **`ensureActive()` in `PassSearch`**, so a cancelled search stops scanning.
- **Result caching** for repeated opens of the same card.
- **TalkBack semantics** for the pass table rows. They are three loose `Text` cells today.
- **Narrow-screen date column width.** The 3:1:1:1 weighting can truncate long date formats.
