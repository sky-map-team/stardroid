# Detailed Design: Time Travel — anchors, scrubbing, and trails

**Status: PROPOSED** — a redesign of the shipped time-travel feature (D42, polished in
D50 and D53). Nothing here is implemented; the open questions are collected at the end
and the decisions entry is reserved as **D112**.

Four interactive mockups accompany this doc:

- **[`mockups/time-travel.html`](mockups/time-travel.html)** — the four concepts compared
  side by side: the scrubber, the anchor set, trails, and the shipped ladder for
  reference. Use this one to understand *what* is being proposed.
- **[`mockups/time-travel-chrome.html`](mockups/time-travel-chrome.html)** — the shipping
  shape: the bottom-anchored player, fling-to-play, the ladder as a long-press menu, the
  popular-dates sheet, and an explicit pixel budget. Use this one to understand *how much
  screen it costs*.
- **[`mockups/trails.html`](mockups/trails.html)** — trails and reference frames: the two
  frames on a live sky, the star-collapse case, the rotation machinery, and the info-card
  affordance. Use this one for Part 3.
- **[`mockups/trails-management.html`](mockups/trails-management.html)** — finding and
  clearing trails: four options compared on the same three live trails, with a verdict.

All three run a live simplified sky simulation (real sidereal rotation, rise/set geometry,
twilight from true solar altitude, lunar phase; circular coplanar planet orbits) so the
concepts demonstrate themselves rather than being described. The anchor and frame math in
the mockups is the real math — see "What the mockups verify" below.

## The problem

Three complaints, one root cause.

**Reaching a time is laborious.** The common question is "what will be up in the next few
hours?" Answering it today means: open the dialog → set a date → set a time → Go, or
travel and then work the speed ladder — accelerate, watch, decelerate, overshoot, reverse,
creep back. The ladder is a *transport* control (a tape deck) being used to answer a
*position* question.

**What stays still when time moves was never decided.** It falls out of an implementation
detail. `MapViewModel._camera` holds `lineOfSight`/`up` as **geocentric** vectors:

- In **manual mode** nothing rewrites those vectors as the clock ticks, so **RA/Dec is
  pinned** and the horizon slides across the screen.
- In **sensor mode** the clock-bus collector calls `refreshLocalFrame(time)` and
  `resolveSensorCamera(lastOrientation)` re-resolves the camera against the new local
  frame every tick, so **alt/az is pinned** and the stars wheel past.

Neither behavior was chosen, and manual mode's is the wrong default: "what will be over
that tree at 10pm?" is a ground-frame question, and manual mode answers it in the sky
frame.

**Motion is invisible.** You can watch the sky move, but you cannot see the *path*
anything takes — where a planet rises, where it transits, whether it clears the roofline.

## What we're building

1. **A time scrubber** — a position control that doubles as an information display, with
   playback folded into it as a fling gesture.
2. **An explicit anchor** — ground / sky / object, exposed in manual mode only, defaulting
   to ground.
3. **Trails** — the path an object takes, drawn in a chosen frame. **A standalone
   feature**: it works in real time, in sensor mode, with no time travel involved.
4. **A re-housed player** — collapsed to a chip at rest, expanding to two rows at the
   bottom of the screen; the ladder and the popular-dates picker both move to menus that
   cost no permanent pixels.

## Part 1 — The anchor

### The three anchors

| Anchor | Camera holds | What moves | Answers |
|---|---|---|---|
| **Ground** (alt/az) | Horizon + compass | Stars, Sun, planets wheel past | "What will be over that tree at 10pm?" |
| **Sky** (RA/Dec) | The stars | The horizon sweeps | "How do planets drift against the constellations?" |
| **Object** | A chosen body | Everything else, relative to it | Retrograde loops, elongations, the Moon's march |

The Sun and Moon are available as *camera* anchors. Note that the Sun is deliberately
**not** offered as a *trail* frame — see Part 3 for why the two diverge.

**Ground is the default in both modes.** It matches physical intuition and it is what
sensor mode already does.

### Why sensor mode gets no choice

In sensor mode the camera is a *function* of device orientation — the phone's orientation
**is** an alt-az measurement, so the camera is pinned to the ground by physics. There is
no degree of freedom to expose. Sky-anchoring there would rotate the map away from where
the phone points: aim at a tree, travel six hours, and the app claims that tree is
somewhere else in the sky, breaking the one promise auto mode makes.

