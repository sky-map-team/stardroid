# AR share still vs. drawn sky — investigation notes

Working notes from 2026-11-21/22. Status: **in progress**. Written so the work can resume
from a cold start. Related design doc: [`design/camera-ar-mode.md`](design/camera-ar-mode.md).

Branch `feature/ar-photo-debug-stamp`, worktree `.worktrees/stardroid-iota`
(commits `8c78b27f`, `18552205`, pushed; no PR yet). Phone: Pixel 9 Pro (Android API 37),
main camera.

## 1. The question

The user shot real photos through Sky Map's AR mode (mostly the "Night photo" still on the
Pixel 9 Pro) and shared them with the side-by-side layout (camera still left, drawn sky right).
Goal: find what could be improved. Findings so far fall into four groups: the camera still is
more zoomed than the overlay, night stills drift/blur, the noise and exposure paths differ,
and the overlay is cluttered.

## 2. Headline finding: the still is ~15% tighter than the overlay

Across every shot, features in the camera still are **larger than the drawn sky by 1.11–1.25×**,
with no meaningful rotation (≤ ~3°).

| Shot | Measured ratio (photo ÷ overlay) | Notes |
|---|---|---|
| Leo/Jupiter (first), night on | ~1.1 (eyeballed) | star matching uncertain |
| Taurus: Aldebaran–Pleiades | 1.18 | ~3° rotation, one frame |
| Orion, zoomed-in map | 1.14 | Betelgeuse–Rigel |
| Orion, wide, night on, noisy | **1.245** | Betelgeuse–Rigel; Sirius then *predicted* within ~5 px, so it is not a fit error |
| Jupiter/Mars/Regulus/Algieba, after midnight, night on | 1.11–1.13 | several pairs agree |
| Indoor, Sky Map plain vs night still | **1.01** | same scale: the night extension is not the cause |
| Indoor, Sky Map plain vs stock camera 1× | **1.15–1.18** | TV, tissue box and mug all scale by the same factor, so it is a real FOV difference, not parallax from moving the phone |

Measurements are by eye from shared images, ±3% for the indoor set, worse for the star set.
Drag-to-match was possibly used on some early star shots, so *translation* offsets from those
are not trustworthy (scale and rotation are not affected by a drag).

### What is consistent on paper

- The app's map FOV is 35.3° short-side (≈ 22–26 px/deg at the shared size), equal to lens FOV
  at 1× divided by zoom: `applyArFov` in `MapViewModel` does `baseFov / zoomRatio`.
- The still is 4080×3072 (12.5 MP), rotated 90°, scaled to a 2048 long edge (1542×2048), then
  centre-cropped to the map's aspect (918×2048 = **59.5% of width**). A 24 mm-equivalent frame
  is ≈ 56.5° across the short side, so the crop should show ≈ 35.5°: matches the map's 35.3°.
- So crop maths and the formula agree with each other. The still apparently covers ≈ 15% less
  sky than the sensor-derived 56.5° implies (≈ 49° instead).

### Hypotheses and status

1. **Aspect mismatch between FOV calc and share crop** — *rejected.* The FOV uses display
   metrics (`MapRoute.kt` `viewAspectLongOverShort`), the map fills the screen, and
   `SkyShare.drawCameraColumn` centre-crops to the map bitmap's own aspect (2.23).
2. **Night extension crops tighter than the preview** — *rejected indoors:* plain (`ext=none`)
   and night stills have the same scale. (Could still differ for dark astro scenes; unverified.)
3. **CameraX still crop / `ImageProxy.cropRect` / camera pipeline crop** — *open, prime suspect.*
   The stamp now records `cropRect`, sensor-to-buffer transform and `SCALER_CROP_REGION`.
