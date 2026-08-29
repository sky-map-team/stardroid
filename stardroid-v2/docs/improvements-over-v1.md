# Improvements over v1

Net-new capabilities and fixes in v2 that v1 did **not** have — the list we'll draw on for
launch highlights, release notes, and store copy.

**Scope.** Only things a user could notice or that we'd genuinely claim as an improvement.
This is *not* a port log: faithfully reproducing v1 behavior (even where v1 was quirky) does
not belong here — that's what decisions.md records. An entry earns its place
by being a capability v1 lacked or a v1 bug we deliberately fixed rather than preserved.

**How to use.** When a slice adds something that isn't in v1, append a row here in the same
change, link the decision(s) that justify it, and phrase the "What it means for users" column
the way we'd say it to a user — not to a compiler. Keep the newest at the top.

**Flag-guarded features are deliberately absent.** The five unannounced ones — through-camera
(AR) mode, share, the two widget sets, and notifications — are gated by `Experiment` flags. They are
not secrets, but they are not launch copy either — and the help text does not reference them.
Do not add them here until they are announced; see [the flagged set](#flag-guarded-not-for-launch-copy)
at the foot of this file for the standing list.

| Feature | What it means for users | Since | Decisions |
|---|---|---|---|
| The Moon's phase is drawn, not picked from eight pictures | The terminator is computed for the actual illumination, so the phase is right on any night rather than snapping to one of eight stock images — and the Man in the Moon now stays still. v1 rotated the whole picture to face the Sun, which swung the familiar face around through the month. A New Moon is still visible too, as a dark sphere lit by earthshine, instead of vanishing. | unreleased | D88 |
| Venus and Mercury have phases | Venus goes through phases like the Moon's — a small full disc when it's far away, a broad crescent when it's near — and Mercury does the same. Zoom in and you can watch it. v1 drew both as flat, fully-lit dots whatever the date. Mars picks up its gibbous phase as well. | unreleased | D88 |
| Eclipses happen at the right time | The Sun and Moon are computed from full analytic series rather than v1's rough approximations, so an eclipse peaks when it actually peaks. v1 could be ~30 minutes out — enough to send you outside at the wrong moment. The same accuracy sharpens every Sun/Moon conjunction and the Moon's position against nearby stars. | unreleased | D84 |
| Rise and set times on every info card | Open any object — a planet, a star, a galaxy — and the card tells you when it next rises and sets where you are, or that it never sets (circumpolar) or never comes up at all. During time travel the times follow the sky you're looking at. v1 could only do this for the Sun, buried in the time-travel picker. | unreleased | D51, D50 |
| A pointing readout on the map | A live corner display shows exactly where you're aimed — right ascension and declination, altitude and azimuth with a compass point, and the current field of view. v1 never told you where you were looking. | unreleased | D65, D66 |
| Tapping objects works out of the box | Tap any labeled object for its info card — including in the normal sensor-driven mode, where v1 shipped this switched off and most users never found it. | unreleased | D74, D45 |
| Labels stay put when you zoom | Object labels now sit a fixed distance from their object at every zoom level, and clear big bodies properly — a Saturn label clears Saturn's disc. In v1 labels drifted further and further from their objects as you zoomed in. | unreleased | D74, D39 |
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

Built, shipping in the binary, and **on by default** — but unannounced. The flags are
kill-switches and staged-rollout levers, not "unfinished" markers. The help text does
not mention these, and neither should store copy or release notes until they are announced.
When one is announced, move it into the table above with a real "What it means for users" row.

| Feature | Flag | Decisions |
|---|---|---|
| Through-camera (AR) mode, with drag-to-align | `camera_ar_enabled` | D64, D67, D68 |
| Share the sky (overflow row + in-AR shutter, camera composite layouts) | `share_sky_enabled` | D70, D71 |
| Moon-phase home-screen widget | `moon_widget_enabled` | D75 |
| Tonight's-Sky and Countdown widgets | `tonight_widget_enabled` | D76 |
| Shower-peak and tonight-digest notifications | `notifications_enabled` | D77 |

The warm-welcome onboarding pager used to sit in this table. It never belonged: it was
inherited from v1 as an A/B lever on the first-run funnel, not a gate on something
unannounced. Its flag was retired (D80) and it is now a launched feature —
free to describe in help text and store copy, and one of the better things to screenshot,
since every fresh install sees it and it is built on the live app chrome (D61).

One caveat if these are ever screenshotted: the in-AR controls still carry the **temporary
manual ISO/shutter dev sliders** (visible only on `MANUAL_SENSOR` cameras), explicitly slated
for removal or a dev flag once night defaults settle — see D69.