So the anchor control is **manual-mode only**, and greys out in sensor mode with a short
explanation. Switching auto → manual mid-travel **inherits ground**, so the sky behaves
consistently across the mode switch and sky-anchoring is always opted into deliberately.

### Object anchors are universal, not a curated list

The mechanism needs one thing per tick: the target's direction at time *t*. Every catalog
object can answer that — a star or DSO returns a constant `RaDec`, a solar-system body
goes through `Ephemeris`. A curated list would be the same code with an arbitrary filter
bolted on, plus a permanent argument about its membership.

Therefore: **capability is universal, prominence is not.**

- **Sun and Moon are hardcoded** in the anchor menu — everyone wants them and neither
  should require a search first.
- **Everything else arrives through selection.** The object info card (D45) gains a
  **Track through time** action, so "track Betelgeuse" is search → tap → track. The menu's
  last slot shows whatever was last selected.

Most of the universe is a dull thing to track, and the failure is quiet rather than loud:
anchor to a fixed star and you have re-created sky-anchoring with extra steps. That earns
a hint, not a restriction. The interesting targets are the ones that move.

### What exactly stays fixed

Not the target centred — that yanks the camera every tick and prevents looking at
something *near* the object while it tracks. Instead, when the anchor engages, record the
rotation from the target direction to the current line of sight, and re-apply it each
tick. Engage while centred and it stays centred; engage off to one side and it stays off
to one side. This makes "anchor the Sun, watch where Venus will be" work.

Two consequences:

- **Roll must rotate with it.** The `up` vector rotates by the same delta rotation rather
  than re-levelling, or the field twists relative to the object being held still. Object
  anchors therefore **suppress the auto-level spring** (`levelJob`) while engaged — a small
  interaction with existing behavior, not a new mechanism.
- **The camera can go below the horizon.** Track the Sun forward twelve hours and you are
  staring at the ground. This should not be prevented — it is honest, the horizon layer
  makes it legible, and trails (Part 3) make it readable rather than confusing. It does
  need the same degenerate-pole handling `HorizonLeveler` already has: a large captured
  offset can drive the camera toward the zenith, where `up` is undefined.

### The implementation shape

One function, three cases:

```
reanchor(camera, fromTime, toTime) -> camera
```

- **Ground** — no-op. The camera is already stored per-frame in the local frame's terms
  for sensor mode; manual mode caches alt/az at the last user interaction and rebuilds
  `lineOfSight`/`up` through the new `localFrame`. This is the inverse of what
  `resolveSensorCamera` already does.
- **Sky** — no-op on the stored geocentric vector. Today's accidental manual-mode behavior
  becomes the deliberate non-default.
- **Object** — resolve the target at `toTime`, apply the stored offset rotation.

It hangs off the existing clock bus: `MapViewModel` already collects `timeFlow` to call
`refreshLocalFrame(time)`, and `reanchor` is a sibling call in that collector. No new
timing machinery, and it inherits the phase-locking that keeps the dome, horizon, and
layers agreeing under fast travel (D42).

## Part 2 — The scrubber, and how it absorbs the ladder

### The scrubber is the ladder

They are the same control read two ways. The thumb has a **position** and a **velocity**.
Dragging sets position. Releasing while still moving sets velocity — and a velocity on a
time axis *is* a playback rate. So playback needs no transport row: fling the thumb and
time plays, decaying to a stop; tap the track to stop dead.

This is a port, not an invention. `MapViewModel` already has fling handling — `onFling`,
a `flingJob`, and a `stopFling()` that touch-down cancels — with v1's constants (20
updates/second, velocity ÷ 1.1 per tick, stop below √10 px/s). The scrubber becomes a
second consumer of a physics model the map already applies to the sky itself, so the decay
feel is consistent between panning the sky and scrubbing time.

### The velocity mapping is the critical calibration

The naive linear map (thumb px/s → sim-seconds/s through the window scale) **does not
work**, and this is worth recording because it was found by measurement, not by eye. Over
a ±12h window a gentle 150 px/s flick comes out at ~10 hours/sec and crosses the entire
track in about a second. Compressing that with a sub-linear curve overshoots the other
way: a hard fling then takes minutes to cross, which reads as broken.

The correct model is that velocity maps to **window-crossings per second**, not absolute
time units:

```
rate = (pxPerSecond / trackWidth) * windowSpan * GAIN     // GAIN ≈ 0.35
```

