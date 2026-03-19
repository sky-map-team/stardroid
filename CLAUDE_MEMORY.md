# Sky Map Project Memory

## Build Gotchas
- Always set `JAVA_HOME=$(/usr/libexec/java_home -v 17)` before building — Gradle will silently use the wrong JDK otherwise
- Use `assembleGmsDebug` not `assembleDebug` (no plain flavor exists)
- Full build/test/deploy commands are in the `/build` skill (`.claude/skills/build/SKILL.md`)

## Adding Catalog Objects (Messier/Special)

### Files to touch (in order)
1. `tools/data/messier.csv` — source of truth; RA in decimal hours, Dec in decimal degrees
2. `app/src/main/res/values/celestial_objects.xml` — one `<string>` per name/alias
3. `app/src/main/res/values/celestial_info_cards.xml` — keys: `object_info_<key>_{description,funfact,distance,size}`
4. `app/src/main/assets/object_info.json` — JSON entry keyed by primary name key; add `imageKey`/`imageCredit` if image available
5. *(Optional)* `app/src/main/assets/celestial_images/messier/<name>.webp` — 480×800 WebP info card image; use `/celestial-image` skill to process a source image. If no free image exists, a 480×800 solid-black WebP is acceptable as placeholder (`python3 -c "from PIL import Image; Image.new('RGB',(480,800),(0,0,0)).save('willman_1.webp','WEBP')"`).

### CSV name format
- Use natural names with spaces, pipe-separated: `T CrB|Blaze Star|T Coronae Borealis`
- `AbstractAsciiProtoWriter.rKeysFromName()` converts spaces→underscores and lowercases to make resource IDs
- Primary label and object_info.json key = first name converted (e.g. `t_crb`)

### Coordinate precision
- For extended nebulae use the **nebula centre**, not the embedded star
- Eta Carinae Nebula (NGC 3372): RA 10h 45m 08.5s / Dec −59° 52' 04" (centre)
  vs. Eta Carinae star: Dec −59° 41' — these differ by ~11 arcmin

## Release Splash Screens

- Tool: `tools/make_release_splash.py` — composites a portrait + gold label onto the base splash
- Source images stored in: `assets/splashscreens/<name>.png` (e.g. `venus.png`, `earth.png`)
- Base splashes (unbranded originals): `assets/splashscreens/stardroid_big_image.webp` / `stardroid_big_image_large.jpg`
- Deployed to: `app/src/main/res/drawable/stardroid_big_image.webp` (phones) and `drawable-large/stardroid_big_image.jpg` (tablets)
- Skill: `/release-splash <Label> <path/to/source.png> [crop x1,y1,x2,y2]`
- New skills must be force-added: `git add --force .claude/skills/<name>/SKILL.md` (parent `.claude/` is gitignored but skills are tracked)

| Release | Source | Crop |
|---------|--------|------|
| Venus   | `venus.png` | `808,0,2016,1200` |
| Earth   | `earth.png` | none (2048×2048 square) |

## Auto-Level Horizon (manual mode)

Branch: `feature/auto-level-horizon` (pushed 2026-03-01)

### How it works
- `HorizonLeveler.kt` — runs at 20 fps via `ScheduledExecutorService`; each frame computes signed misalignment angle between `currentPerp` and the zenith projection, springs 20% of the way per frame (~1–2 s return). Stops at <0.1°.
- `DragRotateZoomGestureDetector` — `onGestureEnd()` added to listener interface (default no-op); `ACTION_UP` always fires it.
- `MapMover` — accepts `SharedPreferences`; starts leveler on `onGestureEnd()` if pref on; stops it on any drag/rotate/stretch.
- `GestureInterpreter.onDown` — calls `mapMover.stopLeveling()` alongside `flinger.stop()`.
- Pref key: `ApplicationConstants.AUTO_LEVEL_HORIZON_PREF_KEY = "auto_level_horizon"` (default true).
- Setting lives in Sensor Settings category of `preference_screen.xml`.

### Key math
1. Project zenith onto view plane: `zenithProj = zenith − (zenith·los)×los`; if `|zenithProj|² < 0.001` skip (looking straight at zenith).
2. Signed angle: `cross = currentPerp × targetPerp`; `angle = atan2(cross·los, currentPerp·targetPerp) * R2D`.
3. Callback calls `controllerGroup.rotate(delta)` directly (not via `mapMover.onRotate` which has a sign flip).
## Location UX Fix

Branch: `fix/location-ux` (created 2026-03-03 from master)

### What was fixed
- `LocationController` tracks `LocationStatus` enum (OK / PERMISSION_DENIED / NO_PROVIDER / MANUAL_NO_COORDS)
- `lastStatus` reset to OK at start of each `start()` call; set in each failure path
- Zero-coord detection uses parsed `float` values, not raw pref strings (avoids "0" vs "0.0" mismatch)
- `ControllerGroup` stores `locationController` field and exposes `getLocationController()` — prevents two-instance bug where activity injection gets a different (never-started) instance than ControllerGroup
- `DynamicStarMapActivity.maybeShowLocationWarning()` calls `controller.getLocationController()` and shows a `Toast` after `controller.start()` when location is unset; for PERMISSION_DENIED also shows `LocationPermissionDeniedDialogFragment`
- **No Snackbar / no Material dependency** — Material `Snackbar` crashes on the app's AppCompat-only `FullscreenTheme` (missing `?attr/colorOnSurface`); `Toast` is used instead
- `DiagnosticActivity`: "Location Permission" row added (first in Location & Time section); green/red colours that respect night mode
- Status colours renamed from sensor-specific names to generic `status_good/ok/warning/bad/absent` with `night_status_*` night-mode variants; no hardcoded hex in Java
- Spec file: `specs/features/location.md`
