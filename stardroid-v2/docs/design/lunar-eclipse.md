# Lunar Eclipses

**Status: IMPLEMENTED** (D106). Astronomy, tonight/digest events, the Moon info-card row, GLES1
map rendering, an opt-in reminder notification, and a static time-travel entry for the next
eclipse are all built and merged.

Closes a gap several earlier docs pointed at without filling: `solar-system-imagery.md` §8
("Adjacent opportunities") named lunar eclipse colouring as "one more `PhaseCompositor`
parameter... exactly what people open the app for" once true-scale discs and D54's topocentric
Moon existed; `ephemeris-accuracy.md` built `MeeusEphemeris` specifically for eclipse-timing
accuracy (D84) and left the Sun and Moon in a shared, precession-correct frame; and
`render-gles3.md` §7.4 sketched a *future*, GLES3-shader version of continuous eclipse shading.
This document is about the version that ships now, on the GLES1 backend already in production,
not that future one — see §4 for how the two relate.

**Scope: lunar eclipses only.** Solar eclipses/transits share some shadow-cone math but are
explicitly out of scope here; `render-gles3.md` §7.4 still names them as the natural next step
once a GLES3 backend exists.

## 1. Shadow-cone astronomy — `core/astronomy/LunarEclipse.kt`

Standard tangent-cone construction (Meeus, *Astronomical Algorithms*, ch. 54): `shadowCone(time)`
returns the umbra/penumbra angular radii at the Moon's distance and the antisolar direction, all
from the existing `Ephemeris` interface — no new ephemeris data needed, since D84 already leaves
Sun and Moon in a shared J2000 frame. `moonShadowSeparationDeg` is the Moon-to-shadow-axis angular
separation `LunarEclipseCircumstances` (type, magnitude, and the P1/U1/U2/U3/U4/P4 contact times)
is built from.

**This geometry is geocentric and stays that way — deliberately, not by oversight.** A PR review
asked whether it holds for every observer regardless of location; it does. A lunar eclipse is
decided by whether the Moon (a real object in space) sits inside Earth's shadow cone (also a real
object in space, anchored to the Earth–Sun line): both are facts about the Solar System fixed the
moment you fix the time, independent of where on a 6,378 km-radius planet someone stands. Moving
the observer changes only which direction they must look to see the Moon (diurnal parallax, up to
~1°) — it does not move the Moon relative to the shadow. This is the opposite of a solar eclipse,
where the Moon's shadow *on Earth* is only ~270 km wide, so the observer's location genuinely
decides totality vs. partial vs. nothing. Consequence pinned in code: `moonShadowSeparationDeg`
must compare against `Ephemeris.geocentricPosition`, never `topocentricPosition` — mixing frames
would reintroduce the parallax this geometry is specifically built to ignore.

`nextLunarEclipse(after)` searches from full moon to full moon (eclipses only occur near syzygy),
seeded 16 days before `after` rather than from `after` itself, so an eclipse already in progress
when the app is opened is still found — searching strictly forward from `after` would skip past a
current eclipse to next month's, since its full moon is already in the past relative to `after`.

Verification: golden-tested against the 2025-03-14 total lunar eclipse. That reference is a
recollection, not a value transcribed from a NASA/USNO table (no web access in the session that
wrote it), so the test tolerance is date-level, not minute-level. **Follow-up**: tighten against a
published table, the way `LunarPhaseTest` already does for phase events.

## 2. Tonight/digest events — `core/events/TonightSky.kt`

`SkyEvent.LunarEclipseUpcoming` joins the sealed `SkyEvent` hierarchy. Unlike `SatellitePassTonight`
(D101), it is **not** a caller-supplied parameter — it needs no network data, so `tonightSky` finds
it itself via `nextLunarEclipse`, the same way `moonPhaseTonight` already does for phase events.
Always `MeeusEphemeris`, regardless of what `tonightSky` was called with, per D84.