What the user is manipulating is the span they can see, so the gesture feels identical at
every zoom — only the sky moves faster. With that gain a gentle ~150 px/s flick crosses
the visible window in ~14 s and a hard ~1200 px/s fling in ~1.7 s, whether the window is
±12 h or ±4 months. The decay cutoff is likewise scale-relative (6 px/s of thumb travel)
so playback does not feel stuck at wide zooms.

Two edge cases that must be handled or the gesture misfires:

- **Stale velocity** — drag, pause with the finger down, then lift. That is a stop, not a
  fling at the velocity from a second ago. Guard with a staleness check (~120 ms).
- **Running off the end** — playback clamps at the window edge rather than scrolling into
  unbounded time.

### The ladder survives as a menu

**Long-press the rate pill** for all thirteen v1 speeds, named, one tap each — the existing
`time_travel_week_speed` … `time_travel_second_speed_back` strings, unchanged. Short-tap
stops if playing, or returns to the present if frozen.

Nothing is lost from the shipped feature, and reaching a *named* rate becomes strictly
fewer taps than stepping to it. The distinction that matters:

- A rate picked from the menu is **pinned** — it does not decay, because it was asked for
  precisely.
- A flung rate **decays**, because it is a gesture, not a declaration.

This also preserves the ≥ 1 day/sec whole-day stepping, which is genuinely different
behavior — it shows the annual procession of the stars instead of a dizzying blur — and
deserves deliberate access rather than being reachable only by flinging hard enough.

### The track is an information display

This is the half of the scrubber that earns its pixels without being touched, and the part
to defend hardest in review. The track is shaded with:

- **Twilight bands** from true solar altitude: daylight, civil, nautical, astronomical
  twilight, full night. The rise/set solver from D50 already computes what this needs.
- **An object-up strip** showing when the selected object is above the horizon.
- **Event marks** at sunset/sunrise crossings, and the D50 preset events as tappable ticks.

So "when should I go outside to see Mars?" is answered by *looking at the control* — the
amber strip overlapping the dark band. No dragging required, and it turns a control into
the answer to the question the user actually has.

### Window zoom

Chips select the span: **±3h / tonight / ±1d / ±1mo / ±1y** (the mockup uses ±6h / ±12h /
±1w / ±4mo to exercise the range). "Tonight" is the one worth calling out — not an instant
like "next sunset" but a *window*, dusk → dawn, which is the single most common real
question and is currently three interactions away.

## Part 3 — Trails

**Trails are a standalone feature that time travel makes more powerful — not a time-travel
accessory.** This was mis-filed in the first draft of this doc. A trail needs an object, a
window, and a frame; all three exist without time travel. The clock is an input, and in
real time that input is "now". Time travel changes *where* the window sits and *how fast*
it moves; it does not change the computation.

A third mockup covers this part: **[`mockups/trails.html`](mockups/trails.html)** — the
frames compared on a live sky, the star-collapse case, the rotation machinery, the info-card
affordance, and the frame-vs-camera-anchor question.

### What it is for in real time

- **"Where will this be later tonight?"** — the trail from now to dawn, over the sky you
  are actually pointing at. The real question is rarely "where is Jupiter" but "will
  Jupiter clear next door's roof by eleven".
- **"Where do I look for the sunset?"** — the Sun's forward trail meets the horizon at the
  bearing it will set at. Same for moonrise. Hard to know otherwise, and it needs no
  interaction at all.
- **Rise and set as a place, not a time.** The info cards already show "Rises: 04:32"
  (D51). A trail turns that into a *direction on screen* — where the line crosses the
  horizon. The number says when; the trail says which way to face.
- **In AR mode it is a genuine sight line.** With the camera underlay on (D64), the Sun's
  trail is drawn over the actual tree line. That is the feature at its best, and it has
  nothing to do with time travel.

### A trail is a path in a frame

The key move is to stop treating a trail as "the object's path" and treat it as a path *in
a frame*. Same ephemeris samples, different transform before projection:

```
trailPoint(t) = R(t) · d(t)
```

`d(t)` is the object's direction — the same call in every frame. `R(t)` is the rotation
carrying the frame's reference at time *t* back to where it sat at *t₀*:

| Frame | R(t) | Over its span |
|---|---|---|
| **Against the stars** | Identity — no transform | Stars collapse to points; planets trace retrograde |
| **Against the horizon** | Rotation about the celestial pole by the sidereal angle | Everything traces a circular arc |