4. **FOV formula inputs** (`LENS_INFO_AVAILABLE_FOCAL_LENGTHS.firstOrNull()` and
   `SENSOR_INFO_PHYSICAL_SIZE` on what is a logical multi-camera) — *less likely:* the stamp
   shows `id 0, sensor 9.792x7.3728 mm, focal 6.9 mm`, matching the camera's EXIF (6.9 mm,
   24 mm-equivalent). Worth keeping in view if (3) shows no crop.
5. **Zoom reapplied after the night rebind maps differently** — *open, weak:* the ratio
   varied (1.11 to 1.245) which could be zoom dependent, but reported zoom is 1.00 in the
   shots stamped so far. Needs zoomed shots (see test plan).

### Reference: the stock camera frame

`PXL_20261121_010200461.NIGHT.jpg` (stock Night Sight, Orion), EXIF: 3072×4080, 0.348 s,
ISO 16899, f/1.68, 6.9 mm (24 mm eq), taken 2026-11-21 01:02 UTC at ≈ 53.20°N 3.07°W.
The 0.35 s / ISO 17k look like per-frame settings; the cleanliness comes from multi-frame
merging. Rough read of Orion star spacing gave ≈ 59° across the full width, consistent with
the 24 mm-equivalent expectation (≈ 57°). The shared PNGs carry no EXIF or text chunks.

## 3. How the camera and night-mode path works (from the code)

All in `app/src/main/kotlin/com/google/android/stardroid/camera/SkyCameraPreview.kt` unless
noted. Known from reading the code unless marked *inferred*.

- **Live session** (`bindSession(night = false)`): `Preview` + `ImageCapture`
  (`CAPTURE_MODE_MAXIMIZE_QUALITY`) + an `ImageAnalysis` luma probe. Sky tuning is applied to
  preview and capture (`applySkyTuning`): AF off, `LENS_FOCUS_DISTANCE = 0` (infinity), and
  Low Light Boost AE (`CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY`, Android 15+,
  devices that list it).
- **Extension probe** (`probeNightExtension`): CameraX `ExtensionsManager`, checks
  NIGHT/AUTO/HDR/BOKEH/FACE_RETOUCH on the back camera. `stillExtensionMode` = NIGHT if
  offered, else the vendor AUTO, else NONE. On this phone the report reads
  `camerax: night; platform: night`, so the night extension is offered.
- **Night still** (`takeNightStill`): sets `capturingNight` (UI shows the "hold still"
  message), **unbinds the live session** and binds a fresh session with
  `extensions.getExtensionEnabledCameraSelector(...)` and **only** `Preview` + `ImageCapture`
  (no analysis). Both are built with `tuned = false` — the extension owns its capture settings.
  After the rebind it reapplies zoom (`reapplyZoom`), tries `pinFocusAtInfinity()` (Camera2
  interop: AF off, focus distance 0), waits `NIGHT_SETTLE_MS` (400 ms), calls `capture()`,
  then rebinds the live session and `reapplyControls()`. The preview blanks during the swap.
  If the night bind or capture returns null, `takeStill` falls back to the plain still.
