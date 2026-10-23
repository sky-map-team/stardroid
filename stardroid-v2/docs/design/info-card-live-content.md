# Info Card Live Content: "Now" facts and "Upcoming" events

**Status: PROPOSED.** Nothing here is built, and the decisions entry is reserved as **D141**. This
is the follow-up that D107 asked for. D107 recorded the card's one-slot-per-object-type shape and
decided to wait for a fourth slot that repeated a pattern before generalizing it. #1131 and
#1142 are the fourth and fifth requests, and they share a pattern.

Driving issues:
- #1131: Moon age on the Moon's card.
- #1142: a table of good satellite passes on the satellite card.
- #1143: the card half of it. The widget row can stay short because the card carries the
  detail.

#1144 (the pass notification has no tap action) is a one-line fix that does not depend on this
design.

## 1. The problem

Every tonight-widget row, Moon widget, digest line and alert links to an object's info card
(`MainActivity.handleDeepLink`, D75). **The card usually knows less than the surface that linked
to it:**

| Surface says | The card says |
|---|---|
| Moon widget: phase, % illuminated, "Day 12" (#1124), rise/set | Rise/set and the next eclipse. No phase, no illumination, no age |
| Tonight widget: "21:34 · ISS", peak 62°, mag −3.2 | Only the first visible pass in 48 h, **with no date**, so a pass tomorrow reads as tonight |
| Tonight widget: "Jupiter well placed from 21:10" | Rise/set only |
| Tonight widget: "Perseids peak in 3 days, dark skies" | Catalog text only, nothing about the peak |

Tapping through should never show less information.

**The slot shape makes the problem worse.** `ObjectInfoCard`/`ObjectInfoBody` have one optional
composable parameter per special case: `promoRow`, `satellitePassRow` (D102) and `eclipseRow`
(D106). Each one means:
- another parameter on both composables;
- one to three `StateFlow`s on `ObjectInfoViewModel` (`showingMoon`, `lunarEclipse`, …);
- wiring at **every** call site.

Call sites miss it. The gallery's card (`SkyMapNavHost`) wires only `promoRow`, so the Moon's
eclipse row disappears when the card is opened from the gallery instead of the map. Adding
#1131 and #1142 the same way would mean five slots and more of these gaps.

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
    data class MoonPhase(val phase: LunarPhase, val illuminatedFraction: Double) : CardFact
    data class MoonAge(val ageDays: Double) : CardFact
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
already says it. Dates read "Tonight", "Tomorrow", or a short weekday and date. That fixes the
satellite row's missing date.

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
pass, even when it is a faint one.

Satellite passes still arrive as a parameter, for the D92 reason: the element sets come from the
network. The app-side caller becomes `upcomingVisiblePasses(norad, from, horizon)`, replacing
`nextVisiblePass`.

**`ObjectInfoViewModel` (shared/viewmodels).** One `CardLiveSource` resolves the shown card's id
to its facts and its `EventTarget`, and returns:

```kotlin
data class CardLive(val now: List<CardFact>, val upcoming: Upcoming?)
```

It is exposed as one `live: StateFlow<CardLive?>`. That replaces `riseSet`, `nextSatellitePass`,
`satellitePassTimesReliable`, `showingSatellite`, `showingMoon` and `lunarEclipse`. The platform
pieces are injected as lambdas, as today: satellite elements and the pass search.

The model is **typed and string-free**. In the shared modules (`feature/ios-screens`), the view
model cannot see `shared/ui`'s Compose `Res`. This matches today's split, where the view model
exposes `SatellitePass` and the UI formats it.

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
| Meteor showers | — | Upcoming peak, with moonlight |
| Stars, deep-sky objects, Sun | Rise/set | No section |

## 7. Phasing

0. **Wait for `feature/ios-screens` to merge.** It moves `ObjectInfoUi` and
   `ObjectInfoViewModel` into `shared/`. Rebasing a structural change across that move costs more
   than waiting for it.
1. **Change the structure, not the behaviour.**
   - Add the model and the two sections.
   - Move rise/set and the eclipse into them.
   - Delete `satellitePassRow`, `eclipseRow` and their flows.

   The only visible change is that the gallery gains the eclipse line. `ObjectInfoViewModelTest`
   is the guard.
2. **Moon Now facts** (#1131, the card half).
3. **Satellite pass list** (#1142). The quality bar applies across the widget, digest and alerts
   in the same change, so they cannot disagree even briefly.
4. **Showers and planets** in Upcoming.

## 8. Ideas, not scope

- **Tap an Upcoming row to time-travel to it.** The map would jump to the pass or the eclipse,
  with the object centred. `time-travel.md`'s anchors and popular dates are the natural home.
- **Live planet Now facts.** Current magnitude, distance and phase, replacing the static catalog
  values that `DataRows` shows today.
- **Live satellite Now facts.** Current altitude, and whether it is sunlit or in shadow right now.

## 9. Open questions

1. **The quality bar for passes.** Peak magnitude ≤ −0.5 is the events engine's existing
   "ordinary pass" boundary. A peak-altitude floor of 25–30° is the alternative; magnitude
   already accounts for range and altitude.
2. **Satellite range and row cap.** Proposed: 5 days and 6 rows. Element-set accuracy and ISS
   reboosts argue against going much further.
3. **Section heading.** "Upcoming", or "When to look"?
4. **Moon list length.** One of each phase, or every phase inside a fixed range?