So the horizon frame is the general (object-referenced) form with the celestial pole as its
reference, and the star frame is the trivial case. Building the general form means a
third frame is a different *argument*, not different code.

**The two frames answer different questions and both are needed.** Over six hours the
Earth's rotation dominates every other motion, so the horizon frame *cannot* show
retrograde; and the star frame *cannot* show rise and set. Verified in the mockup: a star's
horizon-frame trail is exactly a constant-declination circle (declination spread 1.1 ×
10⁻¹⁶, machine epsilon), and Mars over the same six hours deviates by 6 × 10⁻⁴ — invisible.

**Span travels with the frame**, because the frame *is* the question: hours for a diurnal
arc, months for retrograde. Six hours of retrograde is invisible; 300 days of diurnal arc
is a scribble. Span is also object-dependent for fast movers — the Moon laps the sky every
27 days, so a long star-frame span closes into a loop around the whole sphere that no
camera can frame.

### A star's trail collapses to a point — that is the mechanism

In the star frame a fixed star's trail spans < 0.0001° over 300 days, while Mars spans 133°.
This is not a degenerate case to guard against; every star collapsing is exactly what makes
the background static enough for a planet's loop to read against it.

The UI consequence is that offering "against the stars" for a star is offering to draw
nothing, so **that row is disabled for stars and DSOs**, with the reason stated. Availability
is derived from the object, not configured.

### The Sun frame was considered and cut

An earlier draft offered a Sun-referenced trail. It is dropped, for three reasons:

- The Sun is below the horizon whenever the app is in use, so the thing being held fixed is
  underfoot and the camera would have to point at the ground.
- The resulting trail is dominated by Earth's orbital motion rather than by anything about
  the object, which is a hard picture to read.
- Its real question — elongation, "how far from the Sun is Venus?" — is a **number, not a
  shape**, and belongs as an info-card row beside the D51 rise/set rows.

**The Sun survives as a camera anchor** (Part 1), where "keep the Sun centred while I
scrub" is coherent. Only the *trail* frame is cut. Two trail frames remain, which map
cleanly onto the two questions people ask and give each a sensible camera.

### The roll subtlety (for the general object-referenced form)

A rotation carrying one direction onto another leaves the **roll about that axis free**, so
the construction must pin it with a secondary reference (the ecliptic pole).

Worth being precise, because the obvious justification is wrong: for a **Sun** reference it
makes no difference at all — the Sun moves along the ecliptic, so both directions lie in
that plane, their cross product is parallel to the ecliptic pole, and the naive minimal
rotation is already the pinned one (measured divergence over 240 days: 0.000°). It bites
for an **off-ecliptic reference**: with the Moon (5.1° inclined) the unpinned rotation lets
the ecliptic pole wander up to **116°** over a year. This matters for the camera anchor,
which does allow the Moon and planets.

### The other two "paths"

- **Analemma** — a trail with a one-solar-day sample step. Free.
- **Diurnal circle** — the full 24 h circle for a fixed star; shows what is circumpolar.
  Probably a grid-layer option rather than its own feature.

### Presentation, scope, cost

**Presentation.** Fade the line with distance in time from the current instant so "now"
stays legible — and give the present a distinct dot, which matters more in real time where
there is no motion to indicate it. Hour ticks, labels at the ends, and a marker where the
trail crosses the horizon.

**Scope.** The **selected object plus the Sun and Moon**. All-planets-always is visual
noise; selection-only misses the two bodies most people ask about. Default **off**,
discovered through the info card rather than switched on globally — clutter is a real risk
in real time, where a trail is permanent rather than obviously transient.

**Cost.** A trail's *shape* changes only when the window, frame, or selection changes, so
compute the polyline on those events and cache it; per-frame cost is re-projection, which
the renderer already does for every star. One correction to the first draft: in **real
time the window slides continuously**, so "recompute when the window changes" would mean
every tick. A real-time trail's shape is nearly static, so recompute on a coarse cadence
(the existing minute-scale real-time tick) and let the endpoints extend.

**Where the math lives.** Pure vector math on 3-vectors and rotations — no Android, no
renderer. It belongs in `:core:astronomy` beside the rise/set solver, unit-testable
without a device.

### The affordance — and why there is no Layers toggle