- **Camera2 interop is probably ignored in the night session.** The code comment says vendor
  extension sessions may ignore Camera2 options. Evidence it is ignored for focus: an indoor
  night still at ≈ 1 m was sharp, whereas the plain still (focus pinned at infinity, honoured
  because that session uses interop) was soft. So the extension autofocuses itself.
  *Whether it focuses well on stars is unknown* (the stamp's EXIF `subj dist` will tell).
- **Manual exposure and the exposure slider**: applied to the live session via
  `Camera2CameraControl`. They do **not** carry to the night session (only zoom and the focus
  pin do) and the extension would probably ignore them anyway. They are restored only after
  the night shot. For the plain still they are now kept (previously `takeAutoStill` dropped
  them and waited 600 ms for AE).
- **What the extension returns** (from the stamps): the capture is an in-memory JPEG with
  EXIF. Night indoor: `1/100 s ISO 353` and `1/3 s ISO 77` (two shots). Plain: `1/30 s ISO 568`.
  *Inferred:* for the night session those EXIF values summarise one frame of a multi-frame
  merge, not the total exposure; the night capture takes **6–7 s wall time** (observed 6.0,
  7.2 s). The extension plausibly aligns and merges frames (sharp results despite hand-hold),
  but how it picks the reference frame is **not known** and matters for drift compensation.
- **Plain still, dark sky** (night off, auto exposure) came out nearly black: a short exposure
  with none of the live preview's low-light handling.
- **Noisy night stills** (Orion): looked like a single high-ISO frame with heavy noise
  reduction, unlike the clean Night Sight original. Whether the extension ran or the code
  fell back to the plain still is **unknown for those shots** (taken before the stamp
  existed); the stamp's `still <path> ext=` line answers this from now on. The twilight shot
  of Leo/Jupiter looked clean, so the extension does produce good stills in some scenes.
- **Share composite** (`share/SkyShare.kt`): `captureMap` (PixelCopy of the GL view), the still
  is scaled to `MAX_SHARE_EDGE_PX`, then `drawCameraColumn` centre-crops it with
  `centerCrop(...)` (`share/CropRegion.kt`) to the map's aspect, dimmed by the scrim. The map
  bitmap is captured **before** the still starts, so its pose is the pose at the start.

## 4. Drift

- Handheld indoor night still: phone moved **dAz +4.5°, dAlt −6.1°** during a 6.0 s capture.
  Propped on a table: **dAz −2.7°** over 7.2 s. The plain still (0.8 s): −0.45°.
- The map is drawn from the start pose, so for long exposures the overlay is stale by however
  far the user drifts. The stock camera and, presumably, the night extension compensate by
  aligning frames; Sky Map's overlay does not.
- Users won't have tripods, so the goal is to compensate the way the native camera does.
  Direction: use the pose track (below) to find which moment the photo corresponds to (start,
  middle, end) and render/capture the map for that pose instead of the start pose. How to
  render at a computed pose (capture the map at the right time vs. re-render) is undecided.

## 5. Other observations (overlay and capture)

- **Label clutter**: collisions around Leo ("Leonids"/"Algieba"/"Adhafera"), "Aselus
  Borealis/Australis", "Orion Nebula"/"Hatysa", "Lepus"/"Arneb"/"Nihal", "Columba"/"Wazn",
  "Sagitta"/"Sham", and labels clipped at the edge. Galaxy icons overlap labels (Alterf, Leo).
  "Prima Hyadum" sits on a constellation line.
- **Planet discs**: Jupiter and Mars are drawn as ~65 px textured discs against pinpoints in
  the photo, hiding the star position underneath.
- **Overlay density**: constellation lines, equatorial grid and ecliptic all at once compete
  with faint stars in the share image.
- **Compass**: where drag-to-align was not used (Orion wide), the photo was ≈ 5° left of the
  overlay in azimuth after correcting scale, with altitude within ≈ 1°. Plausible compass
  error; not conclusive (Wales, declination ≈ −0.9° to +0.2°).
- **Star streaks**: some night shots show short same-direction dashes (stack misalignment),
  so star positions in night stills are good to tens of pixels only.

## 6. What the debug stamp records

Switch: **"Debug stamp on shared image"** under "Night photo" in the AR still controls
(`StillModeControl` in `ui/map/MapRoute.kt`), state in `ArCaptureViewModel.debugStamp`. Text
is drawn under both layouts by `SkyShare.appendStamp`. Code: `ArStamp.kt` (data + formatter,
`shared/viewmodels`), `ShareDebugStamp.kt` (app side), tests in `ArStampTest.kt`. Everything
is marked `TODO(ar-debug-stamp)` for removal.

Lines, top to bottom: app version and device; UTC and capture duration (plus `SKY TIME` if time
travel shifts the sky clock); location rounded to 0.01° and declination; RA/Dec, Az/Alt, roll;
drag-to-align offsets and sensor/manual frame; `moved dAz/dAlt` before→after (`MOVED` over
0.5°); **`track`** (up to five pose samples through the capture, time:dAz/dAlt from the start);
map FOV rendered, lens FOV at 1×, zoom, expected lens/zoom, bitmap size, px/deg; still path
(`auto`, `night`, `night failed -> auto`, `auto (night unavailable)`) and extension mode;
source size, rotation, share size and the centre-crop (% of width kept); camera-reported zoom
and EXIF **subject distance**; **`img cropRect`**; **`sensor->buffer`** transform;
**`scaler crop`** (plain session only; "n/a (extension session)" for night); EXIF exposure
line; lens/sensor summary; extension report; exposure index and manual ISO/shutter; scrim and
preview luma.

Privacy: the stamp prints location rounded to ~1 km. Fine while it is a temporary developer
toggle; **it must be removed (or the privacy policy updated) before release.**

## 7. Test plan (not yet done — cloudy night)

Same aim for each, phone propped, **no drag** (reset alignment first), side-by-side layout,
stamp on. Targets with two bright, widely separated stars (Orion + Sirius, or Jupiter +
Regulus).

1. Stock camera Night Sight (the FOV reference; keep the original JPEG for EXIF).
2. Sky Map, Night on, 1×.
3. Sky Map, Night off, **manual** ISO ≈ 3200 and shutter ≈ 0.5–1 s (auto gives black).
4. Sky Map, Night off, auto exposure (probably black; the stamp still reports crop/EXIF).
5. Repeat 3 at ≈ 2× zoom, 6. repeat 2 at ≈ 2× zoom (is the ratio zoom dependent?).
7. A second target ≈ 90° around the sky (does the azimuth offset vary with direction?).
8. Optional: repeat 2 after a figure-eight to see whether compass calibration changes it.

Reading the results: if `scaler crop` or `img cropRect` shows a ≈ 85% crop, that is the
cause of the 15% (fix: derive the real still FOV and crop the map to match, or fix the
stream). If crop fields are full-frame and the ratio persists, revisit hypothesis 4/5. An
indoor repeat with the phone **not moved** between the stock and Sky Map shots would give a
cleaner stock-vs-plain comparison than the first one did.

## 8. Next steps (ordered)

1. Run the test plan; read the new crop/subject-distance/track stamp lines.
2. Fix the still-vs-map FOV mismatch once the source is known. Likely: take the still's real
   FOV from its own metadata and crop the map capture to match, so the share composite is
   right whatever the camera pipeline does.
3. Overlay pose for night stills: pick the correct moment from the pose track and render the
   map for it (design open).
4. Decide whether the plain (Night off) still should get a longer fixed exposure or Low Light
   Boost so it is usable for sky shots; consider removing the Night off choice.
5. Check whether the extension's own autofocus is adequate for stars (`subj dist`).
6. Look at noise: stack several short frames ourselves if the extension is not engaging in
   dark scenes.
7. Overlay polish: label collision avoidance, smaller planet markers, calmer default layers
   for share images.
8. Remove the debug stamp; update `PRIVACY_POLICY.md` if anything of it ships.

## 9. Environment notes

- New worktree `.worktrees/stardroid-iota` was created (all other Greek worktrees were in
  use); the four build-critical untracked files were copied from the main checkout.
- `./gradlew check` passes (ktlint, Konsist, JVM and iOS-simulator tests). A first run failed
  at `iosSimulatorArm64Test` (`xcrun` exit 126) because the Gradle daemon predated the new
  Xcode (27.2); a fresh daemon fixed it.
- Debug build installed with `./gradlew :app:installGmsDebug` (device `4C151FDAP000F6`).
- Shared images and the stock original used here are in `~/Downloads`
  (`sky_map_17952*.png`, `PXL_20261121_010200461.NIGHT.jpg`, `PXL_20261122_000145630.jpg`).