Gated on the Moon actually clearing the horizon during the eclipse's window
(`moonAboveHorizonBetween`, now a public function shared with §5's alert scheduler — see below):
an eclipse invisible from the observer's saved location isn't a highlight, however rare it is
elsewhere. Quality is banded by type — total (0.95) and partial (0.75) clear the digest's
announce threshold; penumbral (0.35) stays a highlight but never interrupts, since a penumbral
eclipse is barely perceptible to the eye.

## 3. Object-info card row — `ui/objectinfo/`

`EclipseRow`, wired into `ObjectInfoCard`/`ObjectInfoBody` as an optional composable slot exactly
like D102's `satellitePassRow` and the existing moon-widget `promoRow` — shown only on the Moon's
card, via `ObjectInfoViewModel.showingMoon`/`lunarEclipse`. Renders the next eclipse's type,
greatest-eclipse date/time, and the totality window when there is one, or an explicit "none
expected soon" line rather than hiding the section (D102's reasoning: silence reads as "this card
doesn't know", not "there genuinely isn't one").

**A PR review raised whether the info card should generalize this slot-per-parameter shape**
(`promoRow`, `satellitePassRow`, `eclipseRow`) into something more abstract rather than adding a
new boolean parameter each time. Deliberately not changed here — see D107.

## 4. Map rendering — GLES1 copper tint, not the deferred GLES3 shader

`render-gles3.md` §7.4 sketched continuous eclipse shading as a GLES3 fragment-shader feature,
on the grounds that the existing CPU `PhaseCompositor` "cannot be done this way at all: it evolves
over hours, continuously, and would demand a full per-pixel recomposite and texture re-upload
every frame." That framing is about *per-frame* continuity; it doesn't hold for the coarser
cadence this feature actually needs. `PhaseCompositor` already recomposites the Moon's texture
only on layer resubmission (~once a minute) against a *quantised* phase — an eclipse evolving over
hours is comfortably slow against that cadence, so the same quantise-and-cache mechanism the
ordinary phase terminator already uses extends cleanly to the eclipse shadow, with no shader and
no GLES3 dependency. Confirmed by building it: see the files below.

- **`EclipseShadow`** (`render/api/Primitives.kt`) joins `Terminator`, additive per the seam
  `render-gles3.md` §7.4 already named ("`Terminator` becomes a small sealed hierarchy of shadow
  sources"). Every field is a *ratio to the Moon's own angular radius* — umbra/penumbra radii, the
  shadow axis's offset from disc centre, and its position angle (same east-of-north convention as
  `brightLimbAngleDeg`) — so the compositor, which only ever sees a disc normalized to `[-1, 1]`,
  needs no astronomy of its own.
- **`EclipseGeometry.tint(x, y, shadow)`** (`render/api/EclipseGeometry.kt`) is the pure per-pixel
  math, mirroring `PhaseGeometry` for the same reason: a future GLES3 shader evaluates exactly this
  function, and it is unit-testable without Android/Bitmap. The umbra darkens *and* reddens
  (Rayleigh-scattered sunlight, the same reason sunsets are red — deepest and reddest at the
  umbra's centre, Danjon L≈2 by default); the penumbra only dims, uniformly across channels, since
  that light is unfiltered sunlight simply reduced by partial blocking.
- **`PhaseCompositor`** (`render/gles1/`) applies the tint as a channel multiplier on top of the
  ordinary phase-shading scale.
- **`TextureCache.PhaseKey`** gained an `EclipseKey` (1% of a Moon radius per shadow field, one
  degree of direction) so the cache actually recomposites as an eclipse progresses. This closes a
  real gap the naive approach would have missed: at full moon `illuminatedFraction` is already
  ≥`PhaseCompositor.FULLY_LIT`, which normally means "skip compositing, share the fully-lit
  texture" — exactly the condition every eclipse starts from. `PhaseKey.of` now composites whenever
  an `EclipseShadow` is present, regardless of the fraction check.
- **`SolarSystemLayer.eclipseShadowFor`** computes the shadow from `MeeusEphemeris`, independent of
  whatever ephemeris the layer itself was constructed with (D84) — null on the vast majority of
  nights, when the Moon's disc doesn't overlap the penumbra at all.

**The GLES3 shader version remains the eventual upgrade**, for the reason `render-gles3.md`
originally gave: this CPU version is still cadence-quantised (imperceptibly, at the same ~1/min
rate the ordinary phase already accepts) rather than exactly continuous. Nothing here forecloses
it — `EclipseShadow`/`EclipseGeometry` are the same additive, backend-agnostic shape `Terminator`/
`PhaseGeometry` already are, so a GLES3 backend evaluates the same function per pixel and drops
`PhaseCompositor` and its cache entirely, per `render-gles3.md`'s existing migration story.

## 5. Opt-in reminder — `eclipses/EclipseAlerts.kt`

An "Eclipse alerts" toggle on the Solar System layer (`LayerParameter.ECLIPSE_ALERTS_PARAMETER`),
same shape as D103's satellite `pass_alerts` — off by default, declared on the layer rather than a
Settings row, requests `POST_NOTIFICATIONS` on opt-in.

**Deliberately `WorkManager`, not D103's `AlarmManager`.** D103's whole argument for
`AlarmManager.setAndAllowWhileIdle` was that `WorkManager`'s `setInitialDelay` makes no timeliness
guarantee, and a satellite pass is six minutes long — a Doze-deferred alarm arriving after the
pass is strictly worse than no alert at all. None of that applies here: eclipse contact times are
known days to months ahead, and once under way an eclipse lasts hours, so a WorkManager job
slipping by a few minutes is a non-event. What *does* apply is D77's own reason for a **daily
planner** rather than one job armed on discovery the moment an eclipse is found: a job scheduled
months ahead is unreliable across reboots and app updates, while a cheap daily check costs nothing
while nothing is imminent. So this follows D77's planner/poster shape, not D103's alarm:

- A daily 09:00 planner (`EclipseAlertPlannerWorker`) checks whether `nextLunarEclipse`'s alert
  point — the umbral contact for a partial/total eclipse, else the first penumbral contact — falls
  within a 30-hour horizon, and if so arms a one-time poster ~30 minutes ahead of it.
- The poster (`EclipseAlertPosterWorker`) recomputes the eclipse fresh at fire time rather than
  trusting the planner's snapshot, the same "recompute at post time" shape D77's own
  `NotificationPosterWorker` uses, and re-checks the opt-in — someone who turned alerts off after
  one was armed is not interrupted by it.
- Both stages skip an eclipse the Moon never clears the saved location's horizon for, reusing §2's
  `moonAboveHorizonBetween` (promoted from `private` to public specifically so both call sites
  share one answer to "is this actually visible from here" rather than re-deriving it).
- Its own channel, `CHANNEL_ECLIPSES`, at `IMPORTANCE_DEFAULT` — distinct from D77's digest slot
  and D103's `CHANNEL_PASSES`; eclipses are rare enough (a few a year, fewer visible locally) that
  they don't need to compete for or share either.

## 6. Time-travel picker — a static entry, pending a dynamic refactor

`TimeTravelEvents.kt`'s "popular events" list is a hand-maintained calendar of fixed timestamps,
including a pre-existing "Total Lunar Eclipse, Mar 3, 2026" entry (verified against §1's solver:
its epoch matches the computed greatest-eclipse instant to within 5 minutes, so the hand-entered
value was correct, just no longer upcoming). Added the real next eclipse — a deep partial on
2026-08-28 (umbral magnitude ≈0.93), computed via `nextLunarEclipse` rather than hand-entered like
the rest of the list — as a new static `TimeTravelEvent`, in chronological position.

**Explicitly not a dynamic computation**, per direct instruction: `TimeTravelEvents` is due a
refactor to compute events like this (and, presumably, other recurring astronomical events
currently hand-pinned to 2026 dates) rather than pin one year's calendar by hand. This entry is a
placeholder in that sense — it will go stale again once 2026-08-28 passes, exactly as the March
entry did — and is recorded here so that refactor has a concrete, already-solved case to build
from: `nextLunarEclipse(now)` is already the right function to call.

## D107: object-info row abstraction — recorded, not built

A PR review on §3 asked whether the info card should generalize its slot-per-parameter shape
(`promoRow`, `satellitePassRow`, `eclipseRow`) rather than adding a new optional composable
parameter to `ObjectInfoCard`/`ObjectInfoBody` each time a new object type needs card-specific
content. Explicit instruction was not to change the code now, only to record the thinking.

The shape has stayed cheap so far because there have only been three slots, each genuinely
different in what it needs from its view model (a dismissible one-time offer; a satellite's next
pass, with three distinct empty states; an eclipse's circumstances), and each opts in per-card via
a simple boolean condition the view model already computes (`showingSatellite`, `showingMoon`).
Generalizing from three data points risks guessing the wrong abstraction — the real test is
whether a fourth slot shows a *repeating* shape (e.g., several object types all wanting "one
optional fact block sourced from a per-type view-model flow") rather than another one-off. Revisit
when a fourth candidate actually appears; until then, three parameters is not yet a pattern worth
abstracting.

## Files touched

| Area | Files |
|---|---|
| Astronomy | `core/astronomy/LunarEclipse.kt` (new); reuses `Ephemeris.kt`, `MeeusEphemeris.kt`, `LunarPhase.kt` |
| Events | `core/events/TonightSky.kt` (`SkyEvent.LunarEclipseUpcoming`, public `moonAboveHorizonBetween`) |
| Object info | `ui/objectinfo/ObjectInfoUi.kt` (`EclipseRow`), `ObjectInfoViewModel.kt` (`showingMoon`, `lunarEclipse`) |
| Tonight widget | `widget/TonightFormats.kt` |
| Render API | `render/api/Primitives.kt` (`EclipseShadow`), `render/api/EclipseGeometry.kt` (new) |
| GLES1 backend | `render/gles1/PhaseCompositor.kt`, `render/gles1/TextureCache.kt` (`EclipseKey`) |
| Layer | `layers/SolarSystemLayer.kt` (`eclipseShadowFor`), `layers/LayerParameter.kt` (`ECLIPSE_ALERTS_PARAMETER`) |
| Alerts | `eclipses/EclipseAlerts.kt` (new): `EclipseAlertScheduler`, planner/poster workers, `EclipseNotifier` |
| Time travel | `time/TimeTravelEvents.kt` |
| App wiring | `SkyMapApplication.kt` (eclipse-alert opt-in collector), `ui/map/MapChrome.kt` (parameter label/description), `ui/map/MapScreen.kt` (`eclipseRow` wiring) |
| Strings | `res/values/strings.xml` |

## Acceptance criteria

1. `./gradlew check` from `stardroid-v2/` — ktlint, the Konsist architecture gate, JUnit 5 + Truth,
   across `core:astronomy`, `core:events`, `render:api`, `render:gles1`, and `app`.
2. `LunarEclipseTest` (golden 2025-03-14 case, in-progress-detection regression, contact ordering),
   `EclipseGeometryTest` (umbra reddening, penumbral dimming, shadow offset/direction), `TonightSkyTest`
   (Denver-vs-Dhaka visibility gating on the same real eclipse), `SolarSystemLayerTest` (eclipse
   shadow present/absent), `EclipseAlertSchedulerTest` (alert-time fallback ordering).
3. On-device: time-travel to the new 2026-08-28 entry (or 2025-03-14) and confirm the Moon tints
   copper through the umbra and dims through the penumbra; open the Moon's card and confirm
   `EclipseRow` shows the right type/time; confirm the tonight widget surfaces the eclipse with
   correct quality-based ranking when it is within three days and visible from the saved location.