**There is no Layers-sheet row for trails.** The first draft proposed one; it was wrong.
Every other Layers row governs a category the app draws from its own data (stars, DSOs,
grid, horizon) — you never chose those individually, so a master switch is the only control
that makes sense. A trail is the opposite: it exists *because* you picked an object. A
global toggle would be an off-switch for a set of things you individually turned on, and it
cannot express what a trail needs — an **object and a frame**, which a boolean cannot
carry. To remove a trail, clear it on the object you set it from.

The frame choice therefore lives on the **object info card** (D45), as rows named by the
question rather than the frame — "sky-anchored" means nothing to most people, "against the
stars" does:

- **Against the horizon** — where does it rise and set?
- **Against the stars** — drift and retrograde (disabled for stars and DSOs)

### Finding and clearing trails

Removing the Layers *toggle* left a real gap: with no global list, how do you find a trail
set an hour ago? Four options were mocked
([`trails-management.html`](mockups/trails-management.html)); two ship.

| Option | Discovery | Chrome cost | Scales | Verdict |
|---|---|---|---|---|
| Chips on the map | Best | A permanent row | Poor | Cut |
| **Trails list in the Layers sheet** | Adequate | None | Good | **Ship** |
| Rail item with a count badge | Best | One rail slot | Good | Defer |
| **Tap the trail itself** | None | None | n/a | **Ship** (shortcut) |

**A Trails section in the Layers sheet is the home.** This is not the control D113 rejected:
the objection was to a global *boolean*, which cannot carry object-plus-frame. A **list** of
individually-created trails — one row each, showing object and frame, each with its own ✕,
plus Clear all — is the "active filters" pattern, and it reintroduces nothing. It costs no
permanent pixels, scales past three trails, and puts trails where the canonical list of
drawn things already lives. Its empty state does discovery work for free: *"No trails. Tap
an object and choose Trace its path."*

**Tapping the trail itself is the shortcut.** `IdentifyGeometry`'s inverse projection
already hit-tests the sky for tap-to-identify (D45), so a trail hit-test is one more branch
in an existing handler, and direct manipulation is the right verb for something drawn on the
sky. It cannot stand alone — a trail below the horizon is untappable, and nothing signals
that a line is tappable — but as a second route it is nearly free. It needs a tie-break rule
against tap-to-identify when a trail passes near an object.

**Chips are cut on collision**, not on principle. They are the best discovery surface, but
the bottom strip now belongs to the time player (Part 4), and stacking a chip row under it
recreates the jumble D56/D57 removed.

**The rail badge is deferred, not rejected.** It is the best answer to the real problem —
which is *"I forgot a trail was on"*, not *"clearing is hard"* — but it costs a slot the D57
rail budget cannot spare, and an `ifroom` item that vanishes in landscape is a poor home for
the only discovery signal. If usage shows people lose track of trails, promoting the Layers
row to a badged rail item is a clean follow-up. Interim: the trail-created snackbar reads
"Trail added — manage in Layers", teaching the route once, when it becomes relevant.

**Trail frame vs. camera anchor.** They are the same concept applied to different things:
one holds the camera still, the other draws a path. **Keep them independent, defaulting the
trail frame to the current camera anchor.** Coupling would force a star-frame trail to put
the camera into sky-anchoring — impossible in sensor mode, which is exactly where the best
trail case lives (the AR sight line). A trail drawn in a frame the camera is not holding
translates across the screen as a rigid shape, which is geometrically correct and needs a
visual cue — the mockup dashes and dims the horizon to say "this is no longer held".

## Part 4 — The chrome, and the pixel budget

The concepts mockup stacked four rows permanently over the sky (~158 dp). That was a
comparison surface, not a design. The shipping shape:

| State | Cost | Where |
|---|---|---|
| **Real time** | 0 dp — does not exist | — |
| **Travelling, collapsed** | ~30 dp (one chip) | Top centre, where the player is today |
| **Travelling, expanded** | ~94 dp (two rows) | **Bottom**, thumb-reachable |

Expanded, the player is: **clock readout · anchor pill · rate pill**, with the scrubber
beneath.

### Why the expanded player moved to the bottom

Three reasons, in order of weight:

1. **It is a drag target**, so it belongs under the thumb.
2. **It stops covering the sky being watched** — the top of the screen is where you are
   looking when the phone tilts up.
3. **It dodges the HUD.** The map HUD is top-right (D65/D66) and the player is top-centre;
   an expanded ~330 dp scrubber fights it for the same band on a narrow phone. Both
   already share that strip after R2.2 added `displayCutoutPadding` to the player. This is
   the R2.2 collision in a new costume, and moving down avoids it rather than negotiating
   it.

