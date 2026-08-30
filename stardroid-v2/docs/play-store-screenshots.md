# Play Store phone screenshots

How the current `fastlane/metadata/android/en-US/images/phoneScreenshots/` set was captured, and
how to reproduce or refresh it. This is a manual, driven-by-hand process — not the
`fastlane android screenshots` (Screengrab) lane, which needs annotated UI-test classes that don't
exist yet for v2 (see "Relationship to the Screengrab lane" below).

## Why manual driving instead of Screengrab

Screengrab screenshots are great for deterministic, scripted captures (and for the
per-locale regeneration this app will eventually need), but the whole point of Sky Map's
screenshots is a convincing live starfield — mocked sensor/location data in a test harness doesn't
sell that. Driving the real app on a real device, by hand, gets a materially better result for the
handful of hero shots a store listing needs.

## Prerequisites

- A device with the v2 debug build installed (`gms` flavor). A physical phone gives a much
  better-looking render (real GPU, real screen) than the emulator — prefer it for the phone set.
- If the device already has the production Sky Map (v1, `com.google.android.stardroid`) installed
  from the Play Store, you must uninstall it first — the v2 debug build is signed differently and
  `adb install` will fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` otherwise. This removes v1 and
  its data from that device; reinstall v1 from the Play Store afterward if you want it back.
- Turn on Do Not Disturb before shooting. Incoming notification banners render on top of the app
  and will land in your screenshots otherwise.

```bash
adb -s <device> uninstall com.google.android.stardroid   # only if v1/prod is installed
adb -s <device> install app/build/outputs/apk/gms/debug/app-gms-debug.apk
adb -s <device> shell cmd notification set_dnd on
```

## Capturing each shot

Launch the app (`com.google.android.stardroid`), accept the Terms of Service, skip the onboarding
tutorial ("Skip" — bottom left), and grant (or decline) the location permission. Then:

1. **Switch to manual pan mode.** The default mode points the sky view at wherever the phone's
   sensors are aimed, which is unusable for a still device (you end up looking at `NADIR`). Tap
   the compass/hand icon in the bottom bar ("Switch to manual mode") — after that, the sky pans
   with touch drag instead of the gyroscope.
2. **Hero shot — `1_hero_sky_view.png`.** Use Search (bottom bar, magnifying glass) to jump to a
   recognizable target — Orion works well. Tap "Go" on the search suggestion, dismiss the
   found-target reticle, then tap empty sky once to hide all UI chrome. Screenshot.
3. **Object info card — `2_orion_nebula_info.png`.** Bring chrome back (tap sky), search "Orion
   Nebula", tap "Go", then tap directly on the object marker at the search reticle's centre (not
   a nearby label — it's easy to land on a neighbouring object, e.g. M43 instead of M42) to open
   its info card. Only objects with a photo (`skymap.celestial-image` assets) are worth using here.
4. **Search — `3_search.png`.** Open Search again and type a query with multiple suggestions
   (e.g. "Andromeda") but don't submit — the dialog with suggestions is the shot.
5. **Time travel — `4_time_travel.png`.** Bottom bar → Time travel (clock icon). The base dialog
   (`Visiting: <date>`, Set date / Set time, "Select a popular event…") is the shot; don't expand
   the popular-event dropdown, it's just a plain text list and screenshots worse than the dialog.
6. **Object info card, variety shot — `5_jupiter_closeup.png`.** Same technique as step 3, applied
   to Jupiter instead of Orion Nebula, purely so the listing doesn't show two near-identical M42
   card shots.
7. **Night mode — `6_night_mode.png`.** Bring chrome back, tap the moon icon (Night mode) to
   red-shift the UI, then tap empty sky to hide chrome again. Screenshot.
8. **Zoomed sky view — `7_saturn_zoom.png`.** There's no in-app pinch-to-zoom exposed to the
   automation tooling (and no volume-key zoom binding in v2, unlike v1), so this step needs a
   human: search to a planet, then physically pinch-zoom on the device screen until the framing
   looks good, and say so — don't drive this one blind. If time travel is active when you land on
   a good composition (e.g. a conjunction), leave it alone rather than tapping "Now"/"Cancel" to
   tidy up — that jumps the sky back to real time and throws away the exact framing you just
   zoomed to. It's fine to leave the time-travel banner in the shot — it doubles as a record of
   *when* that framing exists, which matters for reproducing it later. The current
   `7_saturn_zoom.png` is time-travelled to **Nov 20, 2026, 7:49 AM**, which is what puts the Moon,
   Saturn, and Neptune in the same tight frame; reproducing this exact shot means using Time
   travel (Set date / Set time) to dial in that date first, not just zooming in on Saturn at
   whatever the current date happens to be.

Turn Do Not Disturb back off when done: `adb -s <device> shell cmd notification set_dnd off`.

## Post-processing

Raw captures off a high-density phone are far taller than Play Store's allowed aspect ratio (max
dimension no more than 2× the min — a raw 960×2142 capture is ~2.23:1 and gets rejected). Crop to
9:16 and normalize to 1080×1920:

```python
from PIL import Image

W, H = 960, 2142          # adjust to match your raw capture size
target_h = round(W / (9 / 16))
top = ...                 # pick per-image, see below

im = Image.open("raw.png").convert("RGB")          # drop alpha — Play Store wants no transparency
crop = im.crop((0, top, W, top + target_h))
crop.resize((1080, 1920), Image.LANCZOS).save("out.png")
```

**Don't blindly center-crop every shot with the same `top` offset** — dialogs and cards sit at
different heights depending on what triggered them (e.g. a search dialog opened with the keyboard
already up sits higher than one opened fresh), so a fixed offset that works for one shot can slice
the title off another. Look at each raw capture first, find the top/bottom bounds of the content
that actually matters (card edges, dialog edges, or nothing in particular for full-bleed sky art),
and pick `top` so that content is centered — or fully included, for content taller than 1707px, in
which case some peripheral toast/background will get clipped instead.

Output goes in `fastlane/metadata/android/en-US/images/phoneScreenshots/`, numbered so Play
Console orders them as intended (`1_...png` through `7_...png`).

## Relationship to the Screengrab lane

`fastlane/Screengrabfile` and the `screenshots` lane in the `deploy-play-store` skill already
exist (carried over from v1's setup) and can capture per-locale screenshots automatically — but
only from annotated Screengrab test classes, and none exist in `app/src/androidTest/` yet. If/when
this app needs the full translated-locale screenshot set (like v1 has), write those tests and use
the Screengrab lane for that; keep using this manual process for the primary English store
listing shots, where visual quality matters more than scripted repeatability.

## Tablet form factors

Not yet done. Play Store also wants 7" and 10" tablet screenshots — capture those on the emulator
(`fastlane android screenshots --device_type=sevenInch`, once Screengrab tests exist, or by manually
driving a tablet AVD the same way as above) and add the steps here once that pass happens.
