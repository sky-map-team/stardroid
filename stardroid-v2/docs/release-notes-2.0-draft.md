# Sky Map 2.0 — What's New draft

Draft for review. Written for a user on 1.x seeing this for the first time — assumes no
familiarity with the v2 beta. Once approved, distill into `app/src/main/res/values/whatsnew.xml`
(drop `beta_user_help_text` entirely for the non-beta release) and the shorter fastlane
`changelogs/default.txt`.

Sources: `README.md` "What's new in v2", `docs/improvements-over-v1.md`,
`docs/launch-feature-set.md`. Deliberately **excludes** AR/camera mode, share, widgets, and
notifications — all still experiment-flagged off by default (`ec345309`) and marked "not for
launch copy" in `improvements-over-v1.md`.

---

<h1>Sky Map is completely rebuilt</h1>
<p>This is the biggest update in Sky Map's history. Under the hood it's an entirely new app —
new rendering engine, new astronomy engine, new interface — built from scratch to be faster,
more accurate, and easier on the eyes. Everything you knew how to do, you still can. Here's
what's better.</p>

<h2>The sky is where it actually is</h2>
<p>The Moon is now placed exactly where <em>you</em> see it in the sky, not where it would
appear from the centre of the Earth — a correction of up to a full degree, two Moon-widths.
Eclipses and conjunctions land at the right minute too: the Sun and Moon now come from full
analytic calculations instead of v1's rougher approximations, which could be off by half an
hour. And when planets pass in front of one another, the nearer one now correctly draws on
top.</p>

<h2>A solar system that looks real</h2>
<p>The Moon's phase is calculated from its true geometry, not chosen from eight stock pictures
— the line between light and dark falls exactly where it should, every night, and the man in
the moon no longer spins as the month goes by. Venus and Mercury now show phases too, swelling
from a small full disc to a broad crescent as they near Earth, and Mars picks up its gibbous
phase. Zoom in and watch them change.</p>

<h2>Tap anything to learn about it</h2>
<p>Tap-to-identify is on by default now — point your phone at the sky and tap any labelled
object for its info card: photo, description, a fun fact, and the times it next rises and
sets from where you're standing. v1 could only do rise/set for the Sun, and buried it in the
time-travel picker.</p>

<h2>Find more, find it faster</h2>
<p>268 newly-added IAU star names now label the map, and you can search stars by their Bayer
or Flamsteed designation too — "Alpha Cygni", "α Cyg" and "50 Cyg" all find Deneb. Labels now
hold a fixed distance from their object at every zoom level instead of drifting away as you
zoom in, and default label sizes are a notch bigger for modern screens.</p>

<h2>Buttery smooth, even on the newest phones</h2>
<p>Flinging or dragging the sky on a high refresh-rate screen no longer stutters — panning,
momentum and the search fly-to now track your display's real frame rate, up to 120 Hz, instead
of a fixed 20 updates a second.</p>

<h2>Know exactly where you're pointing</h2>
<p>A new live readout shows right ascension and declination, altitude and azimuth with a
compass point, and your current field of view — right on the map. v1 never told you where you
were looking.</p>

<h2>Lunar eclipses, rendered</h2>
<p>The Moon now shows its real copper-red tint during a lunar eclipse on the map itself, and
its info card, tonight's-sky summary, and countdown all know when one is coming.</p>

<h2>A fresh coat of paint</h2>
<p>A new "Deep Space / Star Gold" colour scheme, sharper Sun/Moon/planet artwork smoothed and
blended at every zoom level, a rebuilt first-run tour that walks you through the live app
instead of static screenshots (replayable any time from the menu), and a refreshed welcome
screen built from a new in-app sky capture.</p>

<h2>Clearer about your privacy</h2>
<p>The Terms screen and Help now spell out exactly what each device permission is used for,
and the app no longer requests the advertising-ID permission it never needed.</p>

<h2>¡Hola! Bonjour!你好!</h2>
<p>Sky Map now speaks 28 languages, fully translated.</p>

---

## Notes for the fastlane/changelog distillation pass

- Fastlane `changelogs/default.txt` has a strict length limit — pick 3–5 of the biggest
  headlines (rebuild announcement, accurate Moon/eclipses, tap-to-identify, smooth panning,
  translations) rather than all eleven sections.
- Keep the `whats_new_support` and drop `beta_user_help_text` (or leave it empty) since 2.0 is
  no longer a beta.
- Re-run `tm translate --all-primary --include-stale` after `whatsnew.xml` content is finalized,
  and verify with `tm languages` before release.