The collapsed chip stays top-centre because it is a *status* indicator, not a control —
the role the player plays today, in the place users already look.

### Where the four rows went

- **Step buttons → deleted.** Exact reversibility was the argument for them (tap +1h four
  times, −1h four times, land exactly where you started, which a rate ladder can never do),
  but "Now" covers undo and the scrubber can snap to hours. If they return they belong as
  ± affordances at the track's ends, not as a row. A **1 sidereal day** step is the
  astronomy-specific one worth revisiting — the stars land in exactly the same place and
  only the planets and Moon move, isolating solar-system motion.
- **Transport row → the fling gesture**, with the named ladder on long-press.
- **Anchor chips → one pill** showing the current anchor, opening a menu. Most users will
  never leave Ground, so the common case shows one short word.
- **Hint row → onboarding.** Show it the first few times an anchor changes through the
  existing `SnackbarHost` (D53), then never again.

### Visibility

The player rides the existing chrome `AnimatedVisibility` container, so it inherits
tap-to-show, auto-hide on the `INITIAL_CHROME_FLASH_MS` linger, and hide-during-search with
no new visibility state — the same argument `map-hud.md` makes for the HUD.

## Part 5 — Popular dates

The shipped picker is richer than a scrubber can replace and must not be lost: **19
events** — 5 computed (`NOW`, next sunset, next sunrise, next full moon, next new moon),
10 dated 2026 events, and 4 historical (Apollo 11, the 2024 North American eclipse, the
2016 Mercury transit, the 2020 Jupiter–Saturn conjunction).

Crucially **each carries a `searchTarget`** (D50): the Perseids entry aims the map at the
radiant, Apollo 11 at the Moon. A preset is not a timestamp, it is a **destination** — a
time plus something to look at. That is why the scrubber does not subsume it.

What changes:

- **The dialog becomes a sheet**, sibling to the Layers and ⋮ sheets (D57), opened by
  **tapping the date readout**. The readout is the affordance, so no button is spent on it
  and presets cost no permanent pixels.
- **Grouped by how you reach for them** — *Tonight* (computed, relative to where the clock
  already is), *Coming up* (dated, future), *From the past* (historical). The flat dropdown
  put a 1969 Moon landing adjacent to tonight's sunset.
- **Relative times on the computed rows** ("in 2.3 h"), because that is the question.
- **Past events grey out** — `TimeTravelEvent.isPastAt()` already computes this and a
  dropdown had nowhere to show it.
- **Picking an event outside the window zooms the scrubber to contain it**, so you arrive
  with context: the twilight bands and the hours either side of the eclipse, rather than an
  unmoored instant.

Relative presets keep D50's behavior of resolving from the *app clock's* now, so while
travelling "next full moon" looks forward from the visited time.

## What the mockups verify

The mockups run real geometry rather than canned animation, and the numbers were checked
rather than eyeballed:

- **Sky model** — Polaris parks at alt 51.2°, az ≈ 0° at all times; a star returns to the
  same alt/az after one sidereal day to five decimal places; solar noon lands at 12:03 with
  sunrise 04:33 and sunset 19:33 for 51.5°N in early August; the Moon drifts +14.4°/day
  eastward; Mars retrogrades on 73 of 730 days.
- **Anchor math** — the sky anchor round-trips camera → RA/Dec → camera to four decimal
  places and then correctly sweeps the horizon as time advances; the object anchor
  preserves its captured offset to four decimals across six hours while the Moon climbs
  from −15° to +20°.
- **Preset solvers** — the next-sunset/sunrise solver returns 19:32 / 04:32; the lunar
  phase solver lands on phase 0.501 (full) and 0.000 (new).
- **Frame math** (`trails.html`) — a star's horizon-frame trail is exactly a
  constant-declination circle (declination spread 1.1 × 10⁻¹⁶, machine epsilon) while Mars
  over six hours deviates by 6 × 10⁻⁴; a star's star-frame trail spans < 0.0001° over 300
  days against Mars's 133°; the ecliptic-pinned and minimal rotations agree to 0.000° for a
  Sun reference but diverge by up to 116° for the Moon. All twelve object/frame
  combinations were checked to render on screen.
- **Fling curve** — measured across three window scales to arrive at the crossings-per-
  second model above.

Deliberately faked, and noted in the mockups: planet positions use circular coplanar orbits
(retrograde is qualitatively right, not ephemeris-accurate) and the star field is a
deterministic scattering around 15 real bright stars. Nothing the concepts depend on rests
on the faked parts.

Neither mockup has been verified in a browser — the checks above are static analysis plus
independent ports of the math, not a live render.

## Slicing

1. **Trails, horizon frame** — the frame rotation in `:core:astronomy`, polyline sampling
   + cache, the info-card rows. **Needs none of the rest of this design**: it works in real
   time, in sensor mode, and answers "where will this be tonight?" and "where does the Sun
   set?" on its own. Promoted from last in the first draft, where it was wrongly gated
   behind the scrubber.
2. **Anchors** — `reanchor` in `MapViewModel`, ground default in manual mode, the anchor
   pill + menu, sensor-mode greying, auto-level suppression for object anchors. Ships the
   correctness fix (manual mode currently defaults to the wrong frame) independently of any
   new UI surface. Adds the star frame to trails as a by-product.
3. **The player re-housed** — bottom anchoring, collapse/expand, the chip. Pure chrome
   work; no new time behavior. Resolves the HUD collision.
4. **The scrubber** — track rendering (twilight bands, object-up strip, event marks),
   drag-to-position, window zoom, fling-to-play, the ladder menu. The largest slice; the
   velocity calibration above is the risk to retire first.
5. **Presets re-housed** — the sheet, grouping, relative times, greyed past events,
   window-zoom-on-arrival.

Slices 1–3 are each independently shippable and each fixes or adds something on its own.
Slice 4 depends on 3 (the scrubber wants the bottom real estate). Slice 1 gains its second
frame from slice 2 but does not need it.

## Open questions

1. **Is fling-to-play too clever?** It is elegant and deletes a row, but it is a discovered
   gesture, and playback is the thing v1 users already know how to do. The mitigation is
   that they need never discover it — the rate pill's long-press menu offers the named
   ladder and a short tap stops, so fling is the *fast* path, not the *only* path. The
   conservative alternative is a single play/pause button beside the rate pill.
2. **Landscape.** The action cluster moves to a bottom-anchored right-edge column in
   landscape (D57), which a full-width bottom player would collide with. Likely answer: the
   player insets its right edge by the cluster's width, as the rail budget handles the left.
   Needs checking on a device before committing.
3. **Does the anchor persist across sessions?** Leaning no — ground every launch, with the
   anchor as a per-session choice, so nobody is stranded in a frame they set last week and
   forgot.
4. **Should returning to now be non-destructive?** Leaving travel currently forgets where
   you were. A "back to 10pm" affordance after returning costs little and matches how people
   compare — but it is a new surface, so it is listed rather than assumed.
5. **The transition sweep.** The 2.5 s smoothstep (`TransitioningClock`) is right for a
   six-hour jump and disorienting for a four-century one. Proposal: cap angular sky velocity
   instead of duration — short jumps sweep, long jumps cross-fade. Worth fixing regardless
   of the rest of this design.
6. **Trail sample density** — fixed sample count per trail, or adaptive to the window span?
   Fixed is simpler and probably sufficient given the cache; adaptive matters only if
   month-scale trails visibly polygonize.
7. *(Resolved — a Trails **list** in the Layers sheet, plus tap-the-trail. See
   "Finding and clearing trails" in Part 3.)*
8. *(Resolved — a trail's frame stays as set. See below.)*

### Resolved this round

- **Trails are standalone**, not a time-travel sub-feature — they work in real time and in
  sensor mode, and move to slice 1.
- **No Layers-sheet toggle.** A boolean cannot carry object-plus-frame, and a global
  off-switch for individually-created things is a control without a job.
- **The Sun is cut as a trail frame** (it is below the horizon in use, its trail is
  dominated by Earth's orbital motion, and its real question is a number) but **kept as a
  camera anchor**, where "keep the Sun centred while I scrub" is coherent.
- **Trail frame and camera anchor stay independent**, with the trail frame defaulting to
  the current anchor — coupling would forbid the AR sight-line case in sensor mode.
- **A trail's frame stays as set.** The camera anchor supplies the *default* at creation
  and is never consulted again: changing the anchor later leaves existing trails alone. The
  frame was a deliberate per-trail choice answering a specific question ("where does this
  rise?"), and silently re-framing it would change the answer without being asked. It also
  lets two trails hold different frames at once, which a follow-the-anchor rule could not
  express.
